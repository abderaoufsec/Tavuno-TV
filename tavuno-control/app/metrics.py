"""Prometheus exposition for tavuno-control (M16).

Three things live here:

* **Request instrumentation** — a count and a latency histogram for every
  request, labelled by the *route template* (``/v1/channels/{channel_id}``)
  rather than the concrete path, so a client walking ten thousand channel ids
  still produces one time series. Paths matching no route collapse into a
  single ``unmatched`` bucket for the same reason.
* **Scrape-time gauges** — the app's *own* view of PostgreSQL and Redis
  (probed with the app's credentials, which is what actually decides whether
  a request succeeds), the count of live playback sessions, and the age of
  the last Dispatcharr sync.
* :func:`render` — the exposition payload served at ``GET /metrics``.

Deliberately **not** here: probes of Dispatcharr and OvenMediaEngine. Both
wrap their HTTP calls in a retrying circuit breaker (``app/resilience.py`` —
3 attempts, and Dispatcharr allows 20s each), so a dead dependency would
stall the scrape past Prometheus's timeout and take the whole target down
with it. That is the exact inversion monitoring exists to prevent. Those two
are watched by blackbox-exporter instead, which probes them from outside
with its own short timeout (``tavuno-infra/config/blackbox``).

Nothing here may raise. A metrics failure must never turn a healthy 200 into
a 500, so every entry point swallows its own errors and logs at debug —
losing a sample is free; failing a request is not.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from typing import Any

from prometheus_client import CONTENT_TYPE_LATEST, Counter, Gauge, Histogram, generate_latest

logger = logging.getLogger("tavuno-control.metrics")

# Seconds. Catalog reads are served from Redis in single-digit milliseconds
# while playback authorization walks Postgres, so the useful resolution is at
# the low end; the wide buckets exist to catch a stalled dependency rather
# than to profile ordinary work.
REQUEST_DURATION_BUCKETS = (0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)

# Route label used when nothing matched: one bucket for all of them, so an
# unauthenticated scanner probing random paths cannot mint label values.
UNMATCHED_ROUTE = "unmatched"

http_requests_total = Counter(
    "tavuno_http_requests_total",
    "API requests served, by method, route template and status code.",
    ("method", "route", "status"),
)

http_request_duration_seconds = Histogram(
    "tavuno_http_request_duration_seconds",
    "API request latency in seconds, by method and route template.",
    ("method", "route"),
    buckets=REQUEST_DURATION_BUCKETS,
)

playback_sessions_started_total = Counter(
    "tavuno_playback_sessions_started_total",
    "Playback sessions authorized, by content type.",
    ("content_type",),
)

active_playback_sessions = Gauge(
    "tavuno_active_playback_sessions",
    "Playback sessions active and heartbeating in PostgreSQL at the last scrape.",
)

dispatcharr_sync_last_success_timestamp_seconds = Gauge(
    "tavuno_dispatcharr_sync_last_success_timestamp_seconds",
    "Unix time of the last successful Dispatcharr sync; 0 when it has never run.",
)

dependency_up = Gauge(
    "tavuno_dependency_up",
    "1 when the application's own probe of the dependency succeeded at the last scrape, else 0.",
    ("dependency",),
)

build_info = Gauge(
    "tavuno_build_info",
    "Always 1; the labels carry build metadata.",
    ("version",),
)


def set_build_info(version: str) -> None:
    """Publish the app version. Called once from ``main`` so the version has a
    single source of truth rather than a constant duplicated here."""
    try:
        build_info.labels(version=version).set(1)
    except Exception:
        logger.debug("could not publish build info", exc_info=True)


def route_template(request: Any) -> str:
    """The route *pattern* for a request, never the concrete path.

    Falls back to :data:`UNMATCHED_ROUTE` for 404s and for anything that
    failed before routing ran. The raw path is never used as a label: it is
    attacker-controlled and unbounded.
    """
    route = request.scope.get("route")
    path = getattr(route, "path", None)
    if isinstance(path, str) and path:
        return path
    return UNMATCHED_ROUTE


def observe_request(method: str, route: str, status: int, started_at: float) -> None:
    """Record one served request. Never raises."""
    try:
        duration = time.perf_counter() - started_at
        http_requests_total.labels(method=method, route=route, status=str(status)).inc()
        http_request_duration_seconds.labels(method=method, route=route).observe(duration)
    except Exception:
        logger.debug("could not record request metrics", exc_info=True)


def observe_playback_session(content_type: str) -> None:
    """Count an authorized playback session. Never raises."""
    try:
        playback_sessions_started_total.labels(content_type=content_type).inc()
    except Exception:
        logger.debug("could not record playback session metric", exc_info=True)


def _set_dependency(name: str, healthy: bool) -> None:
    dependency_up.labels(dependency=name).set(1.0 if healthy else 0.0)


def _probe_postgres(services: Any) -> None:
    try:
        with services.connection() as connection:
            connection.execute("SELECT 1").fetchone()
    except Exception:
        logger.debug("PostgreSQL probe failed for /metrics", exc_info=True)
        _set_dependency("postgres", False)
    else:
        _set_dependency("postgres", True)


def _probe_redis(services: Any) -> None:
    try:
        services.redis.ping()
    except Exception:
        logger.debug("Redis probe failed for /metrics", exc_info=True)
        _set_dependency("redis", False)
    else:
        _set_dependency("redis", True)


def _count_active_sessions(services: Any) -> None:
    """Count sessions the concurrency limiter itself would count.

    Same predicate as ``playback.py`` uses for ``max_concurrent_streams`` —
    a dashboard that disagrees with the enforcement would be worse than none.
    """
    try:
        with services.connection() as connection:
            row = connection.execute(
                "SELECT COUNT(*) AS count FROM tavuno_playback_sessions "
                "WHERE status = 'active' AND last_seen_at >= NOW() - INTERVAL '90 SECONDS'"
            ).fetchone()
        if row is None:
            return
        value = row["count"] if isinstance(row, dict) else row[0]
        active_playback_sessions.set(float(value))
    except Exception:
        logger.debug("could not count active playback sessions", exc_info=True)


def _read_sync_timestamp(services: Any) -> None:
    """Publish the age of the last successful sync.

    ``0`` when it has never run, which is why the alert rule guards on
    ``> 0``: a stack with ``DISPATCHARR_API_KEY`` unset runs no sync by
    design, and paging an operator for a deliberately disabled job would get
    the alert muted rather than acted on.
    """
    try:
        summary = services.sync.last_sync()
        text = (summary or {}).get("synced_at")
        dispatcharr_sync_last_success_timestamp_seconds.set(_parse_timestamp(text) or 0.0)
    except Exception:
        logger.debug("could not read last sync timestamp", exc_info=True)


def _parse_timestamp(text: Any) -> float | None:
    """ISO-8601 (with ``Z`` or an offset) to a Unix timestamp, or None."""
    if not text:
        return None
    try:
        value = datetime.fromisoformat(str(text).replace("Z", "+00:00"))
    except ValueError:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.timestamp()


def refresh_scrape_gauges(services: Any) -> None:
    """Re-read every scrape-time gauge. Each probe is independent, so one
    broken dependency cannot blank out the others."""
    _probe_postgres(services)
    _probe_redis(services)
    _count_active_sessions(services)
    _read_sync_timestamp(services)


def render(services: Any) -> tuple[bytes, str]:
    """Refresh the gauges and return ``(body, content_type)`` for the endpoint."""
    refresh_scrape_gauges(services)
    return generate_latest(), CONTENT_TYPE_LATEST
