"""Windowed EPG reads for the guide grid (Slice C).

The guide needs *all* channels' programmes for one time window at once. Asking
the existing `/v1/epg?channel_id=` endpoint per channel would be a request per
row — hundreds of round trips for a single screen — so this service answers the
screen in one query.

Two properties matter and are pinned by ``tests/test_epg_service.py``:

* **One query, bounded.** Channels are chosen first (name-ordered, capped,
  test-scope filtered) inside a CTE, then LEFT JOINed to their programmes. The
  cap therefore limits *channels*, never silently truncating one channel's
  programmes.
* **A channel with no synced EPG still appears.** The window predicate lives in
  the JOIN's ``ON`` clause, not in ``WHERE`` — putting it in ``WHERE`` would
  turn the LEFT JOIN back into an inner join and make those channels vanish.
"""

from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from typing import Any, List, Optional

from ..catalog.scope import live_channel_limit, live_channel_scope
from .models import EpgProgramme, EpgWindow, GuideChannel

# A guide window wider than this is almost certainly a client bug, and would let
# one request pull a day of programmes for every channel.
MAX_WINDOW_HOURS = 24

# Rows shown when neither the caller nor the live test scope sets a cap.
DEFAULT_CHANNEL_LIMIT = 100


def parse_timestamp(value: str) -> datetime:
    """Parse an API timestamp; naive values are read as UTC.

    Accepts the shapes the clients and Redis caches actually produce:
    ``2026-10-01T14:10:00Z``, ``...+00:00``, and the legacy space-separated
    ``2026-10-01 14:10:00+00:00``.
    """
    text = (value or "").strip()
    if not text:
        raise ValueError("timestamp is required")
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    if " " in text and "T" not in text:
        text = text.replace(" ", "T", 1)
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError as exc:
        raise ValueError(f"invalid timestamp: {value!r}") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def format_timestamp(value: datetime) -> str:
    """Render a UTC timestamp the way the rest of the API does (``...Z``)."""
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


class EpgService:
    """Read-only, windowed EPG access for the guide grid."""

    def __init__(self, services):
        self.services = services

    @contextmanager
    def _db(self):
        with self.services.connection() as connection:
            yield connection

    def window(
        self,
        *,
        window_start: datetime,
        window_end: datetime,
        channel_ids: Optional[List[int]] = None,
        category_id: Optional[int] = None,
        limit: Optional[int] = None,
    ) -> EpgWindow:
        """Return every channel and its programmes overlapping ``[window_start, window_end)``.

        A programme that started before the window but has not finished is
        included, because the guide draws it clipped to the left edge.

        Raises:
            ValueError: if the window is empty/inverted or wider than
                :data:`MAX_WINDOW_HOURS`.
        """
        start = window_start if window_start.tzinfo else window_start.replace(tzinfo=timezone.utc)
        end = window_end if window_end.tzinfo else window_end.replace(tzinfo=timezone.utc)
        if end <= start:
            raise ValueError("window_end must be after window_start")
        if end - start > timedelta(hours=MAX_WINDOW_HOURS):
            raise ValueError(f"window may not exceed {MAX_WINDOW_HOURS} hours")

        settings = getattr(self.services, "settings", None)
        scope_clause, scope_params = live_channel_scope(settings)
        scope_limit = live_channel_limit(settings)
        channel_limit = max(1, int(limit)) if limit else (scope_limit or DEFAULT_CHANNEL_LIMIT)

        params: List[Any] = []
        cte = (
            "WITH guide_channels AS ("
            " SELECT id, name, slug, category, logo FROM tavuno_channels WHERE is_active = TRUE"
        )
        if channel_ids:
            cte += " AND id = ANY(%s)"
            params.append([int(cid) for cid in channel_ids])
        if category_id is not None:
            cte += " AND category = %s"
            params.append(int(category_id))
        cte += scope_clause
        params.extend(scope_params)
        cte += " ORDER BY name LIMIT %s)"
        params.append(channel_limit)

        query = (
            cte
            + " SELECT gc.id AS channel_id, gc.name AS channel_name, gc.slug AS channel_slug,"
            " gc.category AS channel_category, gc.logo AS channel_logo,"
            " p.id AS programme_id, p.title AS programme_title,"
            " p.starts_at, p.ends_at, p.description"
            " FROM guide_channels gc"
            " LEFT JOIN tavuno_epg_channels e ON e.channel = gc.id"
            " LEFT JOIN tavuno_epg_programmes p ON p.epg_channel = e.id"
            " AND p.starts_at < %s AND p.ends_at > %s"
            " ORDER BY gc.name, p.starts_at"
        )
        params.extend([end, start])

        with self._db() as conn:
            rows = conn.execute(query, tuple(params)).fetchall()

        return EpgWindow(
            window_start=format_timestamp(start),
            window_end=format_timestamp(end),
            channels=self._group_rows(rows),
        )

    @staticmethod
    def _group_rows(rows: Any) -> List[GuideChannel]:
        """Fold the flat (channel × programme) row list into channel rows.

        Relies on the query's ``ORDER BY gc.name`` so a channel's programmes
        arrive contiguously; insertion order into ``ordered`` therefore matches
        the SQL ordering.
        """
        ordered: List[GuideChannel] = []
        by_id: dict = {}
        for row in rows:
            channel_id = row["channel_id"]
            channel = by_id.get(channel_id)
            if channel is None:
                channel = GuideChannel(
                    id=channel_id,
                    name=row["channel_name"],
                    slug=row["channel_slug"],
                    category_id=row.get("channel_category"),
                    logo=str(row["channel_logo"]) if row.get("channel_logo") else None,
                )
                by_id[channel_id] = channel
                ordered.append(channel)
            if row.get("programme_id") is None:
                continue
            channel.programmes.append(
                EpgProgramme(
                    id=row["programme_id"],
                    title=row["programme_title"],
                    starts_at=format_timestamp(row["starts_at"]),
                    ends_at=format_timestamp(row["ends_at"]),
                    description=row.get("description"),
                    channel_id=channel_id,
                )
            )
        return ordered