"""Catalog service layer (M11.2).

This service provides read access to catalog data (channels, categories, movies, series)
using the existing database access pattern. It maps database columns to the normalized
Pydantic models defined in models.py.

The service:
- Uses psycopg with dict_row (no ORM)
- Follows existing Services.connection() pattern
- Maps database column names to model field names
- Does not implement pagination yet
- Does not integrate with Dispatcharr (future milestone)
"""

from typing import Optional, List, Dict, Any
from contextlib import contextmanager

from .models import (
    Channel, Category, Movie, Series,
    ChannelDetails, MovieDetails, SeriesDetails, SeasonDetails,
    Competition, Team, Match, MatchDetails
)


class CatalogService:
    """Read-only catalog service for channels, categories, movies, and series."""

    def __init__(self, services):
        """Initialize catalog service with Services instance.
        
        Args:
            services: Services instance from app.services
        """
        self.services = services

    @contextmanager
    def _db(self):
        """Database connection context manager."""
        with self.services.connection() as connection:
            yield connection

    def _map_channel(self, row: dict) -> Channel:
        """Map database row to Channel model.
        
        Database columns: id, name, slug, category, logo, is_active
        Model fields: id, name, slug, category_id, logo, is_active
        """
        return Channel(
            id=row["id"],
            name=row["name"],
            slug=row["slug"],
            category_id=row.get("category"),  # Map 'category' to 'category_id'
            logo=str(row["logo"]) if row.get("logo") else None,  # UUID to string
            is_active=row["is_active"],
        )

    def _map_category(self, row: dict) -> Category:
        """Map database row to Category model.
        
        Database columns: id, name, kind, parent, sort_order, is_active
        Model fields: id, name, kind, parent_id, sort_order, is_active
        """
        return Category(
            id=row["id"],
            name=row["name"],
            kind=row["kind"],
            parent_id=row.get("parent"),  # Map 'parent' to 'parent_id'
            sort_order=row["sort_order"],
            is_active=row["is_active"],
        )

    def _map_movie(self, row: dict) -> Movie:
        """Map database row to Movie model.
        
        Database columns: id, title, slug, category, synopsis, release_year, is_active
        Model fields: id, title, slug, category_id, synopsis, release_year, is_active
        """
        return Movie(
            id=row["id"],
            title=row["title"],
            slug=row["slug"],
            category_id=row.get("category"),  # Map 'category' to 'category_id'
            synopsis=row.get("synopsis"),
            release_year=row.get("release_year"),
            is_active=row["is_active"],
        )

    def _map_series(self, row: dict) -> Series:
        """Map database row to Series model.

        Database columns: id, title, slug, category, synopsis, is_active
        Model fields: id, title, slug, category_id, synopsis, is_active
        """
        return Series(
            id=row["id"],
            title=row["title"],
            slug=row["slug"],
            category_id=row.get("category"),  # Map 'category' to 'category_id'
            synopsis=row.get("synopsis"),
            is_active=row["is_active"],
        )

    # M11 Sports mappers

    def _map_competition(self, row: dict) -> Competition:
        """Map database row to Competition model (M11).

        Database columns: id, name, slug, sport, category, external_id, is_active
        Model fields: id, name, slug, sport, category_id, external_id, is_active
        """
        return Competition(
            id=row["id"],
            name=row["name"],
            slug=row["slug"],
            sport=row["sport"],
            category_id=row.get("category"),
            external_id=row.get("external_id"),
            is_active=row["is_active"],
        )

    def _map_team(self, row: dict) -> Team:
        """Map database row to Team model (M11).

        Database columns: id, name, slug, competition, logo, external_id, is_active
        Model fields: id, name, slug, competition_id, logo, external_id, is_active
        """
        return Team(
            id=row["id"],
            name=row["name"],
            slug=row["slug"],
            competition_id=row.get("competition"),
            logo=str(row["logo"]) if row.get("logo") else None,
            external_id=row.get("external_id"),
            is_active=row["is_active"],
        )

    def _map_match(self, row: dict) -> Match:
        """Map database row to Match model (M11).

        Database columns: id, competition, home_team, away_team, channel, kickoff, status, home_score, away_score, external_id, is_active
        Model fields: id, competition_id, home_team_id, away_team_id, channel_id, kickoff, status, home_score, away_score, external_id, is_active
        """
        return Match(
            id=row["id"],
            competition_id=row["competition"],
            home_team_id=row["home_team"],
            away_team_id=row["away_team"],
            channel_id=row.get("channel"),
            kickoff=row["kickoff"].isoformat() if row.get("kickoff") else None,
            status=row["status"],
            home_score=row.get("home_score"),
            away_score=row.get("away_score"),
            external_id=row.get("external_id"),
            is_active=row["is_active"],
        )

    def _map_match_details(self, row: dict) -> MatchDetails:
        """Map database row to MatchDetails model (M11).

        Includes joined data from competitions, teams, and channels.
        """
        return MatchDetails(
            id=row["id"],
            competition_id=row["competition"],
            competition_name=row.get("competition_name"),
            home_team_id=row["home_team"],
            home_team_name=row["home_team_name"],
            home_team_logo=str(row["home_team_logo"]) if row.get("home_team_logo") else None,
            away_team_id=row["away_team"],
            away_team_name=row["away_team_name"],
            away_team_logo=str(row["away_team_logo"]) if row.get("away_team_logo") else None,
            channel_id=row.get("channel"),
            channel_name=row.get("channel_name"),
            kickoff=row["kickoff"].isoformat() if row.get("kickoff") else None,
            status=row["status"],
            home_score=row.get("home_score"),
            away_score=row.get("away_score"),
            is_active=row["is_active"],
        )

    def get_channels(self, category_id: Optional[int] = None) -> List[Channel]:
        """Get all active channels, optionally filtered by category.
        
        Args:
            category_id: Optional category ID to filter channels
            
        Returns:
            List of Channel models
        """
        query = "SELECT id, name, slug, category, logo, is_active FROM tavuno_channels WHERE is_active = TRUE"
        parameters: tuple = ()
        
        if category_id is not None:
            query += " AND category = %s"
            parameters = (category_id,)
        
        query += " ORDER BY name"
        
        with self._db() as conn:
            rows = conn.execute(query, parameters).fetchall()
        
        return [self._map_channel(row) for row in rows]

    def get_channel(self, channel_id: int) -> Optional[Channel]:
        """Get a single channel by ID.
        
        Args:
            channel_id: Channel ID
            
        Returns:
            Channel model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                "SELECT id, name, slug, category, logo, is_active FROM tavuno_channels WHERE id = %s AND is_active = TRUE",
                (channel_id,),
            ).fetchone()
        
        if row is None:
            return None
        
        return self._map_channel(row)

    def get_categories(self, kind: Optional[str] = None) -> List[Category]:
        """Get all active categories, optionally filtered by kind.
        
        Args:
            kind: Optional kind filter (e.g., "live", "movie", "series", "sports")
            
        Returns:
            List of Category models
        """
        query = "SELECT id, name, kind, parent, sort_order, is_active FROM tavuno_categories WHERE is_active = TRUE"
        parameters: tuple = ()
        
        if kind is not None:
            query += " AND kind = %s"
            parameters = (kind,)
        
        query += " ORDER BY sort_order, name"
        
        with self._db() as conn:
            rows = conn.execute(query, parameters).fetchall()
        
        return [self._map_category(row) for row in rows]

    def get_category(self, category_id: int) -> Optional[Category]:
        """Get a single category by ID.
        
        Args:
            category_id: Category ID
            
        Returns:
            Category model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                "SELECT id, name, kind, parent, sort_order, is_active FROM tavuno_categories WHERE id = %s AND is_active = TRUE",
                (category_id,),
            ).fetchone()
        
        if row is None:
            return None
        
        return self._map_category(row)

    def get_movies(self, category_id: Optional[int] = None) -> List[Movie]:
        """Get all active movies, optionally filtered by category.
        
        Args:
            category_id: Optional category ID to filter movies
            
        Returns:
            List of Movie models
        """
        query = "SELECT id, title, slug, category, synopsis, release_year, is_active FROM tavuno_movies WHERE is_active = TRUE"
        parameters: tuple = ()
        
        if category_id is not None:
            query += " AND category = %s"
            parameters = (category_id,)
        
        query += " ORDER BY title"
        
        with self._db() as conn:
            rows = conn.execute(query, parameters).fetchall()
        
        return [self._map_movie(row) for row in rows]

    def get_movie(self, movie_id: int) -> Optional[Movie]:
        """Get a single movie by ID.
        
        Args:
            movie_id: Movie ID
            
        Returns:
            Movie model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                "SELECT id, title, slug, category, synopsis, release_year, is_active FROM tavuno_movies WHERE id = %s AND is_active = TRUE",
                (movie_id,),
            ).fetchone()
        
        if row is None:
            return None
        
        return self._map_movie(row)

    def get_series(self, category_id: Optional[int] = None) -> List[Series]:
        """Get all active series, optionally filtered by category.
        
        Args:
            category_id: Optional category ID to filter series
            
        Returns:
            List of Series models
        """
        query = "SELECT id, title, slug, category, synopsis, is_active FROM tavuno_series WHERE is_active = TRUE"
        parameters: tuple = ()
        
        if category_id is not None:
            query += " AND category = %s"
            parameters = (category_id,)
        
        query += " ORDER BY title"
        
        with self._db() as conn:
            rows = conn.execute(query, parameters).fetchall()
        
        return [self._map_series(row) for row in rows]

    def get_series_by_id(self, series_id: int) -> Optional[Series]:
        """Get a single series by ID.
        
        Args:
            series_id: Series ID
            
        Returns:
            Series model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                "SELECT id, title, slug, category, synopsis, is_active FROM tavuno_series WHERE id = %s AND is_active = TRUE",
                (series_id,),
            ).fetchone()
        
        if row is None:
            return None
        
        return self._map_series(row)

    def get_home(self) -> dict:
        """Get home page data (categories and featured channels).
        
        Returns:
            Dictionary with 'categories' and 'featured_channels' keys
        """
        with self._db() as conn:
            categories = conn.execute(
                "SELECT id, name, kind, parent, sort_order, is_active FROM tavuno_categories WHERE is_active = TRUE ORDER BY sort_order, name LIMIT 12"
            ).fetchall()
            channels = conn.execute(
                "SELECT id, name, slug, category, logo, is_active FROM tavuno_channels WHERE is_active = TRUE ORDER BY name LIMIT 12"
            ).fetchall()
        
        return {
            "categories": [self._map_category(row) for row in categories],
            "featured_channels": [self._map_channel(row) for row in channels],
        }

    def get_channel_details(self, channel_id: int) -> Optional[ChannelDetails]:
        """Get detailed channel information for content detail screens (M12).

        Args:
            channel_id: Channel ID

        Returns:
            ChannelDetails model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT c.id, c.name, c.slug, c.category, c.logo, c.is_active, cat.name as category_name
                FROM tavuno_channels c
                LEFT JOIN tavuno_categories cat ON c.category = cat.id
                WHERE c.id = %s AND c.is_active = TRUE
                """,
                (channel_id,),
            ).fetchone()

        if row is None:
            return None

        return ChannelDetails(
            id=row["id"],
            name=row["name"],
            slug=row["slug"],
            category_id=row.get("category"),
            logo=str(row["logo"]) if row.get("logo") else None,
            is_active=row["is_active"],
            description=None,  # No description field in current schema
            category_name=row.get("category_name"),
            playback_available=True,  # Assume available if active
        )

    def get_movie_details(self, movie_id: int) -> Optional[MovieDetails]:
        """Get detailed movie information for content detail screens (M12).

        Args:
            movie_id: Movie ID

        Returns:
            MovieDetails model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT m.id, m.title, m.slug, m.category, m.synopsis, m.release_year, m.is_active, cat.name as category_name
                FROM tavuno_movies m
                LEFT JOIN tavuno_categories cat ON m.category = cat.id
                WHERE m.id = %s AND m.is_active = TRUE
                """,
                (movie_id,),
            ).fetchone()

        if row is None:
            return None

        return MovieDetails(
            id=row["id"],
            title=row["title"],
            slug=row["slug"],
            category_id=row.get("category"),
            synopsis=row.get("synopsis"),
            release_year=row.get("release_year"),
            is_active=row["is_active"],
            poster=None,  # No poster field in current schema
            backdrop=None,  # No backdrop field in current schema
            duration=None,  # No duration field in current schema
            category_name=row.get("category_name"),
            genres=None,  # No genres field in current schema
            playback_available=True,  # Assume available if active
        )

    def get_series_details(self, series_id: int) -> Optional[SeriesDetails]:
        """Get detailed series information for content detail screens (M12).

        Args:
            series_id: Series ID

        Returns:
            SeriesDetails model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT s.id, s.title, s.slug, s.category, s.synopsis, s.is_active, cat.name as category_name
                FROM tavuno_series s
                LEFT JOIN tavuno_categories cat ON s.category = cat.id
                WHERE s.id = %s AND s.is_active = TRUE
                """,
                (series_id,),
            ).fetchone()

        if row is None:
            return None

        # Fetch seasons with episode counts
        seasons_data = []
        episode_count = 0
        try:
            with self._db() as conn:
                seasons = conn.execute(
                    """
                    SELECT id, season_number, title, poster
                    FROM tavuno_seasons
                    WHERE series = %s AND is_active = TRUE
                    ORDER BY season_number
                    """,
                    (series_id,),
                ).fetchall()

                for season_row in seasons:
                    # Count episodes for this season
                    episode_count_row = conn.execute(
                        """
                        SELECT COUNT(*) as count
                        FROM tavuno_episodes
                        WHERE season = %s AND is_active = TRUE
                        """,
                        (season_row["id"],),
                    ).fetchone()

                    season_episode_count = episode_count_row["count"] if episode_count_row else 0
                    episode_count += season_episode_count

                    seasons_data.append(SeasonDetails(
                        id=season_row["id"],
                        season_number=season_row["season_number"],
                        name=season_row["title"],
                        poster=str(season_row["poster"]) if season_row.get("poster") else None,
                        episode_count=season_episode_count
                    ))
        except Exception:
            # Tables might not exist yet
            pass

        return SeriesDetails(
            id=row["id"],
            title=row["title"],
            slug=row["slug"],
            category_id=row.get("category"),
            synopsis=row.get("synopsis"),
            is_active=row["is_active"],
            poster=None,  # No poster field in current schema
            backdrop=None,  # No backdrop field in current schema
            release_year=None,  # No release_year field in current schema
            category_name=row.get("category_name"),
            seasons=seasons_data if seasons_data else None,
            episode_count=episode_count if episode_count > 0 else None,
            playback_available=True,  # Assume available if active
        )

    def get_season_episodes(self, season_id: int) -> List[Dict[str, Any]]:
        """Get episodes for a specific season (M12).

        Args:
            season_id: Season ID

        Returns:
            List of episode dictionaries
        """
        try:
            with self._db() as conn:
                rows = conn.execute(
                    """
                    SELECT id, episode_number, title, synopsis, duration, thumbnail
                    FROM tavuno_episodes
                    WHERE season = %s AND is_active = TRUE
                    ORDER BY episode_number
                    """,
                    (season_id,),
                ).fetchall()

            return [
                {
                    "id": row["id"],
                    "episode_number": row["episode_number"],
                    "title": row["title"],
                    "synopsis": row.get("synopsis"),
                    "duration": row.get("duration"),
                    "thumbnail": str(row["thumbnail"]) if row.get("thumbnail") else None,
                }
                for row in rows
            ]
        except Exception:
            # Table might not exist yet
            return []

    # M11 Sports service methods

    def get_competitions(self, sport: Optional[str] = None) -> List[Competition]:
        """Get all active competitions, optionally filtered by sport (M11).

        Args:
            sport: Optional sport filter (e.g., "football", "basketball")

        Returns:
            List of Competition models
        """
        try:
            with self._db() as conn:
                if sport:
                    rows = conn.execute(
                        """
                        SELECT id, name, slug, sport, category, external_id, is_active
                        FROM tavuno_competitions
                        WHERE sport = %s AND is_active = TRUE
                        ORDER BY name
                        """,
                        (sport,),
                    ).fetchall()
                else:
                    rows = conn.execute(
                        """
                        SELECT id, name, slug, sport, category, external_id, is_active
                        FROM tavuno_competitions
                        WHERE is_active = TRUE
                        ORDER BY sport, name
                        """,
                    ).fetchall()

            return [self._map_competition(row) for row in rows]
        except Exception as e:
            # Table might not exist or other error
            return []

    def get_competition(self, competition_id: int) -> Optional[Competition]:
        """Get a specific competition by ID (M11).

        Args:
            competition_id: Competition ID

        Returns:
            Competition model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT id, name, slug, sport, category, external_id, is_active
                FROM tavuno_competitions
                WHERE id = %s AND is_active = TRUE
                """,
                (competition_id,),
            ).fetchone()

        if row is None:
            return None

        return self._map_competition(row)

    def get_teams(self, competition_id: Optional[int] = None) -> List[Team]:
        """Get all active teams, optionally filtered by competition (M11).

        Args:
            competition_id: Optional competition filter

        Returns:
            List of Team models
        """
        with self._db() as conn:
            if competition_id:
                rows = conn.execute(
                    """
                    SELECT id, name, slug, competition, logo, external_id, is_active
                    FROM tavuno_teams
                    WHERE competition = %s AND is_active = TRUE
                    ORDER BY name
                    """,
                    (competition_id,),
                ).fetchall()
            else:
                rows = conn.execute(
                    """
                    SELECT id, name, slug, competition, logo, external_id, is_active
                    FROM tavuno_teams
                    WHERE is_active = TRUE
                    ORDER BY name
                    """,
                ).fetchall()

        return [self._map_team(row) for row in rows]

    def get_matches(
        self,
        competition_id: Optional[int] = None,
        status: Optional[str] = None,
        limit: int = 100
    ) -> List[Match]:
        """Get matches, optionally filtered by competition and status (M11).

        Args:
            competition_id: Optional competition filter
            status: Optional status filter ("upcoming", "live", "finished", "postponed")
            limit: Maximum number of matches to return

        Returns:
            List of Match models
        """
        try:
            with self._db() as conn:
                query = """
                    SELECT id, competition, home_team, away_team, channel, kickoff, status, home_score, away_score, external_id, is_active
                    FROM tavuno_matches
                    WHERE is_active = TRUE
                """
                params = []

                if competition_id:
                    query += " AND competition = %s"
                    params.append(competition_id)

                if status:
                    query += " AND status = %s"
                    params.append(status)

                query += " ORDER BY kickoff DESC LIMIT %s"
                params.append(limit)

                rows = conn.execute(query, tuple(params)).fetchall()

            return [self._map_match(row) for row in rows]
        except Exception as e:
            # Table might not exist or other error
            return []

    def get_match(self, match_id: int) -> Optional[Match]:
        """Get a specific match by ID (M11).

        Args:
            match_id: Match ID

        Returns:
            Match model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT id, competition, home_team, away_team, channel, kickoff, status, home_score, away_score, external_id, is_active
                FROM tavuno_matches
                WHERE id = %s AND is_active = TRUE
                """,
                (match_id,),
            ).fetchone()

        if row is None:
            return None

        return self._map_match(row)

    def get_match_details(self, match_id: int) -> Optional[MatchDetails]:
        """Get detailed match information with team names and channel (M11).

        Args:
            match_id: Match ID

        Returns:
            MatchDetails model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT
                    m.id, m.competition, m.home_team, m.away_team, m.channel,
                    m.kickoff, m.status, m.home_score, m.away_score, m.is_active,
                    comp.name as competition_name,
                    ht.name as home_team_name, ht.logo as home_team_logo,
                    at.name as away_team_name, at.logo as away_team_logo,
                    ch.name as channel_name
                FROM tavuno_matches m
                LEFT JOIN tavuno_competitions comp ON m.competition = comp.id
                LEFT JOIN tavuno_teams ht ON m.home_team = ht.id
                LEFT JOIN tavuno_teams at ON m.away_team = at.id
                LEFT JOIN tavuno_channels ch ON m.channel = ch.id
                WHERE m.id = %s AND m.is_active = TRUE
                """,
                (match_id,),
            ).fetchone()

        if row is None:
            return None

        return self._map_match_details(row)
