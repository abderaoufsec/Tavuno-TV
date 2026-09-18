from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

import psycopg
from psycopg.rows import dict_row
from redis import Redis

from .config import Settings


class Services:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.redis = Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            password=settings.redis_password,
            decode_responses=True,
        )

    @contextmanager
    def connection(self) -> Iterator[psycopg.Connection[dict[str, Any]]]:
        with psycopg.connect(self.settings.postgres_dsn, row_factory=dict_row) as connection:
            yield connection

    def health(self) -> dict[str, str]:
        with psycopg.connect(self.settings.postgres_dsn) as connection:
            connection.execute("SELECT 1").fetchone()
        if not self.redis.ping():
            raise RuntimeError("Redis ping failed")
        return {"database": "ok", "cache": "ok"}
