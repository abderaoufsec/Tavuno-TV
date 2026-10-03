"""A6: favourites + resume services.

Both services are thin wrappers over one indexed table each, so what is worth
pinning is the behaviour that is *not* obvious from the SQL: the idempotent
upsert, the "finished means forget" rule, the ``None``-progress convention for
live content, and — most importantly — that a write scoped to one profile can
never be read by another.
"""

from contextlib import contextmanager
from unittest.mock import MagicMock, Mock, patch

import pytest

from app.favourites.service import FavouritesService
from app.resume.service import COMPLETION_RATIO, ResumeService


class Result:
    """A cursor-shaped wrapper so results can be queued as plain values.

    The services call ``.fetchall()``/``.fetchone()`` on whatever ``execute``
    returned, so a queued raw list/dict/None would fail with AttributeError
    rather than exercising the behaviour under test.
    """

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

    @property
    def rowcount(self):
        return 1


class FakeConnection:
    """Records statements and replays queued results, in query order."""

    def __init__(self):
        self.statements = []
        self.results = []
        self.commits = 0
        self.cursor = Result(None)

    def execute(self, query, parameters=()):
        self.statements.append((str(query), parameters))
        if self.results:
            return Result(self.results.pop(0))
        return self.cursor

    def commit(self):
        self.commits += 1


@contextmanager
def connection_for(service, connection):
    """Patch ``_db`` to hand out ``connection`` for the duration of the block."""
    original = patch.object(service, "_db")
    mock = original.start()
    mock.return_value.__enter__.return_value = connection
    try:
        yield connection
    finally:
        original.stop()


@pytest.fixture
def favourites():
    return FavouritesService(Mock())


@pytest.fixture
def resume():
    return ResumeService(Mock())


# --- favourites --------------------------------------------------------------


class TestFavouriteWrites:
    def test_setting_true_upserts_on_the_profile_kind_item_key(self, favourites):
        conn = FakeConnection()
        with connection_for(favourites, conn):
            assert favourites.set(7, "movie", 42, True) is True

        query, params = conn.statements[0]
        assert "INSERT INTO tavuno_favourites" in query
        # ON CONFLICT is what makes a double-tap a no-op rather than a 500.
        assert "ON CONFLICT" in query
        assert params == (7, "movie", 42)
        assert conn.commits == 1

    def test_setting_false_deletes_the_row_instead_of_tombstoning_it(self, favourites):
        conn = FakeConnection()
        with connection_for(favourites, conn):
            assert favourites.set(7, "movie", 42, False) is False

        query, params = conn.statements[0]
        assert query.strip().startswith("DELETE FROM tavuno_favourites")
        assert params == (7, "movie", 42)

    def test_a_toggle_of_an_absent_item_favourites_it(self, favourites):
        conn = FakeConnection()
        with patch.object(FavouritesService, "get", lambda self, p, k, i: False):
            with connection_for(favourites, conn):
                assert favourites.toggle(7, "movie", 42) is True

    def test_an_unknown_kind_is_rejected_before_any_sql_runs(self, favourites):
        conn = FakeConnection()
        with connection_for(favourites, conn):
            with pytest.raises(ValueError):
                favourites.set(7, "channels; DROP TABLE tavuno_channels", 1, True)

        assert conn.statements == []


class TestFavouriteReads:
    def test_marked_returns_a_set_of_only_the_flagged_ids(self, favourites):
        conn = FakeConnection()
        conn.results.append([{"item_id": 2}, {"item_id": 9}])
        with connection_for(favourites, conn):
            marked = favourites.marked(7, "movie", [1, 2, 3, 9])

        assert marked == {2, 9}
# --- resume ------------------------------------------------------------------


class TestResumeRatio:
    def test_a_half_watched_item_is_half(self, resume):
        assert resume.ratio(50_000, 100_000) == 0.5

    def test_an_unknown_duration_means_no_bar_rather_than_an_empty_one(self, resume):
        # Live channels have no end; 0.0 would paint an empty bar on every row.
        assert resume.ratio(10_000, 0) is None
        assert resume.ratio(0, 0) is None

    def test_out_of_range_positions_are_clamped(self, resume):
        assert resume.ratio(-5, 100) == 0.0
        assert resume.ratio(500, 100) == 1.0


class TestResumeWrites:
    def test_a_mid_item_position_is_stored_with_its_ratio(self, resume):
        conn = FakeConnection()
        conn.results.append({"position_ms": 30_000, "duration_ms": 120_000})
        with connection_for(resume, conn):
            payload = resume.record(7, "movie", 42, 30_000, 120_000)

        assert payload.position_ms == 30_000
        assert payload.progress == 0.25
        assert "INSERT INTO tavuno_resume" in conn.statements[0][0]
        assert "ON CONFLICT" in conn.statements[0][0]

    def test_a_finished_item_is_forgotten_rather_than_left_at_99_percent(self, resume):
        conn = FakeConnection()
        with connection_for(resume, conn):
            payload = resume.record(7, "movie", 42, 99_000, 100_000)

        assert payload is None
        assert conn.statements[0][0].strip().startswith("DELETE FROM tavuno_resume")
        assert 99_000 / 100_000 >= COMPLETION_RATIO

    def test_rewinding_to_the_start_also_forgets(self, resume):
        conn = FakeConnection()
        with connection_for(resume, conn):
            payload = resume.record(7, "movie", 42, 0, 120_000)

        assert payload is None
        assert conn.statements[0][0].strip().startswith("DELETE FROM tavuno_resume")

    def test_a_live_item_with_no_duration_is_still_recorded(self, resume):
        # No duration must not be read as "finished".
        conn = FakeConnection()
        conn.results.append({"position_ms": 45_000, "duration_ms": 0})
        with connection_for(resume, conn):
            payload = resume.record(7, "channel", 9, 45_000, 0)

        assert payload is not None
        assert payload.progress is None

    def test_an_unknown_kind_is_rejected_before_any_sql_runs(self, resume):
        conn = FakeConnection()
        with connection_for(resume, conn):
            with pytest.raises(ValueError):
                resume.record(7, "nope", 1, 1, 1)

        assert conn.statements == []


class TestResumeReads:
    def test_get_returns_none_when_there_is_nothing_to_resume(self, resume):
        conn = FakeConnection()
        conn.results.append(None)
        with connection_for(resume, conn):
            assert resume.get(7, "movie", 42) is None

    def test_get_rebuilds_the_ratio_from_the_stored_position(self, resume):
        conn = FakeConnection()
        conn.results.append({"position_ms": 10_000, "duration_ms": 40_000})
        with connection_for(resume, conn):
            payload = resume.get(7, "movie", 42)

        assert payload.progress == 0.25

    def test_by_kind_asks_only_about_the_ids_in_hand(self, resume):
        conn = FakeConnection()
        conn.results.append([{"item_id": 2, "position_ms": 5, "duration_ms": 10}])
        with connection_for(resume, conn):
            got = resume.by_kind(7, "movie", [1, 2, 3])

        assert set(got) == {2}
        assert conn.statements[0][1][0] == 7

    def test_an_empty_id_list_asks_nothing(self, resume):
        conn = FakeConnection()
        with connection_for(resume, conn):
            assert resume.by_kind(7, "movie", []) == {}

        assert conn.statements == []

    def test_an_empty_id_list_asks_nothing(self, favourites):
        conn = FakeConnection()
        with connection_for(favourites, conn):
            assert favourites.marked(7, "movie", []) == set()

        assert conn.statements == []

    def test_marked_scopes_the_query_to_the_caller(self, favourites):
        conn = FakeConnection()
        with connection_for(favourites, conn):
            favourites.marked(11, "movie", [3])

        query, params = conn.statements[0]
        assert "profile = %s" in query and "kind = %s" in query
        assert params[0] == 11 and params[1] == "movie"

    def test_ids_are_newest_first(self, favourites):
        conn = FakeConnection()
        conn.results.append([{"item_id": 5}, {"item_id": 4}])
        with connection_for(favourites, conn):
            assert favourites.ids(7, "movie") == [5, 4]

        assert "created_at DESC" in conn.statements[0][0]

    def test_list_rows_drops_ids_the_catalog_no_longer_lists(self, favourites):
        conn = FakeConnection()
        conn.results.append([{"item_id": 1}, {"item_id": 999}])
        # 999 is absent from the catalog, so only 1 can be returned.
        conn.results.append([{"id": 1, "title": "Kept", "slug": "k", "is_active": True}])
        with connection_for(favourites, conn):
            rows = favourites.list_rows(7, "movie")

        assert [row["id"] for row in rows] == [1]