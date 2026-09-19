import base64
import unittest
from unittest.mock import MagicMock, patch
from app.ome_client import OmeClient


class TestOmeClient(unittest.TestCase):
    """Test OvenMediaEngine client authentication and API calls."""

    def test_ome_client_basic_auth_encoding(self):
        """Test that OME client properly encodes token as HTTP Basic Auth."""
        client = OmeClient(
            api_url="http://localhost:8081",
            api_token="test-token",
            playback_base_url="http://localhost:8080/media"
        )
        headers = client._headers()
        
        # Verify Basic Auth format
        self.assertIn("Authorization", headers)
        self.assertTrue(headers["Authorization"].startswith("Basic "))
        
        # Verify base64 encoding
        encoded_token = headers["Authorization"].replace("Basic ", "")
        decoded = base64.b64decode(encoded_token).decode("utf-8")
        self.assertEqual(decoded, "test-token")

    def test_ome_client_no_token(self):
        """Test that OME client works without token if not configured."""
        client = OmeClient(
            api_url="http://localhost:8081",
            api_token=None,
            playback_base_url="http://localhost:8080/media"
        )
        headers = client._headers()
        
        # Should not include Authorization header when no token
        self.assertNotIn("Authorization", headers)

    def test_ome_client_build_playback_url_hls(self):
        """Test HLS playback URL construction."""
        client = OmeClient(
            api_url="http://localhost:8081",
            api_token="test-token",
            playback_base_url="http://localhost:8080/media"
        )
        url = client.build_playback_url("app", "channel_1", "signed_token", protocol="hls")
        self.assertEqual(url, "http://localhost:8080/media/app/channel_1/playlist.m3u8?token=signed_token")

    def test_ome_client_build_playback_url_webrtc(self):
        """Test WebRTC playback URL construction."""
        client = OmeClient(
            api_url="http://localhost:8081",
            api_token="test-token",
            playback_base_url="http://localhost:8080/media"
        )
        url = client.build_playback_url("app", "channel_1", "signed_token", protocol="webrtc")
        self.assertEqual(url, "http://localhost:8080/media/app/channel_1?token=signed_token")

    def test_ome_health_check_mocked(self):
        """Test OME health check with mocked httpx client."""
        with patch('httpx.Client') as mock_client_class:
            mock_response = MagicMock()
            mock_response.status_code = 200
            mock_client = MagicMock()
            mock_client.get.return_value = mock_response
            mock_client_class.return_value.__enter__.return_value = mock_client
            
            client = OmeClient(
                api_url="http://localhost:8081",
                api_token="test-token",
                playback_base_url="http://localhost:8080/media"
            )
            result = client.get_health()
            
            self.assertEqual(result["status"], "ok")
            self.assertEqual(result["code"], 200)
            mock_client.get.assert_called_once()

    def test_ome_health_check_401_mocked(self):
        """Test OME health check with 401 (endpoint exists but auth fails)."""
        with patch('httpx.Client') as mock_client_class:
            mock_response = MagicMock()
            mock_response.status_code = 401
            mock_client = MagicMock()
            mock_client.get.return_value = mock_response
            mock_client_class.return_value.__enter__.return_value = mock_client
            
            client = OmeClient(
                api_url="http://localhost:8081",
                api_token="test-token",
                playback_base_url="http://localhost:8080/media"
            )
            result = client.get_health()
            
            # 401 is considered ok - means endpoint is listening
            self.assertEqual(result["status"], "ok")
            self.assertEqual(result["code"], 401)

    def test_ome_health_check_unreachable_mocked(self):
        """Test OME health check when OME is unreachable."""
        with patch('httpx.Client') as mock_client_class:
            mock_client_class.return_value.__enter__.side_effect = Exception("Connection refused")
            
            client = OmeClient(
                api_url="http://localhost:8081",
                api_token="test-token",
                playback_base_url="http://localhost:8080/media"
            )
            result = client.get_health()
            
            self.assertEqual(result["status"], "unreachable")
            self.assertIn("error", result)


if __name__ == "__main__":
    unittest.main()
