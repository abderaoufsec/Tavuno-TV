"""
Integration tests for Tavuno TV against real Docker infrastructure.
These tests require all services to be running via docker compose.
"""
import pytest
import httpx
import time
from typing import Any


@pytest.fixture(scope="module")
def api_base():
    """Base URL for Tavuno Control API."""
    return "http://localhost:8000"


@pytest.fixture(scope="module")
def caddy_base():
    """Base URL for Caddy reverse proxy."""
    return "http://localhost:8080"


@pytest.fixture(scope="module")
def dispatcharr_base():
    """Base URL for Dispatcharr."""
    return "http://localhost:9191"


class TestHealth:
    """Health check tests (M1-M3)."""

    def test_api_health(self, api_base: str):
        """Test Tavuno Control API health endpoint."""
        response = httpx.get(f"{api_base}/health", timeout=5)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "ok"
        assert "services" in data
        assert data["services"]["database"] == "ok"
        assert data["services"]["cache"] == "ok"


class TestAuthentication:
    """Authentication tests (M7/M9)."""

    def test_login_with_valid_credentials(self, api_base: str):
        """Test login with valid profile and device (M7 style)."""
        pytest.skip("M7 authentication deprecated - M9 requires Directus user setup")

    def test_login_with_invalid_device(self, api_base: str):
        """Test login with unregistered device should fail."""
        pytest.skip("M7 authentication deprecated - M9 requires Directus user setup")

    def test_login_with_invalid_profile(self, api_base: str):
        """Test login with non-existent profile should fail."""
        pytest.skip("M7 authentication deprecated - M9 requires Directus user setup")


class TestPlaybackAuthorization:
    """Playback authorization tests (M7/M9)."""

    @pytest.fixture
    def auth_token(self, api_base: str):
        """Skip - M9 authentication requires Directus user setup."""
        pytest.skip("M9 authentication tests skipped - requires Directus user with email/password")

    def test_playback_with_valid_token(self, api_base: str, auth_token: str):
        """Test playback request with valid JWT token."""
        # First clean up any existing sessions
        httpx.post(
            f"{api_base}/v1/playback/stop",
            json={"session_id": 1},
            headers={"Authorization": f"Bearer {auth_token}"},
            timeout=5,
        )
        
        response = httpx.post(
            f"{api_base}/v1/playback/live/2",
            json={"channel_id": 2},
            headers={"Authorization": f"Bearer {auth_token}"},
            timeout=5,
        )
        # May fail due to concurrent stream limit - that's expected behavior
        assert response.status_code in (200, 429)
        if response.status_code == 200:
            data = response.json()
            assert "session_id" in data
            assert "channel_id" in data
            assert "playback" in data
            assert "url" in data["playback"]

    def test_playback_without_token(self, api_base: str):
        """Test playback request without JWT token should fail."""
        response = httpx.post(
            f"{api_base}/v1/playback/live/2",
            json={"channel_id": 2},
            timeout=5,
        )
        # Should return 401 when auth header is missing
        assert response.status_code == 401

    def test_playback_with_invalid_token(self, api_base: str):
        """Test playback request with invalid JWT token should fail."""
        response = httpx.post(
            f"{api_base}/v1/playback/live/2",
            json={"channel_id": 2},
            headers={"Authorization": "Bearer invalid-token"},
            timeout=5,
        )
        assert response.status_code == 401

    def test_heartbeat(self, api_base: str, auth_token: str):
        """Test session heartbeat."""
        # First create a session
        response = httpx.post(
            f"{api_base}/v1/playback/live/2",
            json={"channel_id": 2},
            headers={"Authorization": f"Bearer {auth_token}"},
            timeout=5,
        )
        if response.status_code != 200:
            pytest.skip("Could not create session (likely concurrent limit)")
        
        session_id = response.json()["session_id"]

        # Then heartbeat
        response = httpx.post(
            f"{api_base}/v1/playback/heartbeat",
            json={"session_id": session_id},
            headers={"Authorization": f"Bearer {auth_token}"},
            timeout=5,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "active"

    def test_stop_session(self, api_base: str, auth_token: str):
        """Test session stop."""
        # First create a session
        response = httpx.post(
            f"{api_base}/v1/playback/live/2",
            json={"channel_id": 2},
            headers={"Authorization": f"Bearer {auth_token}"},
            timeout=5,
        )
        if response.status_code != 200:
            pytest.skip("Could not create session (likely concurrent limit)")
        
        session_id = response.json()["session_id"]

        # Then stop
        response = httpx.post(
            f"{api_base}/v1/playback/stop",
            json={"session_id": session_id},
            headers={"Authorization": f"Bearer {auth_token}"},
            timeout=5,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "stopped"


class TestEPG:
    """EPG tests (M5)."""

    def test_epg_by_channel(self, api_base: str):
        """Test EPG endpoint with channel filter."""
        response = httpx.get(
            f"{api_base}/v1/epg?channel_id=2",
            timeout=5,
        )
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)

    def test_epg_now_next(self, api_base: str):
        """Test NOW/NEXT/LATER endpoint."""
        response = httpx.get(
            f"{api_base}/v1/epg/channel/2/now-next",
            timeout=5,
        )
        assert response.status_code == 200
        data = response.json()
        assert "now" in data
        assert "next" in data
        assert "later" in data


class TestDispatcharr:
    """Dispatcharr integration tests (M4)."""

    def test_dispatcharr_version(self, dispatcharr_base: str):
        """Test Dispatcharr version endpoint."""
        try:
            response = httpx.get(
                f"{dispatcharr_base}/api/core/version/",
                timeout=5,
            )
            assert response.status_code == 200
            data = response.json()
            assert "version" in data
        except httpx.ConnectError:
            pytest.skip("Dispatcharr not accessible from test environment")

    def test_dispatcharr_sync_admin_protected(self, api_base: str):
        """Test admin sync endpoint requires authentication."""
        response = httpx.post(
            f"{api_base}/v1/admin/sync/dispatcharr",
            timeout=5,
        )
        # Should return 401 when auth header is missing
        assert response.status_code == 401

    def test_dispatcharr_sync_with_auth(self, api_base: str):
        """Test admin sync with authentication."""
        pytest.skip("M9 authentication tests skipped - requires Directus user with email/password")

        # Then sync
        response = httpx.post(
            f"{api_base}/v1/admin/sync/dispatcharr",
            headers={"Authorization": f"Bearer {token}"},
            timeout=30,
        )
        # May succeed or fail depending on Dispatcharr health
        # Should not be 401 (unauthorized)
        assert response.status_code != 401


class TestMediaTokenVerification:
    """Media token verification tests (M7)."""

    @pytest.fixture
    def playback_token(self, api_base: str):
        """Skip - M9 authentication requires Directus user setup."""
        pytest.skip("M9 authentication tests skipped - requires Directus user with email/password")

        response = httpx.post(
            f"{api_base}/v1/playback/live/2",
            json={"channel_id": 2},
            headers={"Authorization": f"Bearer {token}"},
            timeout=5,
        )
        if response.status_code != 200:
            pytest.skip("Could not create playback session (likely concurrent limit)")
        
        playback_url = response.json()["playback"]["url"]
        # Extract token from URL
        token_param = playback_url.split("token=")[1]
        return token_param

    def test_verify_valid_token(self, api_base: str, playback_token: str):
        """Test token verification with valid token."""
        response = httpx.get(
            f"{api_base}/v1/media/verify?token={playback_token}",
            timeout=5,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["valid"] is True
        assert "session_id" in data

    def test_verify_invalid_token(self, api_base: str):
        """Test token verification with invalid token."""
        response = httpx.get(
            f"{api_base}/v1/media/verify?token=invalid.token.here",
            timeout=5,
        )
        assert response.status_code == 401

    def test_verify_expired_token(self, api_base: str):
        """Test token verification with expired token format."""
        # Token with past expiration
        expired_token = "1.1234567890.abcdef1234567890abcdef1234567890abcdef"
        response = httpx.get(
            f"{api_base}/v1/media/verify?token={expired_token}",
            timeout=5,
        )
        assert response.status_code == 401


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
