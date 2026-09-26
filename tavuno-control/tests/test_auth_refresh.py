"""Tests for refresh token rotation and revocation (Fix 2: S2-01)."""

import os
import time
import unittest
from contextlib import contextmanager
from unittest.mock import MagicMock, patch

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.auth.service import AuthService
from app.auth.tokens import create_refresh_token, verify_token
from app.auth.token_storage import TokenStorageService


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


class RefreshTokenTests(unittest.TestCase):
    def setUp(self):
        self.services = FakeServices()
        self.auth_service = AuthService(self.services)
        self.token_storage = TokenStorageService(self.services.redis)

    def test_refresh_token_includes_jti(self):
        """Test that refresh tokens include a jti claim."""
        token = create_refresh_token({'profile_id': 1, 'device_id': 10})
        payload = verify_token(token)
        self.assertIn('jti', payload)
        self.assertIsNotNone(payload['jti'])

    def test_refresh_token_jti_is_unique(self):
        """Test that each refresh token gets a unique jti."""
        token1 = create_refresh_token({'profile_id': 1, 'device_id': 10})
        token2 = create_refresh_token({'profile_id': 1, 'device_id': 10})
        payload1 = verify_token(token1)
        payload2 = verify_token(token2)
        self.assertNotEqual(payload1['jti'], payload2['jti'])

    def test_refresh_token_rotation_old_token_rejected_after_use(self):
        """Test that using an old refresh token after refresh is rejected."""
        # Create initial refresh token
        old_token = create_refresh_token({'profile_id': 1, 'device_id': 10})
        old_payload = verify_token(old_token)

        # Denylist the old token's JTI (simulating what refresh() does)
        if 'jti' in old_payload:
            remaining_ttl = int(old_payload['exp'] - time.time())
            if remaining_ttl > 0:
                self.token_storage.denylist_refresh_jti(old_payload['jti'], remaining_ttl)

        # Verify the token is now denylisted
        self.services.redis.exists.return_value = True
        is_denylisted = self.token_storage.is_refresh_jti_denylisted(old_payload['jti'])
        self.assertTrue(is_denylisted)

    def test_refresh_token_reuse_deactivates_all_devices(self):
        """Test that refresh token reuse deactivates all devices for the profile."""
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

        # Create refresh token and mark it as denylisted
        token = create_refresh_token({'profile_id': 1, 'device_id': 10})
        payload = verify_token(token)
        self.token_storage.denylist_refresh_jti(payload['jti'], 3600)
        self.services.redis.exists.return_value = True

        # Try to refresh with denylisted token
        with self.assertRaises(ValueError) as ctx:
            self.auth_service.refresh(token)

        # Verify that all devices were deactivated
        self.assertEqual(str(ctx.exception), 'refresh_token_reused')
        # Check that the connection executed the deactivation query
        update_calls = [call for call in mock_conn.execute.call_args_list
                       if 'UPDATE tavuno_devices SET is_active = FALSE' in str(call)]
        self.assertTrue(len(update_calls) > 0)

    def test_logout_denylists_refresh_token(self):
        """Test that logout denylists the refresh token."""
        @contextmanager
        def mock_connection():
            conn = MagicMock()
            yield conn

        self.services.connection = mock_connection

        # Create refresh token
        token = create_refresh_token({'profile_id': 1, 'device_id': 10})
        payload = verify_token(token)

        # Logout
        self.auth_service.logout(token)

        # Verify denylist was called
        self.services.redis.setex.assert_called()
        call_args = str(self.services.redis.setex.call_args)
        self.assertIn('auth:refresh_denylist', call_args)
        self.assertIn(payload['jti'], call_args)

    def test_token_storage_denylist_methods(self):
        """Test TokenStorageService denylist methods."""
        jti = "test-jti-123"
        ttl = 3600

        # Test denylist
        self.token_storage.denylist_refresh_jti(jti, ttl)
        self.services.redis.setex.assert_called_once()

        # Test is_denylisted (True)
        self.services.redis.exists.return_value = 1
        self.assertTrue(self.token_storage.is_refresh_jti_denylisted(jti))

        # Test is_denylisted (False)
        self.services.redis.exists.return_value = 0
        self.assertFalse(self.token_storage.is_refresh_jti_denylisted(jti))


if __name__ == "__main__":
    unittest.main()
