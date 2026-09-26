"""Tests for device list authentication (Fix 6: S2-06)."""

import os
import unittest
from contextlib import contextmanager
from unittest.mock import MagicMock

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.devices.service import DeviceService
from app.devices.models import DeviceDto


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


class DeviceAuthTests(unittest.TestCase):
    def setUp(self):
        self.services = FakeServices()
        self.device_service = DeviceService(self.services)

    def test_list_devices_no_longer_requires_fingerprint_header(self):
        """Test that list_devices uses device_id from token instead of header (Fix 6)."""
        @contextmanager
        def mock_connection():
            mock_conn = MagicMock()
            mock_conn.execute.return_value.fetchall.return_value = [
                {
                    'id': 10,
                    'display_name': 'Device 1',
                    'platform': 'android-tv',
                    'last_seen_at': '2026-09-26',
                    'revoked_at': None,
                    'device_fingerprint': 'fp1'
                },
                {
                    'id': 11,
                    'display_name': 'Device 2',
                    'platform': 'ios',
                    'last_seen_at': '2026-09-25',
                    'revoked_at': None,
                    'device_fingerprint': 'fp2'
                }
            ]
            yield mock_conn
        
        self.services.connection = mock_connection
        
        # Call with current_device_id instead of fingerprint
        devices = self.device_service.list_devices(1, current_device_id=10)
        
        self.assertEqual(len(devices), 2)
        self.assertTrue(devices[0].is_current)
        self.assertFalse(devices[1].is_current)

    def test_list_devices_marks_current_device(self):
        """Test that list_devices marks the current device correctly (Fix 6)."""
        @contextmanager
        def mock_connection():
            mock_conn = MagicMock()
            mock_conn.execute.return_value.fetchall.return_value = [
                {
                    'id': 10,
                    'display_name': 'Device 1',
                    'platform': 'android-tv',
                    'last_seen_at': '2026-09-26',
                    'revoked_at': None,
                    'device_fingerprint': 'fp1'
                },
                {
                    'id': 11,
                    'display_name': 'Device 2',
                    'platform': 'ios',
                    'last_seen_at': '2026-09-25',
                    'revoked_at': None,
                    'device_fingerprint': 'fp2'
                }
            ]
            yield mock_conn
        
        self.services.connection = mock_connection
        
        # Test with device_id 10 as current
        devices = self.device_service.list_devices(1, current_device_id=10)
        current = [d for d in devices if d.id == 10]
        others = [d for d in devices if d.id != 10]
        
        self.assertTrue(current and current[0].is_current)
        self.assertTrue(all(not d.is_current for d in others))


if __name__ == "__main__":
    unittest.main()
