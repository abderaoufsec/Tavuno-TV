"""Tests for Prometheus instrumentation (M16).

Two properties matter and both are regression-guarded here:

* **Cardinality.** A metric labelled by concrete path would mint a series per
  channel id; the middleware must record the route *template*, and a 404 must
  collapse into one ``unmatched`` bucket.
* **Infallibility.** Instrumentation runs on every request and every scrape.
  If it can raise, monitoring becomes a new way for healthy traffic to fail —
  so the endpoint answers 200 even when every dependency is down.
"""

import os
import unittest
from types import SimpleNamespace
from unittest.mock import MagicMock

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from fastapi.testclient import TestClient
from prometheus_client import REGISTRY

from app import metrics as prometheus_metrics
from app.main import app


def sample(name, **labels):
    """Read one sample from the default registry, or 0 when absent."""
    return REGISTRY.get_sample_value(name, labels) or 0.0


class RouteTemplateTests(unittest.TestCase):
    """The label is the pattern, never the concrete path."""

    def test_uses_the_route_pattern(self):
        request = SimpleNamespace(scope={"route": SimpleNamespace(path="/v1/channels/{channel_id}")})
        self.assertEqual("/v1/channels/{channel_id}", prometheus_metrics.route_template(request))

    def test_unmatched_when_no_route_matched(self):
        self.assertEqual(
            prometheus_metrics.UNMATCHED_ROUTE,
            prometheus_metrics.route_template(SimpleNamespace(scope={})),
        )

    def test_unmatched_when_route_has_no_path(self):
        request = SimpleNamespace(scope={"route": object()})
        self.assertEqual(prometheus_metrics.UNMATCHED_ROUTE, prometheus_metrics.route_template(request))


class MetricsEndpointTests(unittest.TestCase):
    """``GET /metrics`` answers, unauthenticated, with a valid exposition."""

    def setUp(self):
        self.previous_services = getattr(app.state, "services", None)
        self.client = TestClient(app)

    def tearDown(self):
        app.state.services = self.previous_services

    @staticmethod
    def fake_services():
        services = MagicMock()
        services.connection.return_value.__enter__.return_value = MagicMock()
        services.sync.last_sync.return_value = None
        return services

    def test_returns_200_without_credentials(self):
        """Prometheus sends no Authorization header, so neither may we need one."""
        app.state.services = self.fake_services()
        response = self.client.get("/metrics")
        self.assertEqual(200, response.status_code)

    def test_serves_the_prometheus_content_type(self):
        app.state.services = self.fake_services()
        response = self.client.get("/metrics")
        self.assertIn("text/plain", response.headers["content-type"])

    def test_body_carries_the_expected_metric_names(self):
        app.state.services = self.fake_services()
        body = self.client.get("/metrics").text
        for name in (
            "tavuno_http_requests_total",
            "tavuno_http_request_duration_seconds",
            "tavuno_playback_sessions_started_total",
            "tavuno_active_playback_sessions",
            "tavuno_dependency_up",
            "tavuno_build_info",
            "tavuno_dispatcharr_sync_last_success_timestamp_seconds",
        ):
            with self.subTest(metric=name):
                self.assertIn(name, body)

    def test_survives_every_dependency_being_down(self):
        """A scrape during an outage must still answer — otherwise the target
        disappears exactly when the operator needs to see it."""
        broken = MagicMock()
        broken.connection.side_effect = RuntimeError("postgres gone")
        broken.redis.ping.side_effect = RuntimeError("redis gone")
        broken.sync.last_sync.side_effect = RuntimeError("redis gone")
        app.state.services = broken

        response = self.client.get("/metrics")
        self.assertEqual(200, response.status_code)
        self.assertIn("tavuno_dependency_up", response.text)

    def test_reports_dependencies_as_down_when_the_probe_fails(self):
        broken = MagicMock()
        broken.connection.side_effect = RuntimeError("postgres gone")
        broken.redis.ping.side_effect = RuntimeError("redis gone")
        broken.sync.last_sync.return_value = None
        app.state.services = broken

        self.client.get("/metrics")
        self.assertEqual(0.0, sample("tavuno_dependency_up", dependency="postgres"))
        self.assertEqual(0.0, sample("tavuno_dependency_up", dependency="redis"))

    def test_reports_dependencies_as_up_when_the_probe_succeeds(self):
        app.state.services = self.fake_services()
        self.client.get("/metrics")
        self.assertEqual(1.0, sample("tavuno_dependency_up", dependency="postgres"))
        self.assertEqual(1.0, sample("tavuno_dependency_up", dependency="redis"))


class RequestObservationTests(unittest.TestCase):
    """The middleware labels with the route template and never breaks a request."""

    def setUp(self):
        self.previous_services = getattr(app.state, "services", None)
        app.state.services = MagicMock()
        app.state.services.connection.return_value.__enter__.return_value = MagicMock()
        app.state.services.sync.last_sync.return_value = None
        # The parametrised catalog route walks the database, and these mocks
        # cannot satisfy its row mapping. Letting the 500 surface as a response
        # (rather than being re-raised) is what the middleware sees in
        # production too — and the label under test is chosen regardless.
        self.client = TestClient(app, raise_server_exceptions=False)

    def tearDown(self):
        app.state.services = self.previous_services

    def _count(self, route, status, method="GET"):
        return sample("tavuno_http_requests_total", method=method, route=route, status=status)

    def test_parameterised_path_is_recorded_as_a_template(self):
        """The whole point of the label: a per-id path must not mint a series.

        The latency counter carries no status label, so it proves the template
        label increments without depending on what the route answers.
        """
        before = sample(
            "tavuno_http_request_duration_seconds_count",
            method="GET",
            route="/v1/channels/{channel_id}",
        )
        response = self.client.get("/v1/channels/123456")
        after = sample(
            "tavuno_http_request_duration_seconds_count",
            method="GET",
            route="/v1/channels/{channel_id}",
        )
        self.assertEqual(before + 1, after)
        self.assertEqual(
            1,
            self._count("/v1/channels/{channel_id}", str(response.status_code)),
        )

    def test_the_concrete_path_is_never_used_as_a_label(self):
        response = self.client.get("/v1/channels/123456")
        self.assertEqual(
            0.0,
            sample(
                "tavuno_http_requests_total",
                method="GET",
                route="/v1/channels/123456",
                status=str(response.status_code),
            ),
        )

    def test_unmatched_paths_collapse_into_one_bucket(self):
        """A scanner probing random URLs gets one series, not one per probe."""
        before = self._count(prometheus_metrics.UNMATCHED_ROUTE, "404")
        for probe in ("/definitely/not/a/route", "/another/missing/thing"):
            self.client.get(probe)
        self.assertEqual(before + 2, self._count(prometheus_metrics.UNMATCHED_ROUTE, "404"))

    def test_latency_is_observed_for_a_matched_route(self):
        before = sample("tavuno_http_request_duration_seconds_count", method="GET", route="/health")
        self.client.get("/health")
        after = sample("tavuno_http_request_duration_seconds_count", method="GET", route="/health")
        self.assertGreater(after, before)


class PlaybackSessionMetricTests(unittest.TestCase):
    """Session starts are counted per content type."""

    def test_counts_a_started_session(self):
        before = sample("tavuno_playback_sessions_started_total", content_type="live")
        prometheus_metrics.observe_playback_session("live")
        self.assertEqual(before + 1, sample("tavuno_playback_sessions_started_total", content_type="live"))

    def test_unknown_content_type_does_not_raise(self):
        prometheus_metrics.observe_playback_session("not-a-real-type")


class TimestampParsingTests(unittest.TestCase):
    """The staleness gauge reads the ``synced_at`` field the sync now writes."""

    def test_parses_a_zulu_timestamp(self):
        self.assertIsNotNone(prometheus_metrics._parse_timestamp("2026-10-02T12:00:00Z"))

    def test_parses_an_offset_timestamp(self):
        self.assertIsNotNone(prometheus_metrics._parse_timestamp("2026-10-02T12:00:00+00:00"))

    def test_rejects_garbage_and_empty_input(self):
        self.assertIsNone(prometheus_metrics._parse_timestamp("not a timestamp"))
        self.assertIsNone(prometheus_metrics._parse_timestamp(""))
        self.assertIsNone(prometheus_metrics._parse_timestamp(None))

    def test_gauge_is_zero_when_a_sync_has_never_run(self):
        """0 means "never" — hence the ``> 0`` guard in the alert rule."""
        services = MagicMock()
        services.connection.return_value.__enter__.return_value = MagicMock()
        services.redis.ping.return_value = True
        services.sync.last_sync.return_value = None
        prometheus_metrics.refresh_scrape_gauges(services)
        self.assertEqual(0.0, sample("tavuno_dispatcharr_sync_last_success_timestamp_seconds"))


if __name__ == "__main__":
    unittest.main()
