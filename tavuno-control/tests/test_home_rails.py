"""A6: home rails, the viewer-state projection and asset URLs on reads.

Three behaviours are pinned here, all of them invisible to a response-shape test:

1. **The contract mismatch is closed.** ``/v1/home`` used to answer
   ``categories`` + ``featured_channels`` while the Android client has always
   modeled ``channels``/``categories``/``movies``/``series``.
2. **The read is unchanged for a caller with no profile.** No new queries, no
   viewer state, no personal rails — that is what keeps an anonymous TV client
   byte-identical to before.
3. **A stored Directus UUID leaves the API as a URL** on every read that carries
   artwork.
"""

from contextlib import contextmanager
from unittest.mock import Mock, patch

import pytest

from app.catalog.service import CatalogService

UUID_POSTER = "3f6b1c2a-9d4e-4f7b-8a1d-2c5e6b7a8c9d"
UUID_LOGO = "11111111-2222-3333-4444-555555555555"
DIRECTUS = "https://media.test/directus"


class Result:
    def __init__(self, value):
        self.value = value

    def fetchall(self):
        if isinstance(self.value, list):
            return self.value
        return [] if self.value is None else [self.value]

    def fetchone(self):
        if isinstance(self.value, list):
            return self.value[0] if self.value else None
        return self.value


def _from_table(query: str) -> str:
    import re

    match = re.search(r"FROM\s+([a-z_]+)", query, re.IGNORECASE)
    return match.group(1) if match else ""


class RoutingConnection:
    """Answers per table, so a multi-table read gets plausible rows for each.

    Keyed on the ``FROM`` target rather than a substring: the home VOD rails join
    ``tavuno_categories``, so substring matching would hand category rows to the
    movie mapper — the exact bug a too-naive double hides.
    """

    def __init__(self, rows=None):
        self.rows = rows or {}
        self.queries = []
        self.queued = []

    def execute(self, query, parameters=()):
        query = str(query)
        self.queries.append(query)
        if self.queued:
            return Result(self.queued.pop(0))
        return Result(self.rows.get(_from_table(query), []))

    def commit(self):
        pass


@contextmanager
def service_with(rows, settings=None, queued=None):
    """A CatalogService with routed reads and stubbed A6 collaborators."""
    services = Mock()
    services.settings = settings if settings is not None else Mock(directus_url=DIRECTUS)
    catalog = CatalogService(services)

    connection = RoutingConnection(rows)
    if queued:
        connection.queued.extend(queued)

    favourite_ids = set()
    progress = {}
    # Records which profile ids the state services were asked about, so a test
    # can assert the lookup happened (or did not) without asserting on SQL that
    # the stub intercepts anyway.
    movies_seen = {"favourites": [], "resume": []}

    favourites = Mock()
    favourites.marked.side_effect = lambda profile, kind, ids: (
        movies_seen["favourites"].append(profile) or (favourite_ids & set(ids))
    )
    favourites.list_rows.return_value = []

    resume = Mock()
    resume.by_kind.side_effect = lambda profile, kind, ids: (
        movies_seen["resume"].append(profile) or progress
    )
    resume.ratio = lambda position, duration: (
        None if duration <= 0 else min(1.0, position / duration)
    )

    with patch.object(catalog, "_db") as db, \
            patch("app.catalog.service.FavouritesService", return_value=favourites), \
            patch("app.catalog.service.ResumeService", return_value=resume):
        db.return_value.__enter__.return_value = connection
        yield catalog, connection, favourite_ids, progress, movies_seen
CATEGORY_ROWS = [
    {"id": 1, "name": "Live", "kind": "live", "parent": None, "sort_order": 1, "is_active": True}
]
CHANNEL_ROWS = [
    {"id": 10, "name": "Alpha", "slug": "alpha", "category": 1, "logo": UUID_LOGO, "is_active": True},
    {"id": 11, "name": "Beta", "slug": "beta", "category": 1, "logo": None, "is_active": True},
]
MOVIE_ROWS = [
    {
        "id": 20,
        "title": "Movie A",
        "slug": "movie-a",
        "category": 2,
        "synopsis": "s",
        "release_year": 2024,
        "poster": UUID_POSTER,
        "is_active": True,
    }
]
SERIES_ROWS = [
    {
        "id": 30,
        "title": "Series A",
        "slug": "series-a",
        "category": 3,
        "synopsis": "s",
        "poster": None,
        "is_active": True,
    }
]


def base_rows():
    return {
        "tavuno_categories": CATEGORY_ROWS,
        "tavuno_channels": CHANNEL_ROWS,
        "tavuno_movies": MOVIE_ROWS,
        "tavuno_series": SERIES_ROWS,
    }


class TestHomeShape:
    def test_home_answers_the_keys_the_android_client_models(self):
        """channels/categories/movies/series — what HomeData deserializes."""
        with service_with(base_rows()) as (catalog, *_):
            home = catalog.get_home()

        for key in ("categories", "channels", "movies", "series"):
            assert key in home, f"/v1/home is missing '{key}' (HomeData expects it)"

    def test_the_legacy_featured_channels_key_still_answers(self):
        """Additive, not a rename: a caller outside this repo must not break."""
        with service_with(base_rows()) as (catalog, *_):
            home = catalog.get_home()

        assert [c.id for c in home["featured_channels"]] == [c.id for c in home["channels"]]

    def test_the_personal_rails_are_always_present(self):
        """Empty for a caller without a profile, not absent — a client that
        renders rails from the keys should not have to null-check."""
        with service_with(base_rows()) as (catalog, *_):
            home = catalog.get_home()

        assert home["continue_watching"] == []
        assert home["favourites"] == []

    def test_a_missing_vod_table_yields_an_empty_rail_not_a_500(self):
        with service_with(base_rows()) as (catalog, connection, *_):
            original = connection.execute

            def explode(query, parameters=()):
                if "tavuno_movies" in str(query):
                    raise RuntimeError('relation "tavuno_movies" does not exist')
                return original(query, parameters)

            connection.execute = explode
            home = catalog.get_home()

        assert home["movies"] == []
        assert home["channels"]  # the rest of home still answered

    def test_search_carries_poster_urls_through_to_the_movies_group(self):
        queued = [[CHANNEL_ROWS[0]], [MOVIE_ROWS[0]], [SERIES_ROWS[0]]]
        with service_with(base_rows(), queued=queued) as (catalog, *_):
            results = catalog.search("a")

        assert results["movies"][0].poster == f"{DIRECTUS}/assets/{UUID_POSTER}"


class TestAssetUrls:
    def test_a_stored_uuid_leaves_as_an_asset_url(self):
        with service_with(base_rows()) as (catalog, *_):
            movies = catalog.get_movies()

        assert movies[0].poster == f"{DIRECTUS}/assets/{UUID_POSTER}"

    def test_a_null_artwork_column_stays_null(self):
        """Not an empty string: null is what the client reads as "placeholder"."""
        with service_with(base_rows()) as (catalog, *_):
            series = catalog.get_series()

        assert series[0].poster is None

    def test_channel_logos_are_normalized_too(self):
        with service_with(base_rows()) as (catalog, *_):
            channels = catalog.get_channels()

        assert channels[0].logo == f"{DIRECTUS}/assets/{UUID_LOGO}"
        assert channels[1].logo is None

    def test_without_a_configured_base_a_uuid_is_left_alone(self):
        """Inventing '/assets/<uuid>' against the API host resolves to a 404;
        the raw UUID at least says what went wrong."""
        settings = Mock(directus_url="")
        with service_with(base_rows(), settings=settings) as (catalog, *_):
            movies = catalog.get_movies()

        assert movies[0].poster == UUID_POSTER


class TestViewerStateProjection:
    def test_a_favourited_item_carries_the_flag(self):
        with service_with(base_rows()) as (catalog, _conn, favourites, _progress, _seen):
            favourites.add(20)
            movies = catalog.get_movies(profile_id=7)

        assert movies[0].viewer.is_favourite is True

    def test_an_unfavourited_item_reports_false(self):
        with service_with(base_rows()) as (catalog, *_rest):
            movies = catalog.get_movies(profile_id=7)

        assert movies[0].viewer.is_favourite is False

    def test_resume_position_becomes_a_progress_ratio(self):
        from app.resume.models import ProgressPayload

        with service_with(base_rows()) as (catalog, _conn, _fav, progress, _seen):
            progress[20] = ProgressPayload(
                kind="movie", item_id=20, position_ms=25_000, duration_ms=100_000, progress=0.25
            )
            movies = catalog.get_movies(profile_id=7)

        assert movies[0].viewer.progress == 0.25
        assert movies[0].viewer.position_ms == 25_000

    def test_an_item_with_nothing_stored_reports_no_progress_rather_than_zero(self):
        """0.0 would draw an empty bar; None means 'no bar'."""
        with service_with(base_rows()) as (catalog, *_rest):
            movies = catalog.get_movies(profile_id=7)

        assert movies[0].viewer.progress is None

    def test_a_caller_without_a_profile_never_asks_the_state_services(self):
        with service_with(base_rows()) as (catalog, _, _, _, movies_seen):
            catalog.get_movies()

        assert movies_seen["favourites"] == []
        assert movies_seen["resume"] == []

    def test_a_profile_read_does_ask_the_state_services(self):
        # The collaborators are stubbed, so the signal is that they were called —
        # the SQL they would emit is behind the stub by construction.
        with service_with(base_rows()) as (catalog, _conn, _fav, _progress, movies_seen):
            catalog.get_movies(profile_id=7)

        assert movies_seen["favourites"] == [7]
        assert movies_seen["resume"] == [7]

    def test_a_broken_state_table_degrades_to_a_plain_grid(self):
        """Missing migration 009 must not take the catalog down with it."""
        with service_with(base_rows()) as (catalog, *_rest):
            with patch("app.catalog.service.FavouritesService") as broken:
                broken.return_value.marked.side_effect = RuntimeError("no such table")
                movies = catalog.get_movies(profile_id=7)

        assert [m.id for m in movies] == [20]
        # Still a ViewerState, so the client never has to null-check the key.
        assert movies[0].viewer.is_favourite is False
        assert movies[0].viewer.progress is None


class TestKindSafety:
    def test_the_table_name_comes_from_the_allowlist_not_the_caller(self):
        from app.content_refs import KIND_TABLES, table_for, validate_kind

        assert table_for("movie") == "tavuno_movies"
        with pytest.raises(ValueError):
            validate_kind("movies; DROP TABLE tavuno_movies")
        with pytest.raises(ValueError):
            table_for("anything else")
        # The dict is the single source of truth for what is interpolatable.
        assert set(KIND_TABLES) == {"channel", "movie", "series", "episode"}