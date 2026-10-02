"""Tests for the read-only M15 ops dashboard.

The properties worth pinning:

* **The page carries no secret.** `/v1/ops` must not embed the ops token —
  the page fetches it from sessionStorage, so the document can be served
  openly without leaking anything.
* **The data endpoint is gated**, in constant time, and the gate does not
  depend on the login system that free-launch mode deliberately disables.
* **Degrades section by section.** A missing ledger table, a dead dependency
  or an unmapped table must each produce a field, not a 500.
* **Read-only.** Nothing under /v1/ops may write.
"""

import os
import unittest
from contextlib import contextmanager
from unittest.mock import MagicMock

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from fastapi.testclient import TestClient

from app.main import app
from app.ops import DEFAULT_OPS_TOKEN, OpsService, token_matches

# Counts returned per table, so a test can assert what it saw rather than
# just "some number".
TABLE_COUNTS = {
    "tavuno_channels": 42,
    "tavuno_categories": 5,
    "tavuno_movies": 11,
    "tavuno_series": 3,
    "tavuno_episodes": 24,
    "tavuno_epg_programmes": 319,
    "tavuno_profiles": 2,
}


class FakeCursor:
    def __init__(self, rows=None, row=None):
        self._rows = rows if rows is not None else []
        self._row = row

    def fetchall(self):
        return self._rows

    def fetchone(self):
        return self._row


class FakeConnection:
    """Answers the handful of reads the dashboard performs.

    ``ledger=False`` simulates a database where TAVUNO_AUTO_MIGRATE has never
    run, so ``tavuno_schema_migrations`` does not exist yet.
    """

    def __init__(self, ledger=True):
        self.ledger = ledger

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def execute(self, query, parameters=()):
        if "tavuno_schema_migrations" in query:
            if not self.ledger:
                raise KeyError("relation \"tavuno_schema_migrations\" does not exist")
            return FakeCursor(rows=[{"filename": "001_first.sql"}, {"filename": "008_last.sql"}])
        if "tavuno_playback_sessions" in query:
            return FakeCursor(row={"count": 3})
        if "COUNT(*)" in query:
            table = query.split("FROM", 1)[1].split()[0]
            return FakeCursor(row={"count": TABLE_COUNTS.get(table, 7)})
        return FakeCursor(row={})


def make_services(ledger=True, broken=False):
    services = MagicMock()
    settings = MagicMock()
    settings.ops_token = DEFAULT_OPS_TOKEN
    settings.live_channel_allowlist = "8845,6860"
    settings.live_channel_limit = 10
    services.settings = settings

    if broken:
        services.connection.side_effect = RuntimeError("postgres gone")
        services.redis.ping.side_effect = RuntimeError("redis gone")
        services.health.side_effect = RuntimeError("postgres gone")
        services.sync.last_sync.side_effect = RuntimeError("redis gone")
    else:
        services.connection.side_effect = lambda: FakeConnection(ledger=ledger)
        services.health.return_value = {"database": "ok", "cache": "ok"}
        services.sync.last_sync.return_value = {
            "status": "success",
            "synced_at": "2026-10-02T10:00:00Z",
        }
    return services


class OpsPageTests(unittest.TestCase):
    """The HTML shell is open, and carries no secret."""

    def setUp(self):
        self.client = TestClient(app)

    def test_page_is_served_without_a_token(self):
        self.assertEqual(200, self.client.get("/v1/ops").status_code)

    def test_page_is_html(self):
        self.assertIn("text/html", self.client.get("/v1/ops").headers["content-type"])

    def test_page_does_not_embed_the_ops_token(self):
        """The secret must stay out of the document: it is typed by the user
        and sent as a header, so it never appears in HTML, a URL or a log."""
        body = self.client.get("/v1/ops").text
        self.assertNotIn(DEFAULT_OPS_TOKEN, body)
        self.assertNotIn("tavuno-ops-local", body)

    def test_page_references_the_summary_endpoint_it_polls(self):
        self.assertIn("/v1/ops/summary", self.client.get("/v1/ops").text)

    def test_no_write_routes_exist_under_ops(self):
        """Read-only by construction — CRUD belongs to Directus."""
        write_verbs = {"POST", "PUT", "PATCH", "DELETE"}
        for route in app.routes:
            path = getattr(route, "path", "")
            if path.startswith("/v1/ops"):
                with self.subTest(path=path, methods=getattr(route, "methods", None)):
                    self.assertFalse(write_verbs & set(getattr(route, "methods", set())))


class OpsTokenTests(unittest.TestCase):
    """The shared-secret gate and its constant-time comparison."""

    def test_correct_token_is_accepted(self):
        self.assertTrue(token_matches(DEFAULT_OPS_TOKEN, DEFAULT_OPS_TOKEN))

    def test_wrong_token_is_rejected(self):
        self.assertFalse(token_matches(DEFAULT_OPS_TOKEN, "wrong-token"))

    def test_missing_token_is_rejected(self):
        self.assertFalse(token_matches(DEFAULT_OPS_TOKEN, None))
        self.assertFalse(token_matches(DEFAULT_OPS_TOKEN, ""))

    def test_empty_expected_token_rejects_everything(self):
        """An unset expected token must fail closed, never open."""
        self.assertFalse(token_matches("", ""))
        self.assertFalse(token_matches("", "anything"))


class OpsSummaryEndpointTests(unittest.TestCase):
    """`GET /v1/ops/summary` is guarded and returns a full snapshot."""

    def setUp(self):
        self.previous_services = getattr(app.state, "services", None)
        self.client = TestClient(app)

    def tearDown(self):
        app.state.services = self.previous_services

    def get(self, token=None):
        headers = {"X-Ops-Token": token} if token is not None else {}
        return self.client.get("/v1/ops/summary", headers=headers)

    def test_missing_token_returns_401(self):
        app.state.services = make_services()
        self.assertEqual(401, self.get().status_code)

    def test_wrong_token_returns_401(self):
        app.state.services = make_services()
        self.assertEqual(401, self.get(token="nope").status_code)

    def test_correct_token_returns_200(self):
        app.state.services = make_services()
        self.assertEqual(200, self.get(token=DEFAULT_OPS_TOKEN).status_code)

    def test_snapshot_contains_every_section(self):
        app.state.services = make_services()
        data = self.get(token=DEFAULT_OPS_TOKEN).json()
        for key in ("generated_at", "health", "sync", "counts", "playback", "migrations", "scope"):
            with self.subTest(section=key):
                self.assertIn(key, data)

    def test_counts_come_from_the_queries(self):
        app.state.services = make_services()
        counts = self.get(token=DEFAULT_OPS_TOKEN).json()["counts"]
        self.assertEqual(42, counts["channels"])
        self.assertEqual(319, counts["epg_programmes"])
        self.assertEqual(2, counts["profiles"])

    def test_active_sessions_use_the_enforced_predicate(self):
        app.state.services = make_services()
        self.assertEqual(3, self.get(token=DEFAULT_OPS_TOKEN).json()["playback"]["active_sessions"])

    def test_migration_ledger_is_reported(self):
        app.state.services = make_services()
        migrations = self.get(token=DEFAULT_OPS_TOKEN).json()["migrations"]
        self.assertEqual(2, migrations["applied"])
        self.assertEqual("008_last.sql", migrations["latest"])

    def test_live_channel_scope_is_reported(self):
        app.state.services = make_services()
        scope = self.get(token=DEFAULT_OPS_TOKEN).json()["scope"]
        self.assertTrue(scope["restricted"])
        self.assertEqual(10, scope["limit"])
        self.assertEqual("8845,6860", scope["allowlist"])

    def test_never_migrated_database_yields_null_not_a_500(self):
        """The ledger table does not exist before the first auto-migrate."""
        app.state.services = make_services(ledger=False)
        response = self.get(token=DEFAULT_OPS_TOKEN)
        self.assertEqual(200, response.status_code)
        self.assertIsNone(response.json()["migrations"]["applied"])

    def test_a_dead_dependency_degrades_to_data_not_a_500(self):
        """The dashboard matters most during an outage, so it must still answer."""
        app.state.services = make_services(broken=True)
        response = self.get(token=DEFAULT_OPS_TOKEN)
        self.assertEqual(200, response.status_code)
        self.assertIn("error", response.json()["health"])

    def test_sync_reports_never_when_nothing_has_run(self):
        services = make_services()
        services.sync.last_sync.return_value = None
        app.state.services = services
        self.assertEqual("never", self.get(token=DEFAULT_OPS_TOKEN).json()["sync"]["status"])


if __name__ == "__main__":
    unittest.main()
