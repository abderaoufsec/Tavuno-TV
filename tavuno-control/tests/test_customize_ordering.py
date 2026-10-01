"""Unit tests for profile customization: pure ordering (Slice D)."""

from types import SimpleNamespace

from app.customize.ordering import Overrides, apply_overrides, build_overrides


def item(item_id: int) -> SimpleNamespace:
    return SimpleNamespace(id=item_id)


class TestBuildOverrides:
    def test_no_rows_is_empty(self):
        overrides = build_overrides([])

        assert overrides.is_empty
        assert overrides.order == {}
        assert overrides.hidden == set()

    def test_pinned_row_lands_in_the_order_map(self):
        overrides = build_overrides([{"item_id": 7, "sort_order": 2, "is_hidden": False}])

        assert overrides.order == {7: 2}
        assert overrides.hidden == set()
        assert not overrides.is_empty

    def test_hidden_row_lands_in_the_hidden_set(self):
        overrides = build_overrides([{"item_id": 7, "sort_order": 0, "is_hidden": True}])

        assert overrides.hidden == {7}
        assert overrides.order == {}

    def test_hidden_wins_over_order(self):
        overrides = build_overrides([{"item_id": 7, "sort_order": 5, "is_hidden": True}])

        assert 7 in overrides.hidden
        assert 7 not in overrides.order

    def test_missing_sort_order_defaults_to_zero(self):
        overrides = build_overrides([{"item_id": 7, "is_hidden": False}])

        assert overrides.order == {7: 0}


class TestApplyOverrides:
    def test_no_overrides_returns_the_list_unchanged(self):
        items = [item(1), item(2), item(3)]

        result = apply_overrides(items, Overrides())

        assert result == items
        assert [i.id for i in result] == [1, 2, 3]

    def test_hidden_items_are_removed(self):
        items = [item(1), item(2), item(3)]

        result = apply_overrides(items, Overrides(hidden={2}))

        assert [i.id for i in result] == [1, 3]

    def test_all_hidden_yields_an_empty_list(self):
        result = apply_overrides([item(1), item(2)], Overrides(hidden={1, 2}))

        assert result == []

    def test_pinned_items_float_to_the_front_sorted_by_key(self):
        items = [item(1), item(2), item(3), item(4)]

        # Pin 3 first, then 1: the rest keep their original relative order.
        result = apply_overrides(items, Overrides(order={1: 10, 3: 5}))

        assert [i.id for i in result] == [3, 1, 2, 4]

    def test_unpinned_items_keep_their_relative_order_after_the_pinned_block(self):
        items = [item(4), item(2), item(5), item(9)]

        result = apply_overrides(items, Overrides(order={9: 0}))

        assert [i.id for i in result] == [9, 4, 2, 5]

    def test_pin_and_hide_combine(self):
        items = [item(1), item(2), item(3), item(4)]

        result = apply_overrides(items, Overrides(order={2: 0, 4: 1}, hidden={3}))

        assert [i.id for i in result] == [2, 4, 1]

    def test_override_ids_that_match_nothing_are_ignored(self):
        items = [item(1), item(2)]

        result = apply_overrides(items, Overrides(order={999: 0}, hidden={888}))

        assert [i.id for i in result] == [1, 2]

    def test_items_missing_an_id_are_never_matched(self):
        class NoId:
            pass

        result = apply_overrides([NoId()], Overrides(order={1: 0}, hidden={2}))

        assert len(result) == 1

    def test_returns_a_new_list_so_callers_cannot_mutate_the_input(self):
        items = [item(1), item(2)]

        result = apply_overrides(items, Overrides(hidden={1}))

        assert result is not items
        assert len(items) == 2