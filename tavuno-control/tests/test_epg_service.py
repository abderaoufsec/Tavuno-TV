"""Unit tests for the windowed EPG guide reads (Slice C)."""

from datetime import datetime, timedelta, timezone
from unittest.mock import MagicMock, Mock, patch

import pytest

from app.epg.service import (
    DEFAULT_CHANNEL_LIMIT,
    EpgService,
    format_timestamp,
    parse_timestamp,
)


@pytest.fixture
def mock_services():
    return Mock()


@pytest.fixture
def epg_service(mock_services):
    return EpgService(mock_services)


START = datetime(2026, 10, 1, 18, 0, tzinfo=timezone.utc)
END = datetime(2026, 10, 1, 22, 0, tzinfo=timezone.utc)


def _run(service, rows, **kwargs):
    """Call window() against a mocked connection and return (result, connection)."""
    connection = MagicMock()
    connection.execute.return_value.fetchall.return_value = rows
    kwargs.setdefault("window_start", START)
    kwargs.setdefault("window_end", END)
    with patch.object(service, "_db") as mock_db:
        mock_db.return_value.__enter__.return_value = connection
        result = service.window(**kwargs)
    return result, connection


def _row(**overrides):
    base = {
        "channel_id": 1,
        "channel_name": "News 24",
        "channel_slug": "news-24",
        "channel_category": 5,
        "channel_logo": None,
        "programme_id": 11,
        "programme_title": "Bulletin",
        "starts_at": datetime(2026, 10, 1, 18, 0, tzinfo=timezone.utc),
        "ends_at": datetime(2026, 10, 1, 18, 30, tzinfo=timezone.utc),
        "description": None,
    }
    base.update(overrides)
    return base


class TestTimestampHelpers:
    def test_parses_z_suffix(self):
        assert parse_timestamp("2026-10-01T18:00:00Z") == START

    def test_parses_offset_suffix(self):
        assert parse_timestamp("2026-10-01T20:00:00+02:00") == START

    def test_parses_legacy_space_separator(self):
        assert parse_timestamp("2026-10-01 18:00:00+00:00") == START

    def test_naive_value_is_read_as_utc(self):
        assert parse_timestamp("2026-10-01T18:00:00") == START

    def test_empty_value_is_rejected(self):
        with pytest.raises(ValueError):
            parse_timestamp("   ")

    def test_garbage_is_rejected(self):
        with pytest.raises(ValueError):
            parse_timestamp("not-a-time")

    def test_format_renders_the_z_suffix_the_rest_of_the_api_uses(self):
        assert format_timestamp(START) == "2026-10-01T18:00:00Z"


class TestWindowValidation:
    def test_inverted_window_is_rejected(self, epg_service):
        with pytest.raises(ValueError):
            epg_service.window(window_start=END, window_end=START)

    def test_empty_window_is_rejected(self, epg_service):
        with pytest.raises(ValueError):
            epg_service.window(window_start=START, window_end=START)

    def test_window_wider_than_the_cap_is_rejected(self, epg_service):
        too_wide = START + timedelta(hours=25)
        with pytest.raises(ValueError):
            epg_service.window(window_start=START, window_end=too_wide)

    def test_validation_happens_before_any_query(self, epg_service):
        connection = MagicMock()
        with patch.object(epg_service, "_db") as mock_db:
            mock_db.return_value.__enter__.return_value = connection
            with pytest.raises(ValueError):
                epg_service.window(window_start=END, window_end=START)
        connection.execute.assert_not_called()


class TestWindowQuery:
    def test_selects_channels_first_then_left_joins_programmes(self, epg_service):
        _, connection = _run(epg_service, [])

        query, params = connection.execute.call_args[0]
        assert query.startswith("WITH guide_channels AS (")
        assert "ORDER BY name LIMIT %s)" in query
        assert "LEFT JOIN tavuno_epg_channels" in query
        assert "LEFT JOIN tavuno_epg_programmes" in query
        # The window predicate must sit in the JOIN's ON clause, not WHERE: a
        # WHERE filter would discard channels that simply have no synced EPG.
        assert "AND p.starts_at < %s AND p.ends_at > %s" in query
        assert "WHERE is_active = TRUE AND" not in query
        # window_end then window_start, last.
        assert params[-2:] == (END, START)

    def test_defaults_to_the_standard_channel_cap(self, epg_service):
        _, connection = _run(epg_service, [])

        _, params = connection.execute.call_args[0]
        assert params == (DEFAULT_CHANNEL_LIMIT, END, START)

    def test_channel_ids_filter_is_appended_first(self, epg_service):
        _, connection = _run(epg_service, [], channel_ids=[1, 2, 3])

        query, params = connection.execute.call_args[0]
        assert "id = ANY(%s)" in query
        assert params == ([1, 2, 3], DEFAULT_CHANNEL_LIMIT, END, START)

    def test_category_filter_is_applied(self, epg_service):
        _, connection = _run(epg_service, [], category_id=5)

        query, params = connection.execute.call_args[0]
        assert "category = %s" in query
        assert params == (5, DEFAULT_CHANNEL_LIMIT, END, START)

    def test_explicit_limit_overrides_the_default(self, epg_service):
        _, connection = _run(epg_service, [], limit=7)

        _, params = connection.execute.call_args[0]
        assert params == (7, END, START)

    def test_live_channel_scope_is_applied_and_its_limit_caps_the_rows(self, epg_service):
        epg_service.services.settings.live_channel_allowlist = "8845,news-24"
        epg_service.services.settings.live_channel_limit = 3

        _, connection = _run(epg_service, [])

        query, params = connection.execute.call_args[0]
        assert "slug = ANY(%s)" in query
        assert "id = ANY(%s)" in query
        # allowlist ids, then the test-scope cap, then the window bounds.
        assert params == ([8845], ["news-24"], 3, END, START)

    def test_explicit_limit_still_wins_over_the_test_scope_cap(self, epg_service):
        epg_service.services.settings.live_channel_allowlist = "8845"
        epg_service.services.settings.live_channel_limit = 3

        _, connection = _run(epg_service, [], limit=9)

        _, params = connection.execute.call_args[0]
        assert params == ([8845], 9, END, START)

    def test_window_bounds_are_echoed_as_iso_z(self, epg_service):
        result, _ = _run(epg_service, [])

        assert result.window_start == "2026-10-01T18:00:00Z"
        assert result.window_end == "2026-10-01T22:00:00Z"

    def test_naive_window_bounds_are_treated_as_utc(self, epg_service):
        result, _ = _run(
            epg_service,
            [],
            window_start=datetime(2026, 10, 1, 18, 0),
            window_end=datetime(2026, 10, 1, 22, 0),
        )

        assert result.window_start == "2026-10-01T18:00:00Z"
        assert result.window_end == "2026-10-01T22:00:00Z"
class TestWindowGrouping:
    def test_rows_are_folded_into_one_row_per_channel(self, epg_service):
        rows = [
            _row(programme_id=11, programme_title="Bulletin"),
            _row(programme_id=12, programme_title="Weather",
                 starts_at=datetime(2026, 10, 1, 18, 30, tzinfo=timezone.utc),
                 ends_at=datetime(2026, 10, 1, 19, 0, tzinfo=timezone.utc)),
        ]

        result, _ = _run(epg_service, rows)

        assert len(result.channels) == 1
        channel = result.channels[0]
        assert channel.id == 1
        assert channel.name == "News 24"
        assert [p.title for p in channel.programmes] == ["Bulletin", "Weather"]
        assert channel.programmes[0].channel_id == 1

    def test_a_channel_without_synced_epg_keeps_an_empty_strip(self, epg_service):
        rows = [_row(programme_id=None, programme_title=None, starts_at=None, ends_at=None)]

        result, _ = _run(epg_service, rows)

        assert len(result.channels) == 1
        assert result.channels[0].programmes == []

    def test_two_channels_keep_their_own_programmes_and_order(self, epg_service):
        rows = [
            _row(channel_id=1, channel_name="Alpha", programme_id=11),
            _row(channel_id=1, channel_name="Alpha", programme_id=12),
            _row(channel_id=2, channel_name="Bravo", channel_slug="bravo",
                 programme_id=21, programme_title="Film"),
        ]

        result, _ = _run(epg_service, rows)

        assert [c.name for c in result.channels] == ["Alpha", "Bravo"]
        assert [p.id for p in result.channels[0].programmes] == [11, 12]
        assert [p.id for p in result.channels[1].programmes] == [21]

    def test_logo_uuid_is_stringified(self, epg_service):
        rows = [_row(channel_logo="123e4567-e89b-12d3-a456-426614174000")]

        result, _ = _run(epg_service, rows)

        assert result.channels[0].logo == "123e4567-e89b-12d3-a456-426614174000"

    def test_absent_logo_stays_none(self, epg_service):
        result, _ = _run(epg_service, [_row(channel_logo=None)])

        assert result.channels[0].logo is None