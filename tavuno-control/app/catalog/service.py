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

from typing import Optional, List
from contextlib import contextmanager

from .models import Channel, Category, Movie, Series, ChannelDetails, MovieDetails, SeriesDetails


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
            seasons=None,  # No seasons data in current schema
            episode_count=None,  # No episode data in current schema
            playback_available=True,  # Assume available if active
        )
