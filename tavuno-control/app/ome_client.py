import base64
import hashlib
import hmac
import logging
import time
from typing import Any
import httpx

logger = logging.getLogger("tavuno-control.ome")


class OmeClient:
    """Client for OvenMediaEngine media server (M6)."""

    def __init__(
        self,
        api_url: str,
        api_token: str | None = None,
        playback_base_url: str = "http://localhost:8080/media",
        timeout: float = 5.0,
    ):
        self.api_url = api_url.rstrip("/")
        self.api_token = api_token
        self.playback_base_url = playback_base_url.rstrip("/")
        self.timeout = timeout

    def _headers(self) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.api_token:
            # OME requires HTTP Basic Authentication with base64-encoded token
            # Format: Authorization: Basic <base64_encode(token)>
            encoded_token = base64.b64encode(self.api_token.encode("utf-8")).decode("utf-8")
            headers["Authorization"] = f"Basic {encoded_token}"
        return headers

    def get_health(self) -> dict[str, Any]:
        """Probe OME API health."""
        url = f"{self.api_url}/v1/vhosts"
        try:
            with httpx.Client(timeout=self.timeout, headers=self._headers()) as client:
                response = client.get(url)
                if response.status_code in (200, 401):  # 401 confirms endpoint is listening
                    return {"status": "ok", "code": response.status_code}
                return {"status": "degraded", "code": response.status_code}
        except Exception as exc:
            logger.warning("OME health check failed: %s", exc)
            return {"status": "unreachable", "error": str(exc)}

    def build_playback_url(
        self,
        app_name: str,
        stream_name: str,
        token: str,
        protocol: str = "hls",
    ) -> str:
        """
        Build an authorized playback URL (HLS / LL-HLS) with short-lived signed token.
        Format: {base_url}/{app}/{stream_name}/playlist.m3u8?token={token}
        """
        if protocol == "webrtc":
            return f"{self.playback_base_url}/{app_name}/{stream_name}?token={token}"
        # Default is standard HLS / LL-HLS
        return f"{self.playback_base_url}/{app_name}/{stream_name}/playlist.m3u8?token={token}"
