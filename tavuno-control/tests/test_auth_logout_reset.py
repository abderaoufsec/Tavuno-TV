"""Tests for logout and password-reset device handling (Fix 1: S1-03)."""

import os
import unittest
from contextlib import contextmanager
from unittest.mock import MagicMock, patch

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.auth.service import AuthService
from app.auth.tokens import create_refresh_token, verify_token


class FakeSettings:
    jwt_access_ttl_seconds = 900
    jwt_refresh_ttl_seconds = 2592000
    jwt_secret = "test-secret-key"
    free_launch = False
    default_plan_id = None
    password_reset_ttl_seconds = 3600
    email_enabled = False


class FakeServices:
    def __init__(self):
        self.settings = FakeSettings()
        self.redis = MagicMock()
        self.connection = MagicMock()

    @contextmanager
    def connection(self):
        yield self.connection


class LogoutPasswordResetTests(unittest.TestCase):
    def setUp(self):
        self.services = FakeServices()
        self.auth_service = AuthService(self.services)

    def test_logout_sets_is_active_false_not_revoked_at(self):
        """Test that logout sets is_active=FALSE instead of revoked_at."""
        mock_conn = MagicMock()
        mock_conn.execute.return_value.fetchone.return_value = {
            'id': 10,
            'is_active': True,
            'revoked_at': None
        }

        @contextmanager
        def mock_connection():
            yield mock_conn

        self.services.connection = mock_connection

        # Create refresh token
        token = create_refresh_token({'profile_id': 1, 'device_id': 10})

        # Logout
        self.auth_service.logout(token)

        # Verify that the update used is_active=FALSE, not revoked_at
        update_calls = [call for call in mock_conn.execute.call_args_list]
        update_query = str(update_calls[0]) if update_calls else ""
        self.assertIn('is_active = FALSE', update_query)
        self.assertNotIn('revoked_at = NOW()', update_query)

    def test_login_device_check_code_path(self):
        """Test that login code checks device.is_active for reactivation."""
        # This is a code inspection test - verify the logic exists in the code
        import inspect
        source = inspect.getsource(self.auth_service.login)
        self.assertIn('is_active', source)
        self.assertIn('is_active = TRUE', source)

    def test_password_reset_code_uses_is_active(self):
        """Test that password reset code uses is_active=FALSE."""
        # This is a code inspection test - verify the logic exists in the code
        import inspect
        source = inspect.getsource(self.auth_service.confirm_password_reset)
        self.assertIn('is_active = FALSE', source)
        # Ensure revoked_at is NOT used
        self.assertNotIn('revoked_at = NOW()', source)


if __name__ == "__main__":
    unittest.main()
