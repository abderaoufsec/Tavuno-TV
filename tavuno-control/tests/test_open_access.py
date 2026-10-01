"""Open-access mode tests (AUTH_OPEN_ACCESS free launch, no login required).

Covers:
- current_principal resolves the seeded guest identity when open access is on.
- Strict 401 behaviour is preserved when the flag is off (default).
- Playback endpoints authorize without a Bearer token in open access mode.
- Guest identity never gains admin privileges.
"""

import os
import unittest

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from fastapi.testclient import TestClient
from app.main import app

from tests.test_api import FakeServices, FakeSettings


class OpenAccessSettings(FakeSettings):
    auth_open_access = True
    auth_guest_email = "guest@tavuno.local"
    auth_guest_device_key = "tavuno-tv-guest"


class OpenAccessFakeServices(FakeServices):
    def __init__(self):
        super().__init__()
        self.settings = OpenAccessSettings()


class OpenAccessTests(unittest.TestCase):
    def setUp(self):
        self._original_services = getattr(app.state, "services", None)
        app.state.services = OpenAccessFakeServices()
        self.client = TestClient(app)

    def tearDown(self):
        app.state.services = self._original_services

    def test_guest_principal_created_without_credentials(self):
        """No Authorization header resolves to the seeded guest identity."""
        from app.auth.deps import current_principal

        class FakeRequest:
            class app:
                pass

        request = FakeRequest()
        request.app = app
        principal = current_principal(request, credentials=None)

        self.assertEqual(principal["profile_id"], 1)
        self.assertEqual(principal["device_id"], 10)
        self.assertTrue(principal["guest"])
        self.assertEqual(principal["role"], "user")

    def test_catalog_routes_accessible_without_token(self):
        """Catalog endpoints must not 401 when open access is enabled."""
        routes = [
            ("GET", "/v1/channels"),
            ("GET", "/v1/categories"),
            ("GET", "/v1/movies"),
            ("GET", "/v1/series"),
            ("GET", "/v1/epg"),
        ]
        for method, path in routes:
            with self.subTest(path=path):
                resp = self.client.request(method, path)
                self.assertNotEqual(
                    resp.status_code, 401,
                    f"{method} {path} must not require auth in open access mode",
                )

    def test_playback_live_authorizes_without_token(self):
        """Live playback authorizes with no Authorization header."""
        resp = self.client.post("/v1/playback/live/1")
        self.assertEqual(resp.status_code, 200, resp.text)
        body = resp.json()
        self.assertIn("session_id", body)
        self.assertEqual(body["channel_id"], 1)

    def test_playback_movie_authorizes_without_token(self):
        """Movie playback authorizes with no Authorization header."""
        resp = self.client.post("/v1/playback/movie/1")
        self.assertEqual(resp.status_code, 200, resp.text)

    def test_playback_episode_authorizes_without_token(self):
        """Episode playback authorizes with no Authorization header."""
        resp = self.client.post("/v1/playback/episode/1")
        self.assertEqual(resp.status_code, 200, resp.text)

    def test_invalid_token_still_falls_back_to_guest(self):
        """A stale cached token must not lock users out in open access mode."""
        resp = self.client.post(
            "/v1/playback/live/1",
            headers={"Authorization": "Bearer stale.token.value"},
        )
        self.assertEqual(resp.status_code, 200, resp.text)

    def test_guest_cannot_access_admin_routes(self):
        """Guest identity is role=user, so admin sync stays forbidden."""
        resp = self.client.post("/v1/admin/sync/dispatcharr")
        self.assertIn(resp.status_code, (401, 403), resp.text)


class StrictAuthRegressionTests(unittest.TestCase):
    """Default (flag off) behaviour must be unchanged from the auth baseline."""

    def setUp(self):
        self._original_services = getattr(app.state, "services", None)
        app.state.services = FakeServices()
        self.client = TestClient(app)

    def tearDown(self):
        app.state.services = self._original_services

    def test_catalog_routes_still_require_auth(self):
        resp = self.client.get("/v1/channels")
        self.assertEqual(resp.status_code, 401)

    def test_playback_still_requires_token(self):
        with self.assertRaises(Exception) as ctx:
            from app.main import playback_live
            playback_live(channel_id=1, services=FakeServices(), authorization=None)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_admin_routes_still_require_admin(self):
        resp = self.client.post("/v1/admin/sync/dispatcharr")
        self.assertEqual(resp.status_code, 401)


if __name__ == "__main__":
    unittest.main()
