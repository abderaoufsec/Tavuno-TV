import logging
import re
from typing import Any

from .dispatcharr_client import DispatcharrClient

logger = logging.getLogger("tavuno-control.sync")

LAST_SYNC_KEY = "tavuno:dispatcharr:last_sync"
CATALOG_CACHE_PREFIX = "tavuno:catalog:"


def slugify(text: str, fallback: str = "item") -> str:
    text = re.sub(r"[^\w\s-]", "", text).strip().lower()
    return re.sub(r"[-\s]+", "-", text) or fallback


def _text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _stream_dispatcharr_channel_ids(stream: dict[str, Any]) -> list[int]:
    ids: list[int] = []
    for key in ("channel", "channel_id"):
        parsed = _int(stream.get(key)) if not isinstance(stream.get(key), dict) else _int(stream.get(key, {}).get("id"))
        if parsed is not None:
            ids.append(parsed)
    channels = stream.get("channels") or []
    if isinstance(channels, list):
        for item in channels:
            parsed = _int(item if not isinstance(item, dict) else item.get("id"))
            if parsed is not None:
                ids.append(parsed)
    return list(dict.fromkeys(ids))


class SyncService:
    """Idempotent metadata sync from Dispatcharr into Tavuno-owned tables (M4)."""

    def __init__(
        self,
        client: DispatcharrClient,
        redis: Any | None = None,
        expected_version: str = "0.27.2",
    ):
        self.client = client
        self.redis = redis
        self.expected_version = expected_version

    def sync_all(self, connection: Any) -> dict[str, Any]:
        version_data = self.client.get_version()
        version = str(version_data.get("version") or "unknown")
        if version != self.expected_version:
            logger.warning(
                "Dispatcharr version %s does not match expected contract %s",
                version,
                self.expected_version,
            )

        summary = {
            "status": "success",
            "dispatcharr_version": version,
            "expected_version": self.expected_version,
            "version_match": version == self.expected_version,
            "categories_synced": self.sync_live_categories(connection),
            "vod_categories_synced": self.sync_vod_categories(connection),
            "channels_synced": self.sync_channels(connection),
            "stream_mappings_synced": self.sync_stream_mappings(connection),
            "epg_programmes_synced": self.sync_epg(connection),
            "movies_synced": self.sync_movies(connection),
            "series_synced": self.sync_series(connection),
            "episodes_synced": self.sync_episodes(connection),
        }
        connection.commit()
        self._store_last_sync(summary)
        self._invalidate_catalog_cache()
        return summary

    def _store_last_sync(self, summary: dict[str, Any]) -> None:
        if self.redis is None:
            return
        try:
            import json

            self.redis.set(LAST_SYNC_KEY, json.dumps(summary, default=str))
        except Exception:
            logger.warning("Could not persist last Dispatcharr sync status in Redis")

    def _invalidate_catalog_cache(self) -> None:
        if self.redis is None:
            return
        try:
            for key in self.redis.scan_iter(f"{CATALOG_CACHE_PREFIX}*"):
                self.redis.delete(key)
        except Exception:
            logger.warning("Could not invalidate catalog cache after Dispatcharr sync")

    def last_sync(self) -> dict[str, Any] | None:
        if self.redis is None:
            return None
        try:
            import json

            raw = self.redis.get(LAST_SYNC_KEY)
            return json.loads(raw) if raw else None
        except Exception:
            return None

    def _upsert_category(self, connection: Any, name: str, kind: str) -> int:
        existing = connection.execute(
            "SELECT id FROM tavuno_categories WHERE name = %s",
            (name,),
        ).fetchone()
        if existing:
            connection.execute(
                "UPDATE tavuno_categories SET kind = %s, is_active = TRUE WHERE id = %s",
                (kind, existing["id"]),
            )
            return existing["id"]
        row = connection.execute(
            """
            INSERT INTO tavuno_categories (name, kind, is_active, sort_order)
            VALUES (%s, %s, TRUE, 0)
            RETURNING id
            """,
            (name, kind),
        ).fetchone()
        return row["id"]

    def sync_live_categories(self, connection: Any) -> int:
        try:
            groups = self.client.get_channel_groups()
        except Exception as exc:
            logger.warning("Could not fetch channel groups from Dispatcharr: %s", exc)
            return 0

        synced = 0
        for group in groups:
            name = _text(group.get("name"))
            if not name:
                continue
            before = connection.execute("SELECT id FROM tavuno_categories WHERE name = %s", (name,)).fetchone()
            self._upsert_category(connection, name, "live")
            if before is None:
                synced += 1
        return synced

    def sync_vod_categories(self, connection: Any) -> int:
        try:
            categories = self.client.get_vod_categories()
        except Exception as exc:
            logger.warning("Could not fetch VOD categories from Dispatcharr: %s", exc)
            return 0

        synced = 0
        for category in categories:
            name = _text(category.get("name"))
            if not name:
                continue
            category_type = str(category.get("category_type") or category.get("type") or "movie").lower()
            kind = "series" if "series" in category_type else "movie"
            before = connection.execute("SELECT id FROM tavuno_categories WHERE name = %s", (name,)).fetchone()
            self._upsert_category(connection, name, kind)
            if before is None:
                synced += 1
        return synced

    def _category_id_by_dispatcharr_group(self, connection: Any) -> dict[int, int]:
        mapping: dict[int, int] = {}
        try:
            groups = self.client.get_channel_groups()
        except Exception:
            return mapping
        for group in groups:
            group_id = _int(group.get("id"))
            name = _text(group.get("name"))
            if group_id is None or not name:
                continue
            row = connection.execute("SELECT id FROM tavuno_categories WHERE name = %s", (name,)).fetchone()
            if row:
                mapping[group_id] = row["id"]
        return mapping

    def _category_id_by_vod_category(self, connection: Any) -> dict[int, int]:
        mapping: dict[int, int] = {}
        try:
            categories = self.client.get_vod_categories()
        except Exception:
            return mapping
        for category in categories:
            category_id = _int(category.get("id"))
            name = _text(category.get("name"))
            if category_id is None or not name:
                continue
            row = connection.execute("SELECT id FROM tavuno_categories WHERE name = %s", (name,)).fetchone()
            if row:
                mapping[category_id] = row["id"]
        return mapping

    def _ensure_source(self, connection: Any, channel_id: int, provider: str, external_id: str, priority: int) -> bool:
        existing = connection.execute(
            """
            SELECT id FROM tavuno_channel_sources
            WHERE channel = %s AND provider = %s AND external_id = %s
            """,
            (channel_id, provider, external_id),
        ).fetchone()
        if existing:
            connection.execute(
                "UPDATE tavuno_channel_sources SET is_active = TRUE, priority = %s WHERE id = %s",
                (priority, existing["id"]),
            )
            return False
        connection.execute(
            """
            INSERT INTO tavuno_channel_sources (channel, provider, external_id, priority, is_active)
            VALUES (%s, %s, %s, %s, TRUE)
            """,
            (channel_id, provider, external_id, priority),
        )
        return True

    def sync_channels(self, connection: Any) -> int:
        try:
            channels_data = self.client.get_all_channels()
        except Exception as exc:
            logger.warning("Could not fetch channels from Dispatcharr: %s", exc)
            return 0

        group_map = self._category_id_by_dispatcharr_group(connection)
        synced = 0
        for channel in channels_data:
            name = _text(channel.get("name"))
            dispatcharr_id = _int(channel.get("id"))
            if not name or dispatcharr_id is None:
                continue

            slug = slugify(name, fallback=f"channel-{dispatcharr_id}")
            existing_channel = connection.execute(
                "SELECT id FROM tavuno_channels WHERE slug = %s OR name = %s",
                (slug, name),
            ).fetchone()
            category_id = group_map.get(_int(channel.get("channel_group_id")) or _int(channel.get("channel_group")))

            if existing_channel:
                channel_id = existing_channel["id"]
                connection.execute(
                    "UPDATE tavuno_channels SET name = %s, category = COALESCE(%s, category), is_active = TRUE WHERE id = %s",
                    (name, category_id, channel_id),
                )
            else:
                row = connection.execute(
                    """
                    INSERT INTO tavuno_channels (name, slug, category, is_active)
                    VALUES (%s, %s, %s, TRUE)
                    RETURNING id
                    """,
                    (name, slug, category_id),
                ).fetchone()
                channel_id = row["id"]
                synced += 1

            self._ensure_source(connection, channel_id, "dispatcharr", str(dispatcharr_id), 1)

            tvg_id = _text(channel.get("tvg_id"))
            if tvg_id:
                existing_epg = connection.execute(
                    "SELECT id FROM tavuno_epg_channels WHERE channel = %s",
                    (channel_id,),
                ).fetchone()
                if existing_epg:
                    connection.execute(
                        "UPDATE tavuno_epg_channels SET external_id = %s WHERE id = %s",
                        (tvg_id, existing_epg["id"]),
                    )
                else:
                    connection.execute(
                        "INSERT INTO tavuno_epg_channels (channel, external_id) VALUES (%s, %s)",
                        (channel_id, tvg_id),
                    )
        return synced

    def _tavuno_channel_id_by_dispatcharr_id(self, connection: Any) -> dict[str, int]:
        rows = connection.execute(
            """
            SELECT channel, external_id
            FROM tavuno_channel_sources
            WHERE provider = 'dispatcharr' AND is_active = TRUE
            """
        ).fetchall()
        return {str(row["external_id"]): row["channel"] for row in rows}

    def sync_stream_mappings(self, connection: Any) -> int:
        channel_map = self._tavuno_channel_id_by_dispatcharr_id(connection)
        synced = 0
        streams: list[dict[str, Any]] = []
        try:
            streams = self.client.get_all_streams()
        except Exception as exc:
            logger.warning("Could not fetch bulk streams from Dispatcharr: %s", exc)

        if not streams:
            for dispatcharr_id, tavuno_id in channel_map.items():
                try:
                    streams.extend(
                        [{**stream, "channel_id": int(dispatcharr_id)} for stream in self.client.get_channel_streams(int(dispatcharr_id))]
                    )
                except Exception as exc:
                    logger.warning("Could not fetch streams for Dispatcharr channel %s: %s", dispatcharr_id, exc)

        for stream in streams:
            stream_id = _int(stream.get("id"))
            if stream_id is None:
                continue
            for dispatcharr_channel_id in _stream_dispatcharr_channel_ids(stream):
                tavuno_channel_id = channel_map.get(str(dispatcharr_channel_id))
                if tavuno_channel_id is None:
                    continue
                if self._ensure_source(connection, tavuno_channel_id, "dispatcharr-stream", str(stream_id), 10):
                    synced += 1
        return synced

    def sync_epg(self, connection: Any) -> int:
        try:
            programs = self.client.get_epg_programs()
        except Exception as exc:
            logger.warning("Could not fetch EPG programmes from Dispatcharr: %s", exc)
            return 0

        synced = 0
        for program in programs:
            tvg_id = _text(program.get("tvg_id"))
            title = _text(program.get("title"))
            starts_at = program.get("start_time") or program.get("starts_at")
            ends_at = program.get("end_time") or program.get("ends_at")
            description = program.get("description") or program.get("sub_title") or ""
            if not tvg_id or not title or not starts_at or not ends_at:
                continue

            epg_channel = connection.execute(
                "SELECT id FROM tavuno_epg_channels WHERE external_id = %s",
                (tvg_id,),
            ).fetchone()
            if not epg_channel:
                continue

            existing = connection.execute(
                """
                SELECT id FROM tavuno_epg_programmes
                WHERE epg_channel = %s AND starts_at = %s
                """,
                (epg_channel["id"], starts_at),
            ).fetchone()
            if existing:
                connection.execute(
                    """
                    UPDATE tavuno_epg_programmes
                    SET title = %s, ends_at = %s, description = %s
                    WHERE id = %s
                    """,
                    (title, ends_at, description, existing["id"]),
                )
                continue
            connection.execute(
                """
                INSERT INTO tavuno_epg_programmes (epg_channel, title, starts_at, ends_at, description)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (epg_channel["id"], title, starts_at, ends_at, description),
            )
            synced += 1
        return synced

    def _vod_category_id(self, item: dict[str, Any], category_map: dict[int, int]) -> int | None:
        raw = item.get("category") if not isinstance(item.get("category"), dict) else item.get("category", {}).get("id")
        if raw is None:
            raw = item.get("category_id")
        parsed = _int(raw)
        if parsed is None:
            return None
        return category_map.get(parsed)

    def sync_movies(self, connection: Any) -> int:
        try:
            movies = self.client.get_all_movies()
        except Exception as exc:
            logger.warning("Could not fetch movies from Dispatcharr: %s", exc)
            return 0

        category_map = self._category_id_by_vod_category(connection)
        synced = 0
        for movie in movies:
            movie_id = _int(movie.get("id"))
            title = _text(movie.get("name") or movie.get("title"))
            if movie_id is None or not title:
                continue
            slug = f"darr-movie-{movie_id}"
            synopsis = movie.get("description") or movie.get("plot") or movie.get("synopsis")
            year = _int(movie.get("year") or movie.get("release_year"))
            category_id = self._vod_category_id(movie, category_map)
            existing = connection.execute("SELECT id FROM tavuno_movies WHERE slug = %s", (slug,)).fetchone()
            if existing:
                connection.execute(
                    """
                    UPDATE tavuno_movies
                    SET title = %s, category = COALESCE(%s, category), synopsis = %s, release_year = %s, is_active = TRUE
                    WHERE id = %s
                    """,
                    (title, category_id, synopsis, year, existing["id"]),
                )
                continue
            connection.execute(
                """
                INSERT INTO tavuno_movies (title, slug, category, synopsis, release_year, is_active)
                VALUES (%s, %s, %s, %s, %s, TRUE)
                """,
                (title, slug, category_id, synopsis, year),
            )
            synced += 1
        return synced

    def sync_series(self, connection: Any) -> int:
        try:
            series_items = self.client.get_all_series()
        except Exception as exc:
            logger.warning("Could not fetch series from Dispatcharr: %s", exc)
            return 0

        category_map = self._category_id_by_vod_category(connection)
        synced = 0
        for series in series_items:
            series_id = _int(series.get("id"))
            title = _text(series.get("name") or series.get("title"))
            if series_id is None or not title:
                continue
            slug = f"darr-series-{series_id}"
            synopsis = series.get("description") or series.get("plot") or series.get("synopsis")
            category_id = self._vod_category_id(series, category_map)
            existing = connection.execute("SELECT id FROM tavuno_series WHERE slug = %s", (slug,)).fetchone()
            if existing:
                connection.execute(
                    """
                    UPDATE tavuno_series
                    SET title = %s, category = COALESCE(%s, category), synopsis = %s, is_active = TRUE
                    WHERE id = %s
                    """,
                    (title, category_id, synopsis, existing["id"]),
                )
                continue
            connection.execute(
                """
                INSERT INTO tavuno_series (title, slug, category, synopsis, is_active)
                VALUES (%s, %s, %s, %s, TRUE)
                """,
                (title, slug, category_id, synopsis),
            )
            synced += 1
        return synced

    def _ensure_season(self, connection: Any, series_id: int, season_number: int) -> int:
        existing = connection.execute(
            "SELECT id FROM tavuno_seasons WHERE series = %s AND season_number = %s",
            (series_id, season_number),
        ).fetchone()
        if existing:
            return existing["id"]
        row = connection.execute(
            """
            INSERT INTO tavuno_seasons (series, season_number, title)
            VALUES (%s, %s, %s)
            RETURNING id
            """,
            (series_id, season_number, f"Season {season_number}"),
        ).fetchone()
        return row["id"]

    def sync_episodes(self, connection: Any) -> int:
        try:
            episodes = self.client.get_all_episodes()
        except Exception as exc:
            logger.warning("Could not fetch episodes from Dispatcharr: %s", exc)
            return 0

        synced = 0
        for episode in episodes:
            title = _text(episode.get("name") or episode.get("title")) or "Episode"
            season_number = _int(episode.get("season_number")) or 1
            episode_number = _int(episode.get("episode_number")) or 1
            series_ref = episode.get("series")
            dispatcharr_series_id = _int(series_ref if not isinstance(series_ref, dict) else series_ref.get("id"))
            if dispatcharr_series_id is None:
                dispatcharr_series_id = _int(episode.get("series_id"))
            if dispatcharr_series_id is None:
                continue
            series_row = connection.execute(
                "SELECT id FROM tavuno_series WHERE slug = %s",
                (f"darr-series-{dispatcharr_series_id}",),
            ).fetchone()
            if not series_row:
                continue
            season_id = self._ensure_season(connection, series_row["id"], season_number)
            existing = connection.execute(
                "SELECT id FROM tavuno_episodes WHERE season = %s AND episode_number = %s",
                (season_id, episode_number),
            ).fetchone()
            synopsis = episode.get("description") or episode.get("plot") or ""
            if existing:
                connection.execute(
                    """
                    UPDATE tavuno_episodes
                    SET title = %s, synopsis = %s, is_active = TRUE
                    WHERE id = %s
                    """,
                    (title, synopsis, existing["id"]),
                )
                continue
            connection.execute(
                """
                INSERT INTO tavuno_episodes (season, episode_number, title, synopsis, is_active)
                VALUES (%s, %s, %s, %s, TRUE)
                """,
                (season_id, episode_number, title, synopsis),
            )
            synced += 1
        return synced
