"""Tests for playback authentication (Fix 4: S1-06, Fix 9: S2-04)."""

import os
import unittest
from unittest.mock import MagicMock

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.playback import authorize_live_playback, authorize_movie_playback, authorize_episode_playback


class FakeSettings:
    playback_token_secret = "test-secret-key"
    playback_token_ttl_seconds = 120
    ome_playback_base_url = "http://localhost:8080/media"


class FakeConnection:
    def __init__(self):
        self.query = ""
        self.parameters = ()
        self.commit_called = False
        self.fetchone_call_count = 0

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
            return {"id": 5, "max_concurrent_streams": 2, "max_devices": 3}
        if "FROM tavuno_channels" in self.query and "WHERE id = %s" in self.query:
            return {"id": 1, "name": "Test Channel", "slug": "test-channel"}
        if "FROM tavuno_channel_sources" in self.query:
            return {"provider": "ome", "external_id": "channel_1"}
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
        
        # Should raise 403 with device_not_registered
        self.assertIn("device_not_registered", str(ctx.exception).lower())

    def test_verify_playback_token_rejects_tampered_signature(self):
        """Test that verify_playback_token rejects tampered signatures (Fix 9)."""
        from app.playback import verify_playback_token
        from fastapi import HTTPException
        
        conn = FakeConnection()
        conn.fetchone.return_value = {
            "id": 101,
            "profile": 1,
            "device": 10,
            "content_type": "live",
            "content_key": "1",
            "status": "active",
            "expires_at": "2099-12-31"
        }
        
        # Create a token with wrong signature
        token = "101.9999999999.wrongsignature"
        
        with self.assertRaises(HTTPException) as ctx:
            verify_playback_token(token, conn, self.settings.playback_token_secret)
        
        self.assertEqual(ctx.exception.status_code, 401)
        self.assertIn("signature", str(ctx.exception.detail).lower())


if __name__ == "__main__":
    unittest.main()
