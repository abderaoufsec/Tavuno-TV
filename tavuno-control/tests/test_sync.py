import unittest
from unittest.mock import MagicMock

from app.sync_service import SyncService


class RecordingConnection:
    def __init__(self):
        self.statements: list[tuple[str, tuple]] = []
        self.query = ""
        self.parameters: tuple = ()
        self._existing_names = set()
        self._existing_slugs = set()
        self.series_by_slug: dict[str, int] = {}
        self._ids = {"category": 1, "channel": 1, "movie": 1, "series": 5, "season": 1, "episode": 1}
        self._rowcount = 0

    def execute(self, query, parameters=()):
        self.query = " ".join(query.split())
        self.parameters = parameters
        self.statements.append((self.query, parameters))
        if "INSERT INTO tavuno_series " in self.query:
            self.series_by_slug[parameters[1]] = self._ids["series"]
        self._rowcount = 0
        return self

    @property
    def rowcount(self):
        return self._rowcount

    def commit(self):
        pass

    def rollback(self):
        pass

    def fetchall(self):
        if "provider = 'dispatcharr'" in self.query:
            return [{"channel": 1, "external_id": "10"}]
        return []

    def fetchone(self):
        if self.query.startswith("INSERT"):
            if "tavuno_categories" in self.query:
                return {"id": self._ids["category"]}
            if "tavuno_channels" in self.query:
                return {"id": self._ids["channel"]}
            if "tavuno_series" in self.query:
                slug = self.parameters[1]
                self.series_by_slug[slug] = self._ids["series"]
                return {"id": self._ids["series"]}
            if "tavuno_seasons" in self.query:
                return {"id": self._ids["season"]}
            return {"id": 1}
        if "FROM tavuno_categories WHERE name" in self.query:
            name = self.parameters[0]
            if name in self._existing_names:
                return {"id": 1}
            self._existing_names.add(name)
            return None
        if "FROM tavuno_channels WHERE slug" in self.query:
            return None
        if "FROM tavuno_movies WHERE slug" in self.query:
            slug = self.parameters[0]
            if slug in self._existing_slugs:
                return {"id": 9}
            return None
        if "FROM tavuno_series WHERE slug" in self.query:
            slug = self.parameters[0]
            series_id = self.series_by_slug.get(slug)
            return {"id": series_id} if series_id is not None else None
        if "FROM tavuno_seasons" in self.query:
            return None
        if "FROM tavuno_episodes" in self.query:
            return None
        if "FROM tavuno_channel_sources" in self.query:
            return None
        if "FROM tavuno_epg_channels" in self.query:
            if "external_id" in self.query:
                return {"id": 3}
            return None
        return None


class FakeDispatcharr:
    def get_version(self):
        return {"version": "0.28.0"}

    def get_channel_groups(self):
        return [{"id": 1, "name": "News"}]

    def get_vod_categories(self):
        return [{"id": 8, "name": "Action", "category_type": "movie"}]

    def get_all_channels(self):
        return [{"id": 10, "name": "Tavuno News", "channel_group_id": 1, "tvg_id": "news.tv"}]

    def get_all_streams(self):
        return [{"id": 99, "name": "News A", "channel_id": 10, "url": "http://should-not-be-used"}]

    def get_channel_streams(self, channel_id: int):
        if channel_id == 10:
            return [{"id": 99, "name": "News A", "url": "http://stream.example.com/stream.ts"}]
        return []

    def get_epg_programs(self):
        return [
            {
                "tvg_id": "news.tv",
                "title": "Morning Bulletin",
                "start_time": "2026-09-19T06:00:00Z",
                "end_time": "2026-09-19T07:00:00Z",
                "description": "News",
            }
        ]

    def get_all_movies(self):
        return [{"id": 44, "name": "Neon Sky", "year": 2024, "category_id": 8, "description": "Sci-fi"}]

    def get_all_series(self):
        return [{"id": 5, "name": "Signal Loss", "category_id": 8, "description": "Drama"}]

    def get_all_episodes(self):
        return [
            {
                "id": 70,
                "name": "Pilot",
                "series_id": 5,
                "season_number": 1,
                "episode_number": 1,
                "description": "The first night",
            }
        ]


class SyncServiceTests(unittest.TestCase):
    def test_sync_all_imports_channels_streams_and_vod(self):
        connection = RecordingConnection()
        redis = MagicMock()
        service = SyncService(FakeDispatcharr(), redis=redis, expected_version="0.28.0")
        summary = service.sync_all(connection)

        self.assertEqual(summary["status"], "success")
        self.assertEqual(summary["channels_synced"], 1)
        self.assertEqual(summary["movies_synced"], 1)
        self.assertEqual(summary["series_synced"], 1)
        self.assertEqual(summary["episodes_synced"], 1)
        self.assertGreaterEqual(summary["stream_mappings_synced"], 1)

        inserted_tables = " ".join(query for query, _ in connection.statements)
        inserted_params = " ".join(str(params) for _, params in connection.statements)
        self.assertIn("INSERT INTO tavuno_channels", inserted_tables)
        self.assertIn("INSERT INTO tavuno_movies", inserted_tables)
        self.assertIn("INSERT INTO tavuno_series", inserted_tables)
        self.assertIn("INSERT INTO tavuno_episodes", inserted_tables)
        self.assertIn("dispatcharr-stream", inserted_params)
        redis.set.assert_called()
