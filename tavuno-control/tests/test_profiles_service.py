"""Unit tests for the viewing-profiles service (Slice D).

The rules under test are the ones a wrong query would silently break: every read
and write is scoped to the caller's account, the account row itself cannot be
deleted, and a child profile never gets credentials of its own.
"""

from contextlib import contextmanager
from unittest.mock import MagicMock, patch

import pytest

from app.profiles.models import ProfileSummary
from app.profiles.service import ProfilesService

OWNER = {"profile_id": 42, "role": "user"}


@contextmanager
def fake_db(service: ProfilesService):
    """Replace the service's connection with a MagicMock for the block."""
    connection = MagicMock()
    connection.execute.return_value.rowcount = 1
    with patch.object(service, "_db") as mock_db:
        mock_db.return_value.__enter__.return_value = connection
        yield connection


@pytest.fixture
def service() -> ProfilesService:
    return ProfilesService(MagicMock())


def summary_row(profile_id: int = 42, **overrides) -> dict:
    row = {
        "id": profile_id,
        "display_name": "Ben",
        "email": None,
        "role": "user",
        "status": "active",
        "avatar": None,
        "is_kids": False,
        "owner_profile": 42,
    }
    row.update(overrides)
    return row


class TestOwnerResolution:
    def test_principal_without_a_profile_is_rejected(self, service):
        with pytest.raises(ValueError, match="no profile on principal"):
            service.list_for({"role": "user"})


class TestList:
    def test_list_is_scoped_to_the_owning_account(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = []
            service.list_for(OWNER)

        query, params = conn.execute.call_args[0]
        assert "FROM tavuno_profiles" in query
        assert "WHERE id = %s OR owner_profile = %s" in query
        assert params == (42, 42, 42)

    def test_the_account_row_sorts_first_then_children_by_name(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = []
            service.list_for(OWNER)

        query, _ = conn.execute.call_args[0]
        assert "ORDER BY (id = %s) DESC, display_name, id" in query

    def test_rows_become_summaries_with_the_owner_flagged(self, service):
        rows = [
            summary_row(42, display_name="Ben"),
            summary_row(7, display_name="Mila", owner_profile=42),
        ]
        with fake_db(service) as conn:
            conn.execute.return_value.fetchall.return_value = rows
            profiles = service.list_for(OWNER)

        assert [p.id for p in profiles] == [42, 7]
        assert profiles[0].is_owner is True
        assert profiles[1].is_owner is False
        assert all(isinstance(p, ProfileSummary) for p in profiles)


class TestCreate:
    def test_create_requires_a_name(self, service):
        with pytest.raises(ValueError, match="display_name is required"):
            service.create(OWNER, "   ")

    def test_create_strips_the_name_and_owns_the_child(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchone.return_value = summary_row(7, display_name="Mila")
            created = service.create(OWNER, "  Mila  ", avatar="a.png", is_kids=True)

        query, params = conn.execute.call_args[0]
        assert "INSERT INTO tavuno_profiles" in query
        assert params == ("Mila", 42, "a.png", True)
        assert created.display_name == "Mila"
        conn.commit.assert_called_once()

    def test_a_child_is_created_without_credentials(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchone.return_value = summary_row(7)
            service.create(OWNER, "Mila")

        query, _ = conn.execute.call_args[0]
        assert "email)" in query
        assert "NULL" in query  # the UNIQUE(email) index must not collide


class TestUpdate:
    def test_update_with_nothing_supplied_is_rejected(self, service):
        with pytest.raises(ValueError, match="nothing to update"):
            service.update(OWNER, 7)

    def test_update_with_a_blank_name_is_rejected(self, service):
        with pytest.raises(ValueError, match="cannot be blank"):
            service.update(OWNER, 7, display_name="   ")

    def test_update_is_scoped_to_the_owning_account(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchone.return_value = summary_row(7)
            service.update(OWNER, 7, display_name="Mila")

        query, params = conn.execute.call_args[0]
        assert "WHERE id = %s AND (id = %s OR owner_profile = %s)" in query
        assert params == ("Mila", 7, 42, 42)

    def test_an_unreachable_profile_raises_not_found(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchone.return_value = None
            with pytest.raises(ValueError, match="profile_not_found"):
                service.update(OWNER, 999, display_name="Nope")

    def test_empty_avatar_clears_it_instead_of_storing_a_blank(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.fetchone.return_value = summary_row(7)
            service.update(OWNER, 7, avatar="")

        _, params = conn.execute.call_args[0]
        assert params[0] is None


class TestDelete:
    def test_delete_targets_only_a_child_of_this_account(self, service):
        with fake_db(service) as conn:
            service.delete(OWNER, 7)

        query, params = conn.execute.call_args[0]
        assert query.startswith("DELETE FROM tavuno_profiles")
        assert "AND owner_profile = %s" in query
        assert params == (7, 42)
        conn.commit.assert_called_once()

    def test_deleting_an_unknown_profile_raises_not_found(self, service):
        with fake_db(service) as conn:
            conn.execute.return_value.rowcount = 0
            with pytest.raises(ValueError, match="profile_not_found"):
                service.delete(OWNER, 999)

    def test_the_account_row_is_not_deletable(self, service):
        # The account row has owner_profile = NULL, so the owner-scoped DELETE
        # cannot match it; the service therefore reports not-found rather than
        # removing the row that holds the subscription.
        with fake_db(service) as conn:
            conn.execute.return_value.rowcount = 0
            with pytest.raises(ValueError, match="profile_not_found"):
                service.delete(OWNER, 42)

        _, params = conn.execute.call_args[0]
        assert params == (42, 42)
