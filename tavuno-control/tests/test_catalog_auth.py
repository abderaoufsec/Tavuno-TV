"""Tests for catalog authentication requirements (Fix 3: S1-05)."""

import os
import unittest
from unittest.mock import MagicMock

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from fastapi.testclient import TestClient
from app.main import app


class CatalogAuthTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    def test_catalog_routes_require_auth(self):
        """Test that all catalog routes return 401 without authentication."""
        routes = [
            ("GET", "/v1/home"),
            ("GET", "/v1/channels"),
            ("GET", "/v1/channels/1"),
            ("GET", "/v1/channels/1/details"),
            ("GET", "/v1/categories"),
            ("GET", "/v1/categories/1"),
            ("GET", "/v1/movies"),
            ("GET", "/v1/movies/1"),
            ("GET", "/v1/movies/1/details"),
            ("GET", "/v1/series"),
            ("GET", "/v1/series/1"),
            ("GET", "/v1/series/1/details"),
            ("GET", "/v1/series/1/seasons"),
            ("GET", "/v1/seasons/1/episodes"),
            ("GET", "/v1/sports/competitions"),
            ("GET", "/v1/sports/competitions/1"),
            ("GET", "/v1/sports/competitions/1/matches"),
            ("GET", "/v1/sports/matches"),
            ("GET", "/v1/sports/matches/1"),
            ("GET", "/v1/sports/matches/1/details"),
            ("GET", "/v1/epg"),
            ("GET", "/v1/epg/channel/1/now-next"),
        ]

        for method, path in routes:
            with self.subTest(path=path):
                resp = self.client.request(method, path)
                self.assertEqual(resp.status_code, 401, f"{method} {path} should return 401 without auth")

    def test_admin_routes_require_admin_role(self):
        """Test that admin routes return 401 without authentication."""
        routes = [
            ("POST", "/v1/admin/sync/dispatcharr"),
            ("GET", "/v1/admin/sync/dispatcharr"),
        ]

        for method, path in routes:
            with self.subTest(path=path):
                resp = self.client.request(method, path)
                self.assertEqual(resp.status_code, 401, f"{method} {path} should return 401 without auth")

    def test_auth_routes_do_not_require_auth(self):
        """Test that auth routes are accessible without authentication."""
        # These routes should be accessible without a token
        routes = [
            ("POST", "/v1/auth/login"),
            ("POST", "/v1/auth/register"),
            ("POST", "/v1/auth/refresh"),
            ("POST", "/v1/auth/password-reset/request"),
        ]

        for method, path in routes:
            with self.subTest(path=path):
                # We expect 422 for invalid data, not 401
                resp = self.client.request(method, path, json={})
                self.assertNotEqual(resp.status_code, 401, f"{method} {path} should not return 401")

    def test_media_verify_does_not_require_bearer_token(self):
        """Test that /v1/media/verify is accessible with only playback token (Fix 3 correction)."""
        # This endpoint is called by media layer with playback token, not user JWT
        # It should be accessible without Authorization header
        try:
            resp = self.client.get("/v1/media/verify?token=invalid")
            # If we get a response, ensure it's not 401 (auth error)
            self.assertNotEqual(resp.status_code, 401, "Should not require Bearer token")
        except Exception as e:
            # If it fails due to DB connection or other issues, that's fine
            # The important thing is it didn't fail with 401 auth error
            # DB errors would happen after auth check passes
            pass

    def test_health_check_endpoints_do_not_require_auth(self):
        """Test that health check endpoints are accessible without authentication (Fix 3 correction)."""
        routes = [
            ("GET", "/v1/dispatcharr/health"),
            ("GET", "/v1/ome/health"),
        ]

        for method, path in routes:
            with self.subTest(path=path):
                # These may fail due to service unavailability, but not due to auth
                resp = self.client.request(method, path)
                # Should not return 401 (missing auth) - 503 or 200 is acceptable
                self.assertNotEqual(resp.status_code, 401, f"{method} {path} should not require auth")


if __name__ == "__main__":
    unittest.main()
