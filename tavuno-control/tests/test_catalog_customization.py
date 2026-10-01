"""Tests that profile customization is actually applied on catalog reads (Slice D).

``CatalogService`` gained an optional ``profile_id`` on the read paths a profile
can personalize. These pin the two things that are easy to regress: the lookup is
skipped entirely for a profile-less caller (so behaviour is identical to before
the feature), and each read asks for the right customization kind.
"""

from contextlib import contextmanager
from unittest.mock import MagicMock, Mock, patch

import pytest

from app.catalog.service import CatalogService
from app.customize.ordering import Overrides


class Item:
    """Minimal stand-in with the ``id`` attribute apply_overrides matches on."""

    def __init__(self, item_id: int) -> None:
        self.id = item_id


@pytest.fixture
def service() -> CatalogService:
    return CatalogService(Mock())


@contextmanager
def fake_db(service: CatalogService):
    connection = MagicMock()
    connection.execute.return_value.fetchall.return_value = []
    with patch.object(service, "_db") as mock_db:
        mock_db.return_value.__enter__.return_value = connection
        yield connection


@contextmanager
def stubbed_overrides(overrides: Overrides):
    """Replace CustomizeService with a stub returning ``overrides``."""
    cls = Mock()
    cls.return_value.overrides.return_value = overrides
    with patch("app.catalog.service.CustomizeService", cls):
        yield cls


def channel_rows(*ids: int) -> list:
    """Row dicts satisfying both the channel and category row mappers."""
    return [
        {
            "id": i,
            "name": f"Channel {i}",
            "slug": f"channel-{i}",
            "category": 3,
            "kind": "live",
            "parent": None,
            "sort_order": i,
            "logo": None,
            "is_active": True,
        }
        for i in ids
    ]


class TestOverrideLookup:
    def test_a_caller_without_a_profile_never_hits_the_customization_table(self, service):
        with stubbed_overrides(Overrides()) as cls:
            result = service._apply_profile_overrides([Item(1), Item(2)], None, "live_channel")

        cls.assert_not_called()
        assert [i.id for i in result] == [1, 2]

    def test_the_lookup_is_scoped_to_profile_and_kind(self, service):
        with stubbed_overrides(Overrides()) as cls:
            service._apply_profile_overrides([Item(1)], 7, "live_category")

        cls.assert_called_once_with(service.services)
        cls.return_value.overrides.assert_called_once_with(7, "live_category")

    def test_an_empty_override_set_is_a_no_op(self, service):
        items = [Item(1), Item(2)]
        with stubbed_overrides(Overrides()):
            result = service._apply_profile_overrides(items, 7, "live_channel")

        assert [i.id for i in result] == [1, 2]


class TestChannelsRead:
    def test_hidden_channels_are_dropped(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2, 3)
            with stubbed_overrides(Overrides(hidden={2})):
                channels = service.get_channels(profile_id=7)

        assert [c.id for c in channels] == [1, 3]

    def test_pinned_channels_float_to_the_front(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2, 3)
            with stubbed_overrides(Overrides(order={3: 0})):
                channels = service.get_channels(profile_id=7)

        assert [c.id for c in channels] == [3, 1, 2]

    def test_without_a_profile_the_natural_order_survives(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2, 3)
            with stubbed_overrides(Overrides(order={3: 0}, hidden={1})):
                channels = service.get_channels()

        assert [c.id for c in channels] == [1, 2, 3]


class TestCategoriesRead:
    @pytest.mark.parametrize(
        ("kind", "expected"),
        [("live", "live_category"), ("movie", "movie_category"), ("series", "series_category")],
    )
    def test_each_catalog_kind_asks_for_its_own_customization_kind(self, service, kind, expected):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2)
            with stubbed_overrides(Overrides()) as cls:
                service.get_categories(kind=kind, profile_id=7)

        cls.return_value.overrides.assert_called_once_with(7, expected)

    def test_an_unscoped_category_read_applies_nothing(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2)
            with stubbed_overrides(Overrides(hidden={1})) as cls:
                categories = service.get_categories(profile_id=7)

        cls.return_value.overrides.assert_not_called()
        assert [c.id for c in categories] == [1, 2]

    def test_hidden_categories_are_dropped(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2)
            with stubbed_overrides(Overrides(hidden={1})):
                categories = service.get_categories(kind="live", profile_id=7)

        assert [c.id for c in categories] == [2]


class TestHomeRead:
    def test_featured_channels_carry_the_profile_order(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2, 3)
            with stubbed_overrides(Overrides(order={2: 0}, hidden={3})):
                home = service.get_home(profile_id=7)

        assert [c.id for c in home["featured_channels"]] == [2, 1]

    def test_home_without_a_profile_ignores_the_overrides(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = channel_rows(1, 2, 3)
            with stubbed_overrides(Overrides(order={2: 0}, hidden={3})) as cls:
                home = service.get_home()

        cls.return_value.overrides.assert_not_called()
        assert [c.id for c in home["featured_channels"]] == [1, 2, 3]
