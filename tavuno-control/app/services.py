from collections.abc import Iterator
from contextlib import contextmanager
import json
from typing import Any

import psycopg
from psycopg.rows import dict_row
from redis import Redis

from .config import Settings
from .dispatcharr_client import DispatcharrClient
from .ome_client import OmeClient
from .sync_service import CATALOG_CACHE_PREFIX, SyncService

EPG_CACHE_PREFIX = "tavuno:epg:"
EPG_CACHE_TTL = 300  # 5 minutes


class Services:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.redis = Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password,
            decode_responses=True,
        )
        self.dispatcharr = DispatcharrClient(
            base_url=settings.dispatcharr_url,
            api_key=settings.dispatcharr_api_key,
            timeout=settings.dispatcharr_timeout_seconds,
        )
        self.ome = OmeClient(
            api_url=settings.ome_api_url,
            api_token=settings.ome_api_token,
            playback_base_url=settings.ome_playback_base_url,
        )
        self.sync = SyncService(
            self.dispatcharr,
            redis=self.redis,
            expected_version=settings.dispatcharr_expected_version,
        )

    @contextmanager
    def connection(self) -> Iterator[psycopg.Connection[dict[str, Any]]]:
        with psycopg.connect(self.settings.postgres_dsn, row_factory=dict_row) as connection:
            yield connection

    def cached_json(self, key: str, loader) -> Any:
        cache_key = f"{CATALOG_CACHE_PREFIX}{key}"
        try:
            raw = self.redis.get(cache_key)
            if raw:
                return json.loads(raw)
        except Exception:
            raw = None
        data = loader()
        try:
            ttl = max(self.settings.catalog_cache_seconds, 1)
            self.redis.setex(cache_key, ttl, json.dumps(data, default=str))
        except Exception:
            pass
        return data

    def cache_epg(self, channel_id: int, programmes: list[dict[str, Any]]) -> None:
        cache_key = f"{EPG_CACHE_PREFIX}channel:{channel_id}"
        try:
            self.redis.setex(cache_key, EPG_CACHE_TTL, json.dumps(programmes, default=str))
        except Exception:
            pass

    def get_cached_epg(self, channel_id: int) -> list[dict[str, Any]] | None:
        cache_key = f"{EPG_CACHE_PREFIX}channel:{channel_id}"
        try:
            raw = self.redis.get(cache_key)
            if raw:
                return json.loads(raw)
        except Exception:
            pass
        return None

    def invalidate_epg_cache(self, channel_id: int | None = None) -> None:
        try:
            if channel_id is not None:
                cache_key = f"{EPG_CACHE_PREFIX}channel:{channel_id}"
                self.redis.delete(cache_key)
            else:
                for key in self.redis.scan_iter(f"{EPG_CACHE_PREFIX}*"):
                    self.redis.delete(key)
        except Exception:
            pass

    def health(self) -> dict[str, str]:
        with psycopg.connect(self.settings.postgres_dsn) as connection:
            connection.execute("SELECT 1").fetchone()
        if not self.redis.ping():
            raise RuntimeError("Redis ping failed")

        status = {"database": "ok", "cache": "ok"}
        try:
            version_data = self.dispatcharr.get_version()
            status["dispatcharr"] = f"ok ({version_data.get('version', 'unknown')})"
        except Exception:
            status["dispatcharr"] = "unavailable"

        ome_health = self.ome.get_health()
        status["ome"] = ome_health.get("status", "unknown")
        return status
