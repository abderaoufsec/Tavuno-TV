"""Viewing profiles under one account (Slice D).

Tavuno's ``tavuno_profiles`` table is the account table: M9 added
``email``/``password_hash``/``role`` to it so the row that owns a subscription can
also authenticate. The M2 schema note still describes the original intent
("customer-facing profile attached to a Directus user"), which is what this
service builds on — migration 008 adds ``owner_profile`` so one account row can
own several child rows.

The rules encoded here, and pinned by ``tests/test_profiles_service.py``:

* **The caller's own profile is always the account.** Children are addressed by
  ``owner_profile``, so an account can never see or touch another account's
  profiles even if it guesses an id — every WHERE clause carries the owner.
* **The account row is never deletable.** Deleting it would orphan a
  subscription; the endpoint is for removing extra viewers.
* **A child carries no credentials.** ``email`` is left NULL (migration 008 makes
  the column nullable precisely so two children do not collide on the UNIQUE
  index).
"""

from contextlib import contextmanager
from typing import Any, List, Optional

from .models import ProfileSummary

# Columns every profile read returns, so the mapping lives in exactly one place.
_SELECT_COLUMNS = "id, display_name, email, role, status, avatar, is_kids, owner_profile"


def _to_summary(row: dict, owner_id: int) -> ProfileSummary:
    return ProfileSummary(
        id=row["id"],
        display_name=row.get("display_name") or "",
        email=row.get("email") or None,
        role=row.get("role") or "user",
        status=row.get("status"),
        avatar=row.get("avatar"),
        is_kids=bool(row.get("is_kids")),
        is_owner=row["id"] == owner_id,
    )


class ProfilesService:
    def __init__(self, services):
        self.services = services

    @contextmanager
    def _db(self):
        with self.services.connection() as connection:
            yield connection

    @staticmethod
    def _owner_id(principal: dict) -> int:
        owner_id = principal.get("profile_id")
        if owner_id is None:
            raise ValueError("no profile on principal")
        return int(owner_id)

    def list_for(self, principal: dict) -> List[ProfileSummary]:
        """The account plus every child profile, account first then name order."""
        owner_id = self._owner_id(principal)
        with self._db() as conn:
            rows = conn.execute(
                f"SELECT {_SELECT_COLUMNS} FROM tavuno_profiles"
                " WHERE id = %s OR owner_profile = %s"
                " ORDER BY (id = %s) DESC, display_name, id",
                (owner_id, owner_id, owner_id),
            ).fetchall()
        return [_to_summary(row, owner_id) for row in rows]

    def create(
        self,
        principal: dict,
        display_name: str,
        avatar: Optional[str] = None,
        is_kids: bool = False,
    ) -> ProfileSummary:
        """Add a child profile under the caller's account."""
        owner_id = self._owner_id(principal)
        name = (display_name or "").strip()
        if not name:
            raise ValueError("display_name is required")
        with self._db() as conn:
            row = conn.execute(
                f"""
                INSERT INTO tavuno_profiles
                    (display_name, owner_profile, avatar, is_kids, role, status, password_hash, email)
                VALUES (%s, %s, %s, %s, 'user', 'active', '', NULL)
                RETURNING {_SELECT_COLUMNS}
                """,
                (name, owner_id, avatar, is_kids),
            ).fetchone()
            conn.commit()
        return _to_summary(row, owner_id)

    def update(
        self,
        principal: dict,
        profile_id: int,
        display_name: Optional[str] = None,
        avatar: Optional[str] = None,
        is_kids: Optional[bool] = None,
    ) -> ProfileSummary:
        """Edit the account row or one of its children.

        Raises:
            ValueError: if nothing was supplied, or the profile is not reachable
                from the caller's account.
        """
        owner_id = self._owner_id(principal)
        assignments: List[str] = []
        params: List[Any] = []
        if display_name is not None:
            name = display_name.strip()
            if not name:
                raise ValueError("display_name cannot be blank")
            assignments.append("display_name = %s")
            params.append(name)
        if avatar is not None:
            # "" clears the avatar rather than storing an empty string.
            assignments.append("avatar = %s")
            params.append(avatar or None)
        if is_kids is not None:
            assignments.append("is_kids = %s")
            params.append(is_kids)
        if not assignments:
            raise ValueError("nothing to update")

        params.extend([int(profile_id), owner_id, owner_id])
        with self._db() as conn:
            row = conn.execute(
                f"UPDATE tavuno_profiles SET {', '.join(assignments)}"
                " WHERE id = %s AND (id = %s OR owner_profile = %s)"
                f" RETURNING {_SELECT_COLUMNS}",
                tuple(params),
            ).fetchone()
            conn.commit()
        if row is None:
            raise ValueError("profile_not_found")
        return _to_summary(row, owner_id)

    def delete(self, principal: dict, profile_id: int) -> None:
        """Remove a child profile. The account row is not deletable here.

        Raises:
            ValueError: if the id is not a child of the caller's account.
        """
        owner_id = self._owner_id(principal)
        with self._db() as conn:
            cursor = conn.execute(
                "DELETE FROM tavuno_profiles WHERE id = %s AND owner_profile = %s",
                (int(profile_id), owner_id),
            )
            conn.commit()
        if not getattr(cursor, "rowcount", 0):
            raise ValueError("profile_not_found")