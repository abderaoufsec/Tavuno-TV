import logging
from typing import Any
import httpx

logger = logging.getLogger("tavuno-control.dispatcharr")

SENSITIVE_FIELDS = {
    "url",
    "local_file",
    "stream_url",
    "direct_source",
    "custom_url",
    "username",
    "password",
    "server_url",
    "file",
}


def redact_record(record: Any) -> Any:
    """Drop connection credentials and raw stream locations from Dispatcharr payloads."""
    if isinstance(record, list):
        return [redact_record(item) for item in record]
    if not isinstance(record, dict):
        return record
    cleaned = {}
    for key, value in record.items():
        if key in SENSITIVE_FIELDS:
            continue
        cleaned[key] = redact_record(value)
    return cleaned


class DispatcharrClient:
    """Read-only client for Dispatcharr IPTV middleware (M4)."""

    def __init__(self, base_url: str, api_key: str | None = None, timeout: float = 20.0):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key.strip() if api_key else None
        self.timeout = timeout

    def _headers(self) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.api_key:
            headers["X-API-Key"] = self.api_key
        return headers

    def _get_json(self, path: str, params: dict[str, Any] | None = None, authenticated: bool = True) -> Any:
        url = f"{self.base_url}{path}"
        headers = self._headers() if authenticated else {"Accept": "application/json"}
        with httpx.Client(timeout=self.timeout, headers=headers) as client:
            response = client.get(url, params=params)
            response.raise_for_status()
            return response.json()

    def get_paginated(self, path: str, max_pages: int = 50, page_size: int = 100) -> list[dict[str, Any]]:
        items: list[dict[str, Any]] = []
        page = 1
        while page <= max_pages:
            data = self._get_json(path, params={"page": page, "page_size": page_size})
            if isinstance(data, list):
                return redact_record(data)
            results = data.get("results", [])
            items.extend(results)
            if not data.get("next"):
                break
            page += 1
        return redact_record(items)

    def get_version(self) -> dict[str, Any]:
        """Verify Dispatcharr core version and health (public probe)."""
        return self._get_json("/api/core/version/", authenticated=False)

    def get_channel_groups(self) -> list[dict[str, Any]]:
        data = self._get_json("/api/channels/groups/")
        groups = data if isinstance(data, list) else data.get("results", [])
        return redact_record(groups)

    def get_channels(self, page: int = 1, page_size: int = 100) -> dict[str, Any]:
        data = self._get_json("/api/channels/channels/", params={"page": page, "page_size": page_size})
        if isinstance(data, list):
            return {"count": len(data), "next": None, "results": redact_record(data)}
        if isinstance(data, dict) and "results" in data:
            data = dict(data)
            data["results"] = redact_record(data.get("results") or [])
        return data

    def get_all_channels(self, max_pages: int = 50) -> list[dict[str, Any]]:
        return self.get_paginated("/api/channels/channels/", max_pages=max_pages)

    def get_channel_streams(self, channel_id: int) -> list[dict[str, Any]]:
        data = self._get_json(f"/api/channels/channels/{channel_id}/streams/")
        if isinstance(data, list):
            streams = data
        elif isinstance(data, dict):
            streams = data.get("results", [])
        else:
            streams = []
        return redact_record(streams)

    def get_all_streams(self, max_pages: int = 50) -> list[dict[str, Any]]:
        return self.get_paginated("/api/channels/streams/", max_pages=max_pages)

    def get_epg_data(self) -> list[dict[str, Any]]:
        data = self._get_json("/api/epg/epgdata/")
        records = data if isinstance(data, list) else data.get("results", [])
        return redact_record(records)

    def get_epg_programs(self, tvg_id: str | None = None) -> list[dict[str, Any]]:
        params = {"tvg_id": tvg_id} if tvg_id else None
        data = self._get_json("/api/epg/programs/", params=params)
        records = data if isinstance(data, list) else data.get("results", [])
        return redact_record(records)

    def get_vod_categories(self) -> list[dict[str, Any]]:
        data = self._get_json("/api/vod/categories/")
        records = data if isinstance(data, list) else data.get("results", [])
        return redact_record(records)

    def get_all_movies(self, max_pages: int = 50) -> list[dict[str, Any]]:
        return self.get_paginated("/api/vod/movies/", max_pages=max_pages)

    def get_all_series(self, max_pages: int = 50) -> list[dict[str, Any]]:
        return self.get_paginated("/api/vod/series/", max_pages=max_pages)

    def get_all_episodes(self, max_pages: int = 50) -> list[dict[str, Any]]:
        return self.get_paginated("/api/vod/episodes/", max_pages=max_pages)

    def get_episode_streams(self, episode_id: int) -> list[dict[str, Any]]:
        """Get stream URLs for a specific episode from Dispatcharr."""
        try:
            data = self._get_json(f"/api/vod/episodes/{episode_id}/streams/")
            if isinstance(data, list):
                streams = data
            elif isinstance(data, dict):
                streams = data.get("results", [])
            else:
                streams = []
            return redact_record(streams)
        except Exception as exc:
            logger.warning("Could not fetch streams for episode %s: %s", episode_id, exc)
            return []

    def get_movie_streams(self, movie_id: int) -> list[dict[str, Any]]:
        """Get stream URLs for a specific movie from Dispatcharr."""
        try:
            data = self._get_json(f"/api/vod/movies/{movie_id}/streams/")
            if isinstance(data, list):
                streams = data
            elif isinstance(data, dict):
                streams = data.get("results", [])
            else:
                streams = []
            return redact_record(streams)
        except Exception as exc:
            logger.warning("Could not fetch streams for movie %s: %s", movie_id, exc)
            return []
