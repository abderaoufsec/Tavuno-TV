"""Integration tests for VOD sync logic with realistic fixtures (Task 2: M12 VOD verification)."""

import unittest
from unittest.mock import MagicMock

from app.sync_service import SyncService


class RealisticDispatcharrClient:
    """Mock Dispatcharr client with realistic VOD data including edge cases."""
    
    def __init__(self):
        self._call_count = 0
        self._movie_overrides: dict[int, dict] = {}
    
    def get_version(self):
        return {"version": "0.28.0"}
    
    def get_channel_groups(self):
        return [
            {"id": 1, "name": "Live TV"},
            {"id": 2, "name": "Sports"}
        ]
    
    def get_vod_categories(self):
        return [
            {"id": 10, "name": "Action", "category_type": "movie"},
            {"id": 11, "name": "Drama", "category_type": "series"},
            {"id": 12, "name": "Comedy", "category_type": "movie"}
        ]
    
    def get_all_channels(self):
        return []
    
    def get_all_streams(self):
        return []
    
    def get_channel_streams(self, channel_id):
        return []
    
    def get_epg_programs(self):
        return []
    
    def get_all_movies(self):
        """Multiple movies including edge cases, with optional overrides."""
        base_movies = [
            # Normal movie with full metadata
            {
                "id": 100,
                "name": "The Matrix",
                "year": 1999,
                "category_id": 10,
                "description": "A computer hacker learns about the true nature of reality."
            },
            # Movie missing description
            {
                "id": 101,
                "name": "Action Movie",
                "year": 2020,
                "category_id": 10,
                # No description field
            },
            # Movie missing poster and other optional fields
            {
                "id": 102,
                "name": "Silent Film",
                "year": 1920,
                "category_id": 12,
                # Minimal metadata
            },
            # Movie with stream URL
            {
                "id": 103,
                "name": "Stream Test Movie",
                "year": 2024,
                "category_id": 10,
                "description": "Test movie with stream URL"
            }
        ]
        
        # Apply overrides
        result = []
        for movie in base_movies:
            movie_copy = dict(movie)
            if movie["id"] in self._movie_overrides:
                movie_copy.update(self._movie_overrides[movie["id"]])
            result.append(movie_copy)
        
        return result
    
    def get_all_series(self):
        """Series with multiple seasons, including edge case of empty season."""
        return [
            # Normal series with seasons
            {
                "id": 200,
                "name": "Breaking Bad",
                "category_id": 11,
                "description": "A chemistry teacher turned methamphetamine manufacturer."
            },
            # Series with missing description
            {
                "id": 201,
                "name": "No Description Series",
                "category_id": 11,
                # No description
            }
        ]
    
    def get_all_episodes(self):
        """Episodes for multiple seasons, including edge cases."""
        return [
            # Season 1 episodes
            {
                "id": 1000,
                "name": "Pilot",
                "series_id": 200,
                "season_number": 1,
                "episode_number": 1,
                "description": "The first episode"
            },
            {
                "id": 1001,
                "name": "Cat's in the Bag",
                "series_id": 200,
                "season_number": 1,
                "episode_number": 2,
                "description": "Walter and Jesse deal with their first batch."
            },
            {
                "id": 1002,
                "name": "No Description Episode",
                "series_id": 200,
                "season_number": 1,
                "episode_number": 3,
                # No description
            },
            # Season 2 episodes
            {
                "id": 2000,
                "name": "Seven Thirty-Seven",
                "series_id": 200,
                "season_number": 2,
                "episode_number": 1,
                "description": "Walter is diagnosed with cancer."
            },
            {
                "id": 2001,
                "name": "Four Days Out",
                "series_id": 200,
                "season_number": 2,
                "episode_number": 2,
                "description": "The partners search for new distribution."
            },
            # Episode from the series with no description
            {
                "id": 2100,
                "name": "Episode One",
                "series_id": 201,
                "season_number": 1,
                "episode_number": 1,
                # No description
            },
            # Episode missing optional fields
            {
                "id": 2101,
                "name": "Minimal Episode",
                "series_id": 201,
                "season_number": 1,
                "episode_number": 2,
                # Minimal metadata
            }
        ]
    
    def get_movie_streams(self, movie_id):
        """Mock stream URL fetching."""
        if movie_id == 103:
            return [
                {
                    "id": 5000,
                    "url": "http://example.com/movie/stream.m3u8",
                    "stream_url": "http://example.com/movie/stream.m3u8"
                }
            ]
        return []
    
    def get_episode_streams(self, episode_id):
        """Mock stream URL fetching."""
        if episode_id in [1000, 2000]:
            return [
                {
                    "id": 6000,
                    "url": "http://example.com/episode/stream.m3u8",
                    "stream_url": "http://example.com/episode/stream.m3u8"
                }
            ]
        return []


class RealisticRecordingConnection:
    """Fake connection that tracks SQL operations and verifies sync behavior."""
    
    def __init__(self):
        self.statements = []
        self.query = ""
        self.parameters = ()
        self._categories = {}  # name -> id
        self._movies = {}  # slug -> row dict
        self._movies_by_id = {}  # id -> slug (for UPDATE lookups)
        self._series = {}  # slug -> row dict
        self._series_by_id = {}  # id -> slug (for UPDATE lookups)
        self._seasons = {}  # (series_id, season_number) -> id
        self._episodes = {}  # (season_id, episode_number) -> row dict
        self._next_ids = {
            "category": 1,
            "movie": 1,
            "series": 1,
            "season": 1,
            "episode": 1
        }
        self._rowcount = 0
        self._last_insert_id = None  # Track last INSERT for RETURNING

    def execute(self, query, parameters=()):
        self.query = " ".join(query.split())
        self.parameters = parameters
        self.statements.append((self.query, parameters))
        self._last_insert_id = None  # Reset before each execute
        
        # Handle INSERT (with or without RETURNING)
        if "INSERT INTO" in self.query:
            if "tavuno_categories" in self.query:
                new_id = self._next_ids["category"]
                name = self.parameters[0]
                self._categories[name] = new_id
                self._next_ids["category"] += 1
                self._last_insert_id = new_id
                self._rowcount = 1
                return self
            
            if "tavuno_movies" in self.query:
                new_id = self._next_ids["movie"]
                slug = self.parameters[1]
                self._movies[slug] = {
                    "id": new_id,
                    "title": self.parameters[0],
                    "slug": slug,
                    "category": self.parameters[2],
                    "synopsis": self.parameters[3],
                    "release_year": self.parameters[4],
                    # A6: poster/backdrop sit between release_year and stream_url.
                    "poster": self.parameters[5],
                    "backdrop": self.parameters[6],
                    "stream_url": self.parameters[7],
                    "is_active": True
                }
                self._movies_by_id[new_id] = slug
                self._next_ids["movie"] += 1
                self._last_insert_id = new_id
                self._rowcount = 1
                return self

            if "tavuno_series" in self.query:
                new_id = self._next_ids["series"]
                slug = self.parameters[1]
                self._series[slug] = {
                    "id": new_id,
                    "title": self.parameters[0],
                    "slug": slug,
                    "category": self.parameters[2],
                    "synopsis": self.parameters[3],
                    # A6: poster/backdrop/release_year added to the column list.
                    "poster": self.parameters[4],
                    "backdrop": self.parameters[5],
                    "release_year": self.parameters[6],
                    "is_active": True
                }
                self._series_by_id[new_id] = slug
                self._next_ids["series"] += 1
                self._last_insert_id = new_id
                self._rowcount = 1
                return self
            
            if "tavuno_seasons" in self.query:
                new_id = self._next_ids["season"]
                series_id = self.parameters[0]
                season_number = self.parameters[1]
                self._seasons[(series_id, season_number)] = new_id
                self._next_ids["season"] += 1
                self._last_insert_id = new_id
                self._rowcount = 1
                return self
            
            if "tavuno_episodes" in self.query:
                new_id = self._next_ids["episode"]
                season_id = self.parameters[0]
                episode_number = self.parameters[1]
                synopsis = self.parameters[3]
                # Convert empty string to None for missing descriptions
                if synopsis == "":
                    synopsis = None
                self._episodes[(season_id, episode_number)] = {
                    "id": new_id,
                    "season": season_id,
                    "episode_number": episode_number,
                    "title": self.parameters[2],
                    "synopsis": synopsis,
                    # A6: thumbnail sits between synopsis and stream_url.
                    "thumbnail": self.parameters[4],
                    "stream_url": self.parameters[5],
                    "is_active": True
                }
                self._next_ids["episode"] += 1
                self._last_insert_id = new_id
                self._rowcount = 1
                return self
        
        # Handle UPDATE statements
        if "UPDATE tavuno_movies" in self.query:
            # Two patterns:
            # 1. UPDATE for upsert. A6 added poster/backdrop to the SET list, so the
            #    id moved from index 5 to 7:
            #    (title, category_id, synopsis, year, stream_url, poster, backdrop, id)
            # 2. UPDATE for deactivation: parameters: (id1, id2, id3, id4) - no field values
            if "is_active = FALSE" in self.query:
                # Deactivation UPDATE - skip for test purposes
                self._rowcount = 0
                return self
            else:
                # Upsert UPDATE
                movie_id = self.parameters[7]
                if movie_id in self._movies_by_id:
                    slug = self._movies_by_id[movie_id]
                    self._movies[slug]["title"] = self.parameters[0]
                    self._movies[slug]["category"] = self.parameters[1]
                    self._movies[slug]["synopsis"] = self.parameters[2]
                    self._movies[slug]["release_year"] = self.parameters[3]
                    self._movies[slug]["stream_url"] = self.parameters[4]
                    self._movies[slug]["poster"] = self.parameters[5]
                    self._movies[slug]["backdrop"] = self.parameters[6]
                self._rowcount = 0
                return self

        if "UPDATE tavuno_series" in self.query:
            if "is_active = FALSE" in self.query:
                self._rowcount = 0
                return self
            # A6 added poster/backdrop/release_year: id moved from 3 to 6.
            series_id = self.parameters[6]
            if series_id in self._series_by_id:
                slug = self._series_by_id[series_id]
                self._series[slug]["title"] = self.parameters[0]
                self._series[slug]["category"] = self.parameters[1]
                self._series[slug]["synopsis"] = self.parameters[2]
                self._series[slug]["poster"] = self.parameters[3]
                self._series[slug]["backdrop"] = self.parameters[4]
                self._series[slug]["release_year"] = self.parameters[5]
            self._rowcount = 0
            return self
        
        if "UPDATE tavuno_episodes" in self.query:
            if "is_active = FALSE" in self.query:
                self._rowcount = 0
                return self
            # A6 added thumbnail: id moved from 3 to 4.
            episode_id = self.parameters[4]
            # Find episode by id
            for key, ep in self._episodes.items():
                if ep["id"] == episode_id:
                    self._episodes[key]["title"] = self.parameters[0]
                    self._episodes[key]["synopsis"] = self.parameters[1]
                    self._episodes[key]["stream_url"] = self.parameters[2]
                    self._episodes[key]["thumbnail"] = self.parameters[3]
                    break
            self._rowcount = 0
            return self
        
        return self

    @property
    def rowcount(self):
        return self._rowcount

    def commit(self):
        pass

    def rollback(self):
        pass

    def fetchall(self):
        return []

    def fetchone(self):
        # Handle category lookups
        if "FROM tavuno_categories WHERE name" in self.query:
            name = self.parameters[0]
            if name in self._categories:
                return {"id": self._categories[name]}
            return None
        
        # Handle slug lookups
        if "FROM tavuno_movies WHERE slug" in self.query:
            slug = self.parameters[0]
            if slug in self._movies:
                return {"id": self._movies[slug]["id"]}
            return None
        
        if "FROM tavuno_series WHERE slug" in self.query:
            slug = self.parameters[0]
            if slug in self._series:
                return {"id": self._series[slug]["id"]}
            return None
        
        if "FROM tavuno_seasons WHERE series" in self.query:
            series_id = self.parameters[0]
            season_number = self.parameters[1]
            if (series_id, season_number) in self._seasons:
                return {"id": self._seasons[(series_id, season_number)]}
            return None
        
        if "FROM tavuno_episodes WHERE season" in self.query:
            season_id = self.parameters[0]
            episode_number = self.parameters[1]
            key = (season_id, episode_number)
            if key in self._episodes:
                return {"id": self._episodes[key]["id"]}
            return None
        
        # Handle UPDATE statements' WHERE clause lookups
        if "UPDATE tavuno_movies" in self.query and "WHERE id" in self.query:
            # A6: poster/backdrop added to the SET list, so the id is index 7 now.
            movie_id = self.parameters[7]
            if movie_id in self._movies_by_id:
                return {"id": movie_id}
            return None

        if "UPDATE tavuno_series" in self.query and "WHERE id" in self.query:
            # A6: poster/backdrop/release_year added, so the id is index 6 now.
            series_id = self.parameters[6]
            if series_id in self._series_by_id:
                return {"id": series_id}
            return None
        
        if "UPDATE tavuno_episodes" in self.query and "WHERE id" in self.query:
            episode_id = self.parameters[3]
            for key, ep in self._episodes.items():
                if ep["id"] == episode_id:
                    return {"id": episode_id}
            return None
        
        # Handle RETURNING clause
        if "RETURNING" in self.query or "returning" in self.query:
            if self._last_insert_id is not None:
                return {"id": self._last_insert_id}
        
        return None


class RealisticVODSyncTests(unittest.TestCase):
    """Test VOD sync logic with realistic fixtures and edge cases."""
    
    def test_vod_sync_creates_movies_series_with_correct_values(self):
        """Test that sync creates movies and series with correct field values."""
        connection = RealisticRecordingConnection()
        redis = MagicMock()
        
        # Pre-populate categories
        connection._categories = {"Action": 1, "Drama": 2, "Comedy": 3}
        
        client = RealisticDispatcharrClient()
        service = SyncService(client, redis=redis, expected_version="0.28.0")
        
        # Run sync
        movies_synced = service.sync_movies(connection)
        series_synced = service.sync_series(connection)
        
        # Assert counts
        self.assertEqual(movies_synced, 4)
        self.assertEqual(series_synced, 2)
        
        # Assert movies were created with correct values
        self.assertEqual(len(connection._movies), 4)
        
        # Check specific movie fields
        matrix = connection._movies["darr-movie-100"]
        self.assertEqual(matrix["title"], "The Matrix")
        self.assertEqual(matrix["release_year"], 1999)
        self.assertEqual(matrix["synopsis"], "A computer hacker learns about the true nature of reality.")
        
        # Check movie with missing description has NULL synopsis
        action_movie = connection._movies["darr-movie-101"]
        self.assertEqual(action_movie["title"], "Action Movie")
        self.assertIsNone(action_movie["synopsis"])
        
        # Assert series were created
        self.assertEqual(len(connection._series), 2)
        
        breaking_bad = connection._series["darr-series-200"]
        self.assertEqual(breaking_bad["title"], "Breaking Bad")
        self.assertEqual(breaking_bad["synopsis"], "A chemistry teacher turned methamphetamine manufacturer.")
    
    def test_vod_sync_creates_episodes_with_correct_values(self):
        """Test that sync creates episodes with correct values, including missing descriptions."""
        connection = RealisticRecordingConnection()
        redis = MagicMock()
        
        # Pre-populate categories
        connection._categories = {"Action": 1, "Drama": 2, "Comedy": 3}
        
        client = RealisticDispatcharrClient()
        service = SyncService(client, redis=redis, expected_version="0.28.0")
        
        # Sync series first (required dependency)
        service.sync_series(connection)
        
        # Sync episodes
        episodes_synced = service.sync_episodes(connection)
        
        # Assert 7 episodes were synchronized
        self.assertEqual(episodes_synced, 7)
        self.assertEqual(len(connection._episodes), 7)
        
        # Assert specific episode values
        # Season 1 Episode 1
        s1_id = connection._seasons[(1, 1)]
        pilot = connection._episodes[(s1_id, 1)]
        self.assertEqual(pilot["title"], "Pilot")
        self.assertEqual(pilot["synopsis"], "The first episode")
        
        # Season 2 Episode 1
        s2_id = connection._seasons[(1, 2)]
        seven_thirty_seven = connection._episodes[(s2_id, 1)]
        self.assertEqual(seven_thirty_seven["title"], "Seven Thirty-Seven")
        self.assertEqual(seven_thirty_seven["synopsis"], "Walter is diagnosed with cancer.")
        
        # Episode with no description should have None synopsis
        no_desc_ep = connection._episodes[(s1_id, 3)]
        self.assertEqual(no_desc_ep["title"], "No Description Episode")
        self.assertIsNone(no_desc_ep["synopsis"])
    
    def test_vod_sync_creates_multiple_seasons_correctly(self):
        """Test that sync creates multiple seasons correctly and associates episodes with the right season."""
        connection = RealisticRecordingConnection()
        redis = MagicMock()
        
        # Pre-populate categories
        connection._categories = {"Action": 1, "Drama": 2, "Comedy": 3}
        
        client = RealisticDispatcharrClient()
        service = SyncService(client, redis=redis, expected_version="0.28.0")
        
        # Sync series first
        service.sync_series(connection)
        
        # Sync episodes
        service.sync_episodes(connection)
        
        # Assert 3 seasons were created (2 for Breaking Bad, 1 for No Description Series)
        self.assertEqual(len(connection._seasons), 3)
        
        # Breaking Bad (series ID 1) should have 2 seasons
        self.assertIn((1, 1), connection._seasons)  # Season 1
        self.assertIn((1, 2), connection._seasons)  # Season 2
        
        # No Description Series (series ID 2) should have 1 season
        self.assertIn((2, 1), connection._seasons)  # Season 1
        
        # Assert episodes are associated with correct seasons
        s1_id = connection._seasons[(1, 1)]
        s2_id = connection._seasons[(1, 2)]
        
        # Season 1 should have 3 episodes
        s1_episodes = [ep for key, ep in connection._episodes.items() if key[0] == s1_id]
        self.assertEqual(len(s1_episodes), 3)
        
        # Season 2 should have 2 episodes
        s2_episodes = [ep for key, ep in connection._episodes.items() if key[0] == s2_id]
        self.assertEqual(len(s2_episodes), 2)
    
    def test_vod_sync_episodes_idempotent_on_rerun(self):
        """Test that re-running episode sync with identical data doesn't create duplicates."""
        connection = RealisticRecordingConnection()
        redis = MagicMock()
        
        # Pre-populate categories
        connection._categories = {"Action": 1, "Drama": 2, "Comedy": 3}
        
        client = RealisticDispatcharrClient()
        service = SyncService(client, redis=redis, expected_version="0.28.0")
        
        # Sync series first
        service.sync_series(connection)
        
        # First episode sync
        episodes_synced_1 = service.sync_episodes(connection)
        first_episode_count = len(connection._episodes)
        
        # Second episode sync with identical data
        episodes_synced_2 = service.sync_episodes(connection)
        
        # Assert no new episodes were created
        self.assertEqual(len(connection._episodes), first_episode_count)
        
        # Assert sync count is 0 for second run (all upserts)
        self.assertEqual(episodes_synced_2, 0)
    
    def test_vod_sync_is_idempotent_on_rerun(self):
        """Test that re-running sync with identical data doesn't create duplicates (upsert behavior)."""
        connection = RealisticRecordingConnection()
        redis = MagicMock()
        
        # Pre-populate categories
        connection._categories = {"Action": 1, "Drama": 2, "Comedy": 3}
        
        client = RealisticDispatcharrClient()
        service = SyncService(client, redis=redis, expected_version="0.28.0")
        
        # First sync
        movies_synced_1 = service.sync_movies(connection)
        series_synced_1 = service.sync_series(connection)
        
        first_movie_count = len(connection._movies)
        first_series_count = len(connection._series)
        
        # Second sync with identical data
        movies_synced_2 = service.sync_movies(connection)
        series_synced_2 = service.sync_series(connection)
        
        # Assert no new records were created (idempotent)
        self.assertEqual(len(connection._movies), first_movie_count)
        self.assertEqual(len(connection._series), first_series_count)
        
        # Assert sync count is 0 for second run (all upserts)
        self.assertEqual(movies_synced_2, 0)
        self.assertEqual(series_synced_2, 0)
    
    def test_vod_sync_updates_changed_data_on_rerun(self):
        """Test that re-running sync with changed data updates existing rows instead of creating duplicates."""
        connection = RealisticRecordingConnection()
        redis = MagicMock()
        
        # Pre-populate categories
        connection._categories = {"Action": 1, "Drama": 2, "Comedy": 3}
        
        client = RealisticDispatcharrClient()
        service = SyncService(client, redis=redis, expected_version="0.28.0")
        
        # First sync
        service.sync_movies(connection)
        
        # Record the current synopsis for movie 100
        original_synopsis = connection._movies["darr-movie-100"]["synopsis"]
        self.assertEqual(original_synopsis, "A computer hacker learns about the true nature of reality.")
        
        # Set override to change the description
        client._movie_overrides[100] = {
            "description": "An updated synopsis after a metadata refresh."
        }
        
        # Second sync with changed data
        service.sync_movies(connection)
        
        # Assert no new movie was created (update, not insert)
        self.assertEqual(len(connection._movies), 4)
        
        # Assert the synopsis was updated to the new value
        updated_synopsis = connection._movies["darr-movie-100"]["synopsis"]
        self.assertEqual(updated_synopsis, "An updated synopsis after a metadata refresh.")
    
    def test_vod_sync_handles_null_optional_fields(self):
        """Test that sync code handles NULL optional fields gracefully."""
        connection = RealisticRecordingConnection()
        redis = MagicMock()
        
        # Pre-populate categories
        connection._categories = {"Action": 1, "Drama": 2, "Comedy": 3}
        
        client = RealisticDispatcharrClient()
        service = SyncService(client, redis=redis, expected_version="0.28.0")
        
        # Sync should succeed despite missing metadata
        movies_synced = service.sync_movies(connection)
        series_synced = service.sync_series(connection)
        
        self.assertEqual(movies_synced, 4)
        self.assertEqual(series_synced, 2)
        
        # Verify NULL fields are persisted as None
        action_movie = connection._movies["darr-movie-101"]
        self.assertIsNone(action_movie["synopsis"])
        
        no_desc_series = connection._series["darr-series-201"]
        self.assertIsNone(no_desc_series["synopsis"])


if __name__ == "__main__":
    unittest.main()
