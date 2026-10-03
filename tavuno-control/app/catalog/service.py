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

import logging
from typing import Optional, List, Dict, Any, Set
from contextlib import contextmanager

from .models import (
    Channel, Category, Movie, Series,
    ChannelDetails, MovieDetails, SeriesDetails, SeasonDetails, EpisodeDetails,
    Competition, Team, Match, MatchDetails, ViewerState
)
from .scope import live_channel_limit, live_channel_scope, scope_int, scope_text
from ..assets import asset_url
from ..content_refs import fetch_items
from ..customize.ordering import apply_overrides
from ..customize.service import CustomizeService
from ..favourites.service import FavouritesService
from ..resume.service import ResumeService

logger = logging.getLogger("tavuno-control.catalog")

# Which customization kind applies to a category read, keyed by the catalog
# "kind" the endpoint was asked for. A read with no kind (every category) has no
# single customization list to apply and is therefore left in natural order.
CATEGORY_CUSTOMIZE_KIND = {
    "live": "live_category",
    "movie": "movie_category",
    "series": "series_category",
}


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

    # --- Live-channel test scope ------------------------------------------
    # Thin delegates over app.catalog.scope, which owns the implementation and
    # is shared with the EPG guide so both readers filter channels identically.

    @staticmethod
    def _scope_text(value: Any) -> str:
        """Coerce a settings value to text; non-strings (e.g. test doubles) -> ""."""
        return scope_text(value)

    @staticmethod
    def _scope_int(value: Any) -> int:
        """Coerce a settings value to int; anything unusable -> 0."""
        return scope_int(value)

    def _live_channel_scope(self) -> tuple:
        """Return an extra WHERE fragment + params restricting live channels."""
        return live_channel_scope(getattr(self.services, "settings", None))

    def _live_channel_limit(self) -> int:
        """Cap on how many live channels are listed; 0 means unlimited."""
        return live_channel_limit(getattr(self.services, "settings", None))

    def _apply_profile_overrides(self, items: List[Any], profile_id: Optional[int], kind: str) -> List[Any]:
        """Filter and reorder ``items`` by a profile's stored customization.

        A no-op when no profile is supplied, so an unauthenticated/legacy caller
        sees exactly the pre-customization behaviour. Applying happens in Python
        rather than SQL because the catalog is small (the whole channel list is
        already fetched) and because the ordering rule â€” pinned first, then the
        natural order â€” is a pure function worth testing
        (:func:`app.customize.ordering.apply_overrides`).
        """
        if profile_id is None:
            return items
        overrides = CustomizeService(self.services).overrides(int(profile_id), kind)
        return apply_overrides(items, overrides)

    # --- Viewer state + assets (A6) ------------------------------------------

    def _viewer_state(
        self,
        profile_id: Optional[int],
        kind: str,
        ids: List[int],
    ) -> Dict[int, ViewerState]:
        """Favourite flag + resume position for the given ids, keyed by id.

        One pair of queries per read, not per item: a 12-tile rail asking the
        database twice is cheap, asking 24 times is not. A profile-less caller
        gets no state at all â€” and, importantly, gets *no queries either*, so the
        pre-A6 call pattern is unchanged for anyone who has not logged in.

        A failure here degrades rather than raises: a grid that cannot be read
        because the favourites table is missing is strictly worse than a grid
        with no hearts on it. Degraded still returns a state per id (all
        defaults), so ``viewer`` is present for every profile read and a client
        never has to null-check it.
        """
        if profile_id is None:
            return {}
        if not ids:
            return {}

        favourites: set = set()
        progress: Dict[int, Any] = {}
        try:
            favourites = FavouritesService(self.services).marked(int(profile_id), kind, ids)
            progress = ResumeService(self.services).by_kind(int(profile_id), kind, ids)
        except Exception:  # pragma: no cover - needs a database without migration 009
            logger.warning(
                "Viewer state unavailable for kind=%s; serving catalog without it", kind
            )

        return {
            item_id: ViewerState(
                is_favourite=item_id in favourites,
                progress=progress[item_id].progress if item_id in progress else None,
                position_ms=progress[item_id].position_ms if item_id in progress else 0,
            )
            for item_id in ids
        }

    def _with_viewer_state(
        self,
        items: List[Any],
        profile_id: Optional[int],
        kind: str,
    ) -> List[Any]:
        """Attach per-item [ViewerState] in place, and return the same list."""
        states = self._viewer_state(profile_id, kind, [item.id for item in items])
        if not states:
            return items
        for item in items:
            state = states.get(item.id)
            if state is not None:
                item.viewer = state
        return items

    def _asset(self, value: Any) -> Optional[str]:
        """Normalize one stored artwork reference into a fetchable URL."""
        return asset_url(value, getattr(self.services.settings, "directus_url", ""))

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
            logo=self._asset(row.get("logo")),  # Directus UUID -> asset URL
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
            poster=self._asset(row.get("poster")),
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
            poster=self._asset(row.get("poster")),
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

    def get_channels(self, category_id: Optional[int] = None, profile_id: Optional[int] = None) -> List[Channel]:
        """Get all active channels, optionally filtered by category.

        Args:
            category_id: Optional category ID to filter channels
            profile_id: Optional profile whose rail order/visibility to apply

        Returns:
            List of Channel models
        """
        scope_clause, scope_params = self._live_channel_scope()
        query = "SELECT id, name, slug, category, logo, is_active FROM tavuno_channels WHERE is_active = TRUE"
        parameters: List[Any] = []

        if category_id is not None:
            query += " AND category = %s"
            parameters.append(category_id)

        query += scope_clause
        parameters.extend(scope_params)
        query += " ORDER BY name"

        limit = self._live_channel_limit()
        if limit:
            query += " LIMIT %s"
            parameters.append(limit)

        with self._db() as conn:
            rows = conn.execute(query, tuple(parameters)).fetchall()

        channels = self._with_viewer_state([self._map_channel(row) for row in rows], profile_id, "channel")
        return self._apply_profile_overrides(channels, profile_id, "live_channel")

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

    def get_categories(self, kind: Optional[str] = None, profile_id: Optional[int] = None) -> List[Category]:
        """Get all active categories, optionally filtered by kind.

        Args:
            kind: Optional kind filter (e.g., "live", "movie", "series", "sports")
            profile_id: Optional profile whose rail order/visibility to apply

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

        categories = [self._map_category(row) for row in rows]
        customize_kind = CATEGORY_CUSTOMIZE_KIND.get(kind or "")
        if customize_kind is None:
            return categories
        return self._apply_profile_overrides(categories, profile_id, customize_kind)

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

    def get_movies(self, category_id: Optional[int] = None, profile_id: Optional[int] = None) -> List[Movie]:
        """Get all active movies, optionally filtered by category.

        Args:
            category_id: Optional category ID to filter movies
            profile_id: Optional profile whose favourite/progress state to project

        Returns:
            List of Movie models
        """
        query = "SELECT id, title, slug, category, synopsis, release_year, poster, is_active FROM tavuno_movies WHERE is_active = TRUE"
        parameters: tuple = ()
        
        if category_id is not None:
            query += " AND category = %s"
            parameters = (category_id,)
        
        query += " ORDER BY title"
        
        with self._db() as conn:
            rows = conn.execute(query, parameters).fetchall()
        
        return self._with_viewer_state([self._map_movie(row) for row in rows], profile_id, "movie")

    def get_movie(self, movie_id: int) -> Optional[Movie]:
        """Get a single movie by ID.
        
        Args:
            movie_id: Movie ID
            
        Returns:
            Movie model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                "SELECT id, title, slug, category, synopsis, release_year, poster, is_active FROM tavuno_movies WHERE id = %s AND is_active = TRUE",
                (movie_id,),
            ).fetchone()
        
        if row is None:
            return None
        
        return self._map_movie(row)

    def get_series(self, category_id: Optional[int] = None, profile_id: Optional[int] = None) -> List[Series]:
        """Get all active series, optionally filtered by category.

        Args:
            category_id: Optional category ID to filter series
            profile_id: Optional profile whose favourite/progress state to project

        Returns:
            List of Series models
        """
        query = "SELECT id, title, slug, category, synopsis, poster, is_active FROM tavuno_series WHERE is_active = TRUE"
        parameters: tuple = ()
        
        if category_id is not None:
            query += " AND category = %s"
            parameters = (category_id,)
        
        query += " ORDER BY title"
        
        with self._db() as conn:
            rows = conn.execute(query, parameters).fetchall()
        
        return self._with_viewer_state([self._map_series(row) for row in rows], profile_id, "series")

    def get_series_by_id(self, series_id: int) -> Optional[Series]:
        """Get a single series by ID.
        
        Args:
            series_id: Series ID
            
        Returns:
            Series model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                "SELECT id, title, slug, category, synopsis, poster, is_active FROM tavuno_series WHERE id = %s AND is_active = TRUE",
                (series_id,),
            ).fetchone()
        
        if row is None:
            return None
        
        return self._map_series(row)

    # --- Search (Slice B: OwnTV parity) ---------------------------------

    @staticmethod
    def _escape_like(query: str) -> str:
        """Escape LIKE/ILIKE wildcards: a query of '%' must not match everything."""
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    def search(
            self,
            query: str,
            limit: int = 20,
            profile_id: Optional[int] = None,
    ) -> Dict[str, Any]:
        """Case-insensitive name/title search across channels, movies and series.

        Live channels are filtered through the same test-scope allowlist (and cap)
        as :meth:`get_channels`, so search can never reveal a channel the app is
        not allowed to list.

        Args:
            query: Raw user query; trimmed before use. Empty -> empty result groups.
            limit: Per-collection cap, clamped to 1..50.
            profile_id: Optional profile whose favourite/progress state to project

        Returns:
            ``{"query": str, "channels": [Channel], "movies": [Movie], "series": [Series]}``
        """
        term = query.strip() if isinstance(query, str) else ""
        if not term:
            return {"query": term, "channels": [], "movies": [], "series": []}

        pattern = f"%{self._escape_like(term)}%"
        cap = max(1, min(int(limit), 50))
        scope_clause, scope_params = self._live_channel_scope()
        live_limit = self._live_channel_limit()
        channel_cap = min(cap, live_limit) if live_limit else cap

        channel_query = (
            "SELECT id, name, slug, category, logo, is_active FROM tavuno_channels "
            "WHERE is_active = TRUE AND name ILIKE %s"
            + scope_clause
            + " ORDER BY name LIMIT %s"
        )
        channel_params: List[Any] = [pattern, *scope_params, channel_cap]
        movie_query = (
            "SELECT id, title, slug, category, synopsis, release_year, poster, is_active FROM tavuno_movies "
            "WHERE is_active = TRUE AND title ILIKE %s ORDER BY title LIMIT %s"
        )
        series_query = (
            "SELECT id, title, slug, category, synopsis, poster, is_active FROM tavuno_series "
            "WHERE is_active = TRUE AND title ILIKE %s ORDER BY title LIMIT %s"
        )

        with self._db() as conn:
            channel_rows = conn.execute(channel_query, tuple(channel_params)).fetchall()
            movie_rows = conn.execute(movie_query, (pattern, cap)).fetchall()
            series_rows = conn.execute(series_query, (pattern, cap)).fetchall()

        return {
            "query": term,
            "channels": self._with_viewer_state(
                [self._map_channel(row) for row in channel_rows], profile_id, "channel"
            ),
            "movies": self._with_viewer_state(
                [self._map_movie(row) for row in movie_rows], profile_id, "movie"
            ),
            "series": self._with_viewer_state(
                [self._map_series(row) for row in series_rows], profile_id, "series"
            ),
        }

    # Rail sizes. Small on purpose: these are TV rails a viewer scrolls with a
    # remote, and a 40-tile row is unreachable content rather than content.
    HOME_CHANNEL_RAIL = 12
    HOME_VOD_RAIL = 12
    HOME_PERSONAL_RAIL = 12

    def get_home(self, profile_id: Optional[int] = None) -> dict:
        """Every home rail in one round trip.

        The response is **additive** on purpose. It used to carry only
        ``categories`` and ``featured_channels``, but the Android client has
        always modeled ``channels``/``categories``/``movies``/``series`` â€” so the
        two halves of the app disagreed about the shape of home and Gson's nulls
        were invisible until a screen tried to render them. Both keys are now
        returned (``featured_channels`` stays as an alias of ``channels``)
        because removing it would break any caller outside this repo, while
        adding to it breaks nobody: Gson ignores absent keys, so a build from
        before A6 reads the new payload correctly.

        Args:
            profile_id: Optional profile whose rails/order/visibility to apply

        Returns:
            Dictionary with 'categories', 'channels', 'featured_channels',
            'movies', 'series', 'continue_watching' and 'favourites'.
        """
        with self._db() as conn:
            categories = conn.execute(
                "SELECT id, name, kind, parent, sort_order, is_active FROM tavuno_categories WHERE is_active = TRUE ORDER BY sort_order, name LIMIT 12"
            ).fetchall()
            scope_clause, scope_params = self._live_channel_scope()
            # The limit doubles as the rail size: a 10-channel test scope must
            # not advertise 12 features.
            channel_limit = self._live_channel_limit() or self.HOME_CHANNEL_RAIL
            channels = conn.execute(
                "SELECT id, name, slug, category, logo, is_active FROM tavuno_channels WHERE is_active = TRUE"
                + scope_clause
                + " ORDER BY name LIMIT %s",
                tuple(scope_params) + (channel_limit,),
            ).fetchall()

        channel_items = self._apply_profile_overrides(
            self._with_viewer_state(
                [self._map_channel(row) for row in channels], profile_id, "channel"
            ),
            profile_id,
            "live_channel",
        )
        personal = self._home_personal(profile_id)

        return {
            "categories": [self._map_category(row) for row in categories],
            "channels": channel_items,
            # Kept for compatibility with callers written before `channels`; the
            # two are the same list, not two queries.
            "featured_channels": channel_items,
            "movies": self._with_viewer_state(
                self._home_vod("tavuno_movies", "_map_movie", self.HOME_VOD_RAIL),
                profile_id,
                "movie",
            ),
            "series": self._with_viewer_state(
                self._home_vod("tavuno_series", "_map_series", self.HOME_VOD_RAIL),
                profile_id,
                "series",
            ),
            "continue_watching": personal["continue_watching"],
            "favourites": personal["favourites"],
        }

    def _home_vod(self, table: str, mapper: str, limit: int) -> List[Any]:
        """One VOD rail (movies or series), most recently added first.

        A LEFT JOIN on the category is what makes this usable as a rail: the
        tiles carry their category name, so the client does not need a second
        request to label a grid. Failures degrade to an empty rail â€” a missing
        VOD table must not take the home screen down with it.
        """
        try:
            with self._db() as conn:
                rows = conn.execute(
                    f"SELECT v.*, c.name AS category_name FROM {table} v"
                    " LEFT JOIN tavuno_categories c ON v.category = c.id"
                    " WHERE v.is_active = TRUE"
                    " ORDER BY v.created_at DESC NULLS LAST, v.title"
                    " LIMIT %s",
                    (limit,),
                ).fetchall()
        except Exception:
            logger.warning("Home rail %s unavailable; serving it empty", table)
            return []
        return [getattr(self, mapper)(row) for row in rows]

    def _home_personal(self, profile_id: Optional[int]) -> Dict[str, List[Dict[str, Any]]]:
        """The two per-profile rails: continue watching, then favourites.

        Both are computed only for a real profile, so an anonymous caller pays
        no queries and sees the same payload it always did. Each rail is
        independent: a missing resume row must not hide the favourites.
        """
        if profile_id is None:
            return {"continue_watching": [], "favourites": []}

        rails: Dict[str, List[Dict[str, Any]]] = {}
        try:
            rails["continue_watching"] = self._continue_watching(int(profile_id))
        except Exception:
            logger.warning("Continue-watching rail unavailable; serving it empty")
            rails["continue_watching"] = []

        try:
            favourites: List[Dict[str, Any]] = []
            for kind in ("movie", "series", "channel"):
                for row in FavouritesService(self.services).list_rows(
                    int(profile_id), kind, limit=self.HOME_PERSONAL_RAIL
                ):
                    favourites.append({**row, "kind": kind})
            rails["favourites"] = favourites[: self.HOME_PERSONAL_RAIL]
        except Exception:
            logger.warning("Favourites rail unavailable; serving it empty")
            rails["favourites"] = []

        return rails

    def _continue_watching(self, profile_id: int) -> List[Dict[str, Any]]:
        """Resumable rows, most recent first, each carrying kind + progress.

        Read once rather than through :meth:`ResumeService.recent` so the rows and
        their progress share a single ordered query â€” zipping two independently
        ordered reads would silently pair the wrong items.
        """
        with self._db() as conn:
            rows = conn.execute(
                "SELECT kind, item_id, position_ms, duration_ms FROM tavuno_resume"
                " WHERE profile = %s ORDER BY updated_at DESC, id DESC LIMIT %s",
                (profile_id, self.HOME_PERSONAL_RAIL),
            ).fetchall()

        results: List[Dict[str, Any]] = []
        with self._db() as conn:
            for row in rows:
                kind = str(row["kind"])
                try:
                    fetched = fetch_items(conn, kind, [int(row["item_id"])])
                except ValueError:
                    # A row under a kind this build does not know must not take
                    # the whole rail down.
                    continue
                item = fetched.get(int(row["item_id"]))
                if item is None:
                    continue
                results.append(
                    {
                        **item,
                        "kind": kind,
                        "progress": ResumeService.ratio(
                            int(row["position_ms"]), int(row["duration_ms"])
                        ),
                        "position_ms": int(row["position_ms"]),
                        "duration_ms": int(row["duration_ms"]),
                    }
                )
        return results

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
            logo=self._asset(row.get("logo")),
            is_active=row["is_active"],
            description=None,  # No description field in current schema
            category_name=row.get("category_name"),
            playback_available=True,  # Assume available if active
        )

    def get_movie_details(
        self,
        movie_id: int,
        profile_id: Optional[int] = None,
    ) -> Optional[MovieDetails]:
        """Get detailed movie information for content detail screens (M12).

        Args:
            movie_id: Movie ID
            profile_id: Optional profile whose favourite/progress state to project

        Returns:
            MovieDetails model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT m.id, m.title, m.slug, m.category, m.synopsis, m.release_year,
                       m.is_active, m.poster, m.backdrop, m.duration,
                       cat.name as category_name
                FROM tavuno_movies m
                LEFT JOIN tavuno_categories cat ON m.category = cat.id
                WHERE m.id = %s AND m.is_active = TRUE
                """,
                (movie_id,),
            ).fetchone()

        if row is None:
            return None

        details = MovieDetails(
            id=row["id"],
            title=row["title"],
            slug=row["slug"],
            category_id=row.get("category"),
            synopsis=row.get("synopsis"),
            release_year=row.get("release_year"),
            is_active=row["is_active"],
            # Artwork: a stored Directus file UUID becomes an /assets/<uuid> URL.
            poster=self._asset(row.get("poster")),
            backdrop=self._asset(row.get("backdrop")),
            duration=row.get("duration"),
            category_name=row.get("category_name"),
            genres=None,  # No genres field in current schema
            playback_available=True,  # Assume available if active
        )
        return self._with_viewer_state([details], profile_id, "movie")[0]

    def get_series_details(
        self,
        series_id: int,
        profile_id: Optional[int] = None,
    ) -> Optional[SeriesDetails]:
        """Get detailed series information for content detail screens (M12).

        Args:
            series_id: Series ID
            profile_id: Optional profile whose favourite/progress state to project

        Returns:
            SeriesDetails model or None if not found
        """
        with self._db() as conn:
            row = conn.execute(
                """
                SELECT s.id, s.title, s.slug, s.category, s.synopsis, s.is_active,
                       s.poster, s.backdrop, s.release_year,
                       cat.name as category_name
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
                        poster=self._asset(season_row.get("poster")),
                        episode_count=season_episode_count
                    ))
        except Exception:
            # Tables might not exist yet
            pass

        details = SeriesDetails(
            id=row["id"],
            title=row["title"],
            slug=row["slug"],
            category_id=row.get("category"),
            synopsis=row.get("synopsis"),
            is_active=row["is_active"],
            poster=self._asset(row.get("poster")),
            backdrop=self._asset(row.get("backdrop")),
            release_year=row.get("release_year"),
            category_name=row.get("category_name"),
            seasons=seasons_data if seasons_data else None,
            episode_count=episode_count if episode_count > 0 else None,
            playback_available=True,  # Assume available if active
        )
        return self._with_viewer_state([details], profile_id, "series")[0]

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
                    "thumbnail": self._asset(row.get("thumbnail")),
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
