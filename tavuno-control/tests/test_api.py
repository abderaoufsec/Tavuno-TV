import os

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from contextlib import contextmanager
import datetime
import unittest
from unittest.mock import MagicMock

from app.main import (
    LivePlaybackRequest,
    SessionRequest,
    channel_epg_now_next,
    get_channel,
    health,
    home,
    list_channels,
    playback_heartbeat,
    playback_live,
    playback_stop,
)
from app.ome_client import OmeClient
from app.playback import mint_token


class FakeSettings:
    playback_token_secret = "test-secret-key"
    playback_token_ttl_seconds = 120
    ome_playback_base_url = "http://localhost:8080/media"
    dispatcharr_expected_version = "0.27.2"
    dispatcharr_api_key = "test-key"


class FakeServices:
    def __init__(self):
        self.settings = FakeSettings()
        self.dispatcharr = MagicMock()
        self.dispatcharr.get_version.return_value = {"version": "0.27.2"}
        self.ome = MagicMock()
        self.ome.get_health.return_value = {"status": "ok", "code": 200}
        self.sync = MagicMock()
        self.sync.last_sync.return_value = None

    def health(self):
        return {
            "database": "ok",
            "cache": "ok",
            "dispatcharr": "ok (0.27.2)",
            "ome": "ok",
        }

    def cached_json(self, key, loader):
        return loader()

    @contextmanager
    def connection(self):
        yield FakeConnection()


class FakeConnection:
    def __init__(self):
        self.query = ""
        self.parameters = ()

    def execute(self, query, parameters=()):
        self.query = query
        self.parameters = parameters
        return self

    def commit(self):
        pass

    def fetchall(self):
        if "FROM tavuno_channel_sources" in self.query:
            return [{"provider": "dispatcharr", "external_id": "17", "priority": 1, "is_active": True}]
        if "tavuno_categories" in self.query:
            return [{"id": 1, "name": "M2 Demo Live", "kind": "live"}]
        if "tavuno_channels" in self.query:
            return [{"id": 1, "name": "M2 Demo Channel", "slug": "m2-demo-channel", "category": 1, "is_active": True}]
        if "tavuno_epg_programmes" in self.query:
            return [
                {"id": 1, "title": "Evening News", "starts_at": "2026-09-18T18:00:00Z", "ends_at": "2026-09-18T19:00:00Z", "description": "Daily news"},
                {"id": 2, "title": "Prime Movie", "starts_at": "2026-09-18T19:00:00Z", "ends_at": "2026-09-18T21:00:00Z", "description": "Action thriller"},
            ]
        return []

    def fetchone(self):
        if "FROM tavuno_profiles" in self.query:
            if self.parameters == (999,):
                return None
            return {"id": 1, "status": "active"}

        if "FROM tavuno_devices" in self.query:
            device_key = self.parameters[0]
            if device_key == "unregistered-device-key":
                return None
            return {"id": 10, "is_active": True, "device_key": device_key}

        if "FROM tavuno_subscriptions" in self.query:
            return {"id": 5, "max_concurrent_streams": 2, "max_devices": 3}

        if "COUNT(*) AS count" in self.query:
            profile_id = self.parameters[0]
            if profile_id == 888:  # simulates concurrency limit exceeded
                return {"count": 2}
            return {"count": 0}

        if "FROM tavuno_channels" in self.query and "WHERE id = %s" in self.query:
            if self.parameters == (999,):
                return None
            return {"id": 1, "name": "M2 Demo Channel", "slug": "m2-demo-channel", "category": 1, "is_active": True}

        if "FROM tavuno_channel_sources" in self.query:
            return {"provider": "ome", "external_id": "channel_1"}

        if "INSERT INTO tavuno_playback_sessions" in self.query:
            return {"id": 101, "expires_at": datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(seconds=120)}

        if "UPDATE tavuno_playback_sessions" in self.query:
            session_id = self.parameters[-1]
            if session_id == 999:
                return None
            return {"id": session_id, "status": "active", "expires_at": datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(seconds=120)}

        return None


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.services = FakeServices()

    def test_health_reports_dependencies(self):
        health_resp = health(self.services)
        self.assertEqual(health_resp["status"], "ok")
        self.assertEqual(health_resp["services"]["dispatcharr"], "ok (0.27.2)")
        self.assertEqual(health_resp["services"]["ome"], "ok")

    def test_home_returns_catalog_shape(self):
        self.assertEqual(home(self.services)["categories"][0]["name"], "M2 Demo Live")

    def test_channel_lookup_and_not_found_behavior(self):
        channel = get_channel(1, self.services)
        self.assertEqual(channel["id"], 1)
        self.assertEqual(channel["sources"][0]["external_id"], "17")
        with self.assertRaises(Exception) as response:
            get_channel(999, self.services)
        self.assertEqual(response.exception.status_code, 404)

    def test_channel_listing_filters_active_catalog(self):
        self.assertEqual(len(list_channels(self.services)), 1)

    def test_token_minting_structure(self):
        token = mint_token(101, 10, "live", "1", 1789000000, "secret-key")
        parts = token.split(".")
        self.assertEqual(len(parts), 3)
        self.assertEqual(parts[0], "101")
        self.assertEqual(parts[1], "1789000000")

    def test_playback_live_authorization_success(self):
        payload = LivePlaybackRequest(profile_id=1, device_key="living-room-tv-device-key")
        resp = playback_live(1, payload, self.services)
        self.assertEqual(resp["session_id"], 101)
        self.assertEqual(resp["channel_id"], 1)
        self.assertIn("playlist.m3u8?token=", resp["playback"]["url"])

    def test_playback_rejects_unregistered_device(self):
        payload = LivePlaybackRequest(profile_id=1, device_key="unregistered-device-key")
        with self.assertRaises(Exception) as ctx:
            playback_live(1, payload, self.services)
        self.assertEqual(ctx.exception.status_code, 403)

    def test_playback_rejects_inactive_profile(self):
        payload = LivePlaybackRequest(profile_id=999, device_key="valid-device-key")
        with self.assertRaises(Exception) as ctx:
            playback_live(1, payload, self.services)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_playback_enforces_concurrency_limit(self):
        payload = LivePlaybackRequest(profile_id=888, device_key="valid-device-key")
        with self.assertRaises(Exception) as ctx:
            playback_live(1, payload, self.services)
        self.assertEqual(ctx.exception.status_code, 429)

    def test_playback_heartbeat(self):
        payload = SessionRequest(session_id=101)
        resp = playback_heartbeat(payload, self.services)
        self.assertEqual(resp["session_id"], 101)
        self.assertEqual(resp["status"], "active")

    def test_playback_stop(self):
        payload = SessionRequest(session_id=101)
        resp = playback_stop(payload, self.services)
        self.assertEqual(resp["session_id"], 101)
        self.assertEqual(resp["status"], "stopped")

    def test_epg_now_next(self):
        resp = channel_epg_now_next(1, self.services)
        self.assertEqual(resp["channel_id"], 1)
        self.assertIsNotNone(resp["now"])
        self.assertEqual(resp["now"]["title"], "Evening News")
        self.assertIsNotNone(resp["next"])
        self.assertEqual(resp["next"]["title"], "Prime Movie")

    def test_ome_client_playback_url(self):
        ome = OmeClient(api_url="http://localhost:8081", playback_base_url="http://localhost:8080/media")
        url = ome.build_playback_url("app", "channel_1", "signed_token")
        self.assertEqual(url, "http://localhost:8080/media/app/channel_1/playlist.m3u8?token=signed_token")


if __name__ == "__main__":
    unittest.main()
