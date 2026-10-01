"""Unit tests for the customization persistence service (Slice D)."""

from contextlib import contextmanager
from unittest.mock import MagicMock, patch

import pytest

from app.customize.models import CustomizationItem
from app.customize.service import KINDS, CustomizeService


@contextmanager
def fake_db(service: CustomizeService):
    """Replace the service's connection with a MagicMock for the block."""
    connection = MagicMock()
    connection.execute.return_value.rowcount = 0
    with patch.object(service, "_db") as mock_db:
        mock_db.return_value.__enter__.return_value = connection
        yield connection


@pytest.fixture
def service() -> CustomizeService:
    return CustomizeService(MagicMock())


class TestKindValidation:
    def test_only_the_documented_kinds_exist(self):
        assert KINDS == ("live_channel", "live_category", "movie_category", "series_category")

    @pytest.mark.parametrize("method", ["overrides", "list", "reset"])
    def test_unknown_kind_is_rejected(self, service, method):
        with pytest.raises(ValueError, match="unknown customization kind"):
            getattr(service, method)(1, "live_channells")

    def test_unknown_kind_is_rejected_on_write(self, service):
        with pytest.raises(ValueError, match="unknown customization kind"):
            service.save(1, "live_channells", [])

    def test_rejected_write_stores_nothing(self, service):
        with fake_db(service) as conn:
            with pytest.raises(ValueError):
                service.save(1, "live_channells", [CustomizationItem(item_id=1)])
        conn.execute.assert_not_called()

    def test_validation_happens_before_any_query(self, service):
        with fake_db(service) as conn:
            with pytest.raises(ValueError):
                service.overrides(1, "nope")
        conn.execute.assert_not_called()

    @pytest.mark.parametrize("kind", list(KINDS))
    def test_every_kind_round_trips_through_validation(self, service, kind):
        assert CustomizeService._validate_kind(kind) == kind


class TestOverridesRead:
    def test_read_is_scoped_to_profile_and_kind(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = []
            service.overrides(42, "live_channel")

        query, params = conn.execute.call_args[0]
        assert "FROM tavuno_customizations" in query
        assert "profile = %s AND kind = %s" in query
        assert params == (42, "live_channel")

    def test_rows_are_folded_into_overrides(self, service):
        rows = [
            {"item_id": 5, "sort_order": 2, "is_hidden": False},
            {"item_id": 9, "sort_order": 0, "is_hidden": True},
        ]
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = rows
            overrides = service.overrides(42, "live_channel")

        assert overrides.order == {5: 2}
        assert overrides.hidden == {9}
        assert not overrides.is_empty

    def test_a_profile_with_no_rows_gets_empty_overrides(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = []
            overrides = service.overrides(42, "live_channel")

        assert overrides.is_empty


class TestSave:
    def test_save_deletes_the_kind_then_inserts(self, service):
        with fake_db(service) as conn:
            service.save(42, "live_channel", [CustomizationItem(item_id=5, sort_order=1)])

        delete_query, delete_params = conn.execute.call_args_list[0][0]
        assert delete_query.startswith("DELETE FROM tavuno_customizations")
        assert delete_params == (42, "live_channel")

        insert_query, insert_params = conn.execute.call_args_list[1][0]
        assert insert_query.startswith("INSERT INTO tavuno_customizations")
        assert insert_params == (42, "live_channel", 5, 1, False)
        conn.commit.assert_called_once()

    def test_save_with_an_empty_payload_clears_the_kind(self, service):
        with fake_db(service) as conn:
            result = service.save(42, "live_channel", [])

        assert conn.execute.call_count == 1  # the DELETE only
        assert result.items == []

    def test_duplicate_ids_collapse_with_last_winning(self, service):
        with fake_db(service) as conn:
            result = service.save(42, "live_channel", [
                CustomizationItem(item_id=5, sort_order=1),
                CustomizationItem(item_id=5, sort_order=9, is_hidden=True),
            ])

        assert len(result.items) == 1
        assert result.items[0].sort_order == 9
        assert result.items[0].is_hidden is True
        # One delete + one insert, not one insert per submitted row.
        assert conn.execute.call_count == 2

    def test_save_echoes_back_the_stored_set(self, service):
        with fake_db(service):
            result = service.save(42, "live_channel", [CustomizationItem(item_id=5, sort_order=3)])

        assert result.kind == "live_channel"
        assert result.items == [CustomizationItem(item_id=5, sort_order=3, is_hidden=False)]

    def test_saving_a_different_kind_leaves_the_other_alone(self, service):
        with fake_db(service) as conn:
            service.save(42, "movie_category", [CustomizationItem(item_id=1)])

        _, delete_params = conn.execute.call_args_list[0][0]
        assert delete_params == (42, "movie_category")


class TestReset:
    def test_reset_deletes_the_kind_and_reports_the_count(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.rowcount = 4
            removed = service.reset(42, "movie_category")

        query, params = conn.execute.call_args[0]
        assert query.startswith("DELETE FROM tavuno_customizations")
        assert params == (42, "movie_category")
        assert removed == 4
        conn.commit.assert_called_once()

    def test_reset_of_an_untouched_kind_reports_zero(self, service):
        with fake_db(service):
            assert service.reset(42, "movie_category") == 0


class TestList:
    def test_list_orders_hidden_last_then_by_sort_order(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = []
            service.list(42, "live_channel")

        query, _ = conn.execute.call_args[0]
        assert "ORDER BY is_hidden, sort_order, item_id" in query

    def test_list_maps_rows_into_items(self, service):
        rows = [
            {"item_id": 5, "sort_order": 2, "is_hidden": False},
            {"item_id": 9, "sort_order": 0, "is_hidden": True},
        ]
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = rows
            result = service.list(42, "live_channel")

        assert result.kind == "live_channel"
        assert result.items[0] == CustomizationItem(item_id=5, sort_order=2, is_hidden=False)
        assert result.items[1] == CustomizationItem(item_id=9, sort_order=0, is_hidden=True)
