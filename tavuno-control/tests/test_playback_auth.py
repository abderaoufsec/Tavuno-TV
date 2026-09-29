"""Tests for playback authentication (Fix 4: S1-06, Fix 9: S2-04)."""

import os
import unittest
from contextlib import contextmanager
from unittest.mock import MagicMock, patch

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.playback import authorize_live_playback, authorize_movie_playback, authorize_episode_playback


class FakeSettings:
    playback_token_secret = "test-secret-key"
    playback_token_ttl_seconds = 120
    ome_playback_base_url = "http://localhost:8080/media"
    ome_dvr_max_duration_seconds = 3600
    dispatcharr_url = "http://dispatcharr:9191"
    dispatcharr_api_key = "test-key"
    dispatcharr_timeout_seconds = 20.0


class FakeConnection:
    def __init__(self):
        self.query = ""
        self.parameters = ()
        self.commit_called = False
        self.fetchone_call_count = 0
        self.channel_source_provider = "ome"  # Default to OME for backward compatibility
        self.channel_source_external_id = "channel_1"
        self.has_entitlement = True
        self.has_subscription = True

    def execute(self, query, parameters=()):
        self.query = query
        self.parameters = parameters
        return self

    def commit(self):
        self.commit_called = True

    def fetchone(self):
        self.fetchone_call_count += 1
        if "FROM tavuno_profiles" in self.query:
            return {"id": 1, "status": "active"}
        if "FROM tavuno_devices" in self.query:
            # Return None to simulate deleted device
            if self.parameters == ("deleted-device-key", 1):
                return None
            return {"id": 10, "is_active": True}
        if "FROM tavuno_subscriptions" in self.query:
            if not self.has_subscription:
                return None
            return {"id": 5, "max_concurrent_streams": 2, "max_devices": 3}
        if "FROM tavuno_channels" in self.query and "WHERE id = %s" in self.query:
            return {"id": 1, "name": "Test Channel", "slug": "test-channel"}
        if "FROM tavuno_entitlements" in self.query:
            if not self.has_entitlement:
                return None
            return {"id": 1}
        if "FROM tavuno_channel_sources" in self.query:
            return {"provider": self.channel_source_provider, "external_id": self.channel_source_external_id}
        if "FROM tavuno_movies" in self.query and "WHERE id = %s" in self.query:
            return {"id": 1, "title": "Test Movie", "slug": "test-movie", "stream_url": "http://example.com/movie.m3u8"}
        if "FROM tavuno_episodes" in self.query and "WHERE e.id = %s" in self.query:
            return {
                "id": 1,
                "title": "Episode 1",
                "episode_number": 1,
                "stream_url": "http://example.com/episode.m3u8",
                "season_id": 1,
                "series_id": 1,
                "series_title": "Test Series"
            }
        if "INSERT INTO tavuno_playback_sessions" in self.query:
            return {"id": 101, "expires_at": "2099-12-31"}
        return None


class PlaybackAuthTests(unittest.TestCase):
    def setUp(self):
        self.settings = FakeSettings()

    def test_playback_with_deleted_device_returns_403(self):
        """Test that playback with a deleted device returns 403 (Fix 4)."""
        # Simulate device not found
        conn = FakeConnection()
        
        with self.assertRaises(Exception) as ctx:
            authorize_live_playback(
                profile_id=1,
                device_key="deleted-device-key",
                channel_id=1,
                connection=conn,
                settings=self.settings
            )
        
        # Should raise 403 with device not registered message
        self.assertIn("device_not_registered", str(ctx.exception).lower())

    def test_verify_playback_token_rejects_tampered_signature(self):
        """Test that verify_playback_token rejects tampered signatures (Fix 9)."""
        from app.playback import verify_playback_token
        from fastapi import HTTPException
        
        @contextmanager
        def mock_connection():
            mock_conn = MagicMock()
            mock_conn.execute.return_value.fetchone.return_value = {
                "id": 101,
                "profile": 1,
                "device": 10,
                "content_type": "live",
                "content_key": "1",
                "status": "active",
                "expires_at": "2099-12-31"
            }
            yield mock_conn
        
        # Create a token with wrong signature
        token = "101.9999999999.wrongsignature"
        
        with mock_connection() as conn:
            with self.assertRaises(HTTPException) as ctx:
                verify_playback_token(token, conn, self.settings.playback_token_secret)
        
        self.assertEqual(ctx.exception.status_code, 401)
        self.assertIn("signature", str(ctx.exception.detail).lower())

    def test_live_playback_includes_dvr_fields(self):
        """Test that live playback response includes DVR capability fields (M13)."""
        conn = FakeConnection()
        
        result = authorize_live_playback(
            profile_id=1,
            device_key="test-device-key",
            channel_id=1,
            connection=conn,
            settings=self.settings
        )
        
        # Verify DVR fields are present (OME/DVR mode)
        self.assertIn("playback", result)
        self.assertIn("dvr_enabled", result["playback"])
        self.assertTrue(result["playback"]["dvr_enabled"])
        self.assertIn("max_rewind_seconds", result["playback"])
        self.assertEqual(result["playback"]["max_rewind_seconds"], 3600)
        self.assertEqual(result["playback"]["protocol"], "hls")

    def test_direct_hls_playback_mode(self):
        """Test that Dispatcharr direct HLS playback mode works correctly."""
        from unittest.mock import patch
        # Mock Dispatcharr client to return a stream URL
        with patch('app.dispatcharr_client.DispatcharrClient') as mock_dispatcharr_client_class:
            mock_dispatcharr = MagicMock()
            mock_dispatcharr.get_stream_by_id.return_value = {
                "id": 123,
                "url": "https://example.com/stream.m3u8",
                "name": "Test Stream"
            }
            mock_dispatcharr_client_class.return_value = mock_dispatcharr

            conn = FakeConnection()
            conn.channel_source_provider = "dispatcharr"
            conn.channel_source_external_id = "123"
            
            result = authorize_live_playback(
                profile_id=1,
                device_key="test-device-key",
                channel_id=1,
                connection=conn,
                settings=self.settings
            )
            
            # Verify direct HLS mode
            self.assertIn("playback", result)
            self.assertEqual(result["playback"]["protocol"], "http_hls")
            self.assertFalse(result["playback"]["dvr_enabled"])
            self.assertEqual(result["playback"]["max_rewind_seconds"], 0)
            self.assertIn("https://example.com/stream.m3u8", result["playback"]["url"])
            # Direct HLS should not have token appended
            self.assertNotIn("token=", result["playback"]["url"])

    def test_ome_dvr_mode_unchanged(self):
        """Test that OME/DVR mode remains unchanged when provider is 'ome'."""
        from unittest.mock import patch
        with patch('app.dispatcharr_client.DispatcharrClient') as mock_dispatcharr_client_class:
            conn = FakeConnection()
            conn.channel_source_provider = "ome"
            conn.channel_source_external_id = "custom_stream"
            
            result = authorize_live_playback(
                profile_id=1,
                device_key="test-device-key",
                channel_id=1,
                connection=conn,
                settings=self.settings
            )
            
            # Verify OME/DVR mode
            self.assertIn("playback", result)
            self.assertEqual(result["playback"]["protocol"], "hls")
            self.assertTrue(result["playback"]["dvr_enabled"])
            self.assertEqual(result["playback"]["max_rewind_seconds"], 3600)
            self.assertIn("custom_stream", result["playback"]["stream_name"])
            self.assertIn("token=", result["playback"]["url"])
            # Dispatcharr client should not be called for OME sources
            mock_dispatcharr_client_class.assert_not_called()

    def test_missing_source_falls_back_to_ome_dvr(self):
        """Test that missing source falls back to OME/DVR mode."""
        from unittest.mock import patch
        with patch('app.dispatcharr_client.DispatcharrClient') as mock_dispatcharr_client_class:
            conn = FakeConnection()
            conn.channel_source_provider = None
            conn.channel_source_external_id = None
            
            result = authorize_live_playback(
                profile_id=1,
                device_key="test-device-key",
                channel_id=1,
                connection=conn,
                settings=self.settings
            )
            
            # Should fall back to OME/DVR
            self.assertIn("playback", result)
            self.assertEqual(result["playback"]["protocol"], "hls")
            self.assertTrue(result["playback"]["dvr_enabled"])

    def test_dispatcharr_fetch_failure_falls_back_to_ome_dvr(self):
        """Test that Dispatcharr fetch failure falls back to OME/DVR mode."""
        from unittest.mock import patch
        with patch('app.dispatcharr_client.DispatcharrClient') as mock_dispatcharr_client_class:
            # Mock Dispatcharr client to raise an exception
            mock_dispatcharr = MagicMock()
            mock_dispatcharr.get_stream_by_id.side_effect = Exception("API error")
            mock_dispatcharr_client_class.return_value = mock_dispatcharr

            conn = FakeConnection()
            conn.channel_source_provider = "dispatcharr"
            conn.channel_source_external_id = "123"
            
            result = authorize_live_playback(
                profile_id=1,
                device_key="test-device-key",
                channel_id=1,
                connection=conn,
                settings=self.settings
            )
            
            # Should fall back to OME/DVR on fetch failure
            self.assertIn("playback", result)
            self.assertEqual(result["playback"]["protocol"], "hls")
            self.assertTrue(result["playback"]["dvr_enabled"])

    def test_missing_entitlement_returns_403(self):
        """Test that missing channel entitlement returns 403."""
        conn = FakeConnection()
        conn.has_entitlement = False
        
        with self.assertRaises(Exception) as ctx:
            authorize_live_playback(
                profile_id=1,
                device_key="test-device-key",
                channel_id=1,
                connection=conn,
                settings=self.settings
            )
        
        self.assertIn("entitlement", str(ctx.exception).lower())

    def test_inactive_subscription_returns_402(self):
        """Test that inactive subscription returns 402."""
        conn = FakeConnection()
        conn.has_subscription = False
        
        with self.assertRaises(Exception) as ctx:
            authorize_live_playback(
                profile_id=1,
                device_key="test-device-key",
                channel_id=1,
                connection=conn,
                settings=self.settings
            )
        
        self.assertIn("subscription", str(ctx.exception).lower())


if __name__ == "__main__":
    unittest.main()
