"""Persistence for per-profile favourites (A6).

Modelled on :mod:`app.customize.service` — same ``services.connection()``
access, same "validate the kind, then talk to the table" discipline — but with
one deliberate difference in shape: a favourite is a **flag per item**, so the
useful read is "which of these ids are favourited", not "load the override set
and apply it". :meth:`marked` is therefore the read the catalog uses on every
grid, and it is a single indexed query against the ids already in hand.

Writes are idempotent upserts rather than the replace-all a PUT would need:
toggling a heart twice must leave one row, not an error or a duplicate.
"""

from contextlib import contextmanager
from typing import Any, Dict, Iterable, List, Optional, Set

from ..content_refs import fetch_items, order_by_input, validate_kind
from .models import FavouriteState


class FavouritesService:
    def __init__(self, services):
        self.services = services

    @contextmanager
    def _db(self):
        with self.services.connection() as connection:
            yield connection

    def marked(self, profile_id: int, kind: str, item_ids: Iterable[int]) -> Set[int]:
        """Which of ``item_ids`` this profile has favourited.

        Returns a set for O(1) membership per row: the catalog calls this once
        per grid and then projects ``item_id in marked`` onto each item, so a
        list lookup per row would be quadratic on a 12-tile rail.
        """
        validate_kind(kind)
        ids = [int(value) for value in item_ids]
        if not ids:
            return set()
        placeholders = ",".join(["%s"] * len(ids))
        with self._db() as conn:
            rows = conn.execute(
                "SELECT item_id FROM tavuno_favourites"
                f" WHERE profile = %s AND kind = %s AND item_id IN ({placeholders})",
                tuple([int(profile_id), kind] + ids),
            ).fetchall()
        return {int(row["item_id"]) for row in rows}

    def ids(self, profile_id: int, kind: Optional[str] = None) -> List[int]:
        """Favourited ids, newest first — the order the rails read them in."""
        query = "SELECT item_id FROM tavuno_favourites WHERE profile = %s"
        params: List[Any] = [int(profile_id)]
        if kind is not None:
            validate_kind(kind)
            query += " AND kind = %s"
            params.append(kind)
        query += " ORDER BY created_at DESC, id DESC"
        with self._db() as conn:
            rows = conn.execute(query, tuple(params)).fetchall()
        return [int(row["item_id"]) for row in rows]

    def list_rows(self, profile_id: int, kind: str, limit: int = 50) -> List[Dict[str, Any]]:
        """Favourited catalog rows for one kind, in favourite order."""
        validate_kind(kind)
        order = self.ids(profile_id, kind)
        if not order:
            return []
        with self._db() as conn:
            fetched = fetch_items(conn, kind, order)
        rows = order_by_input(fetched, order)
        return rows[:limit] if limit else rows

    def get(self, profile_id: int, kind: str, item_id: int) -> bool:
        """Whether one item is currently favourited."""
        return int(item_id) in self.marked(profile_id, kind, [item_id])

    def set(self, profile_id: int, kind: str, item_id: int, value: bool) -> bool:
        """Set the flag to an explicit value; returns the stored value.

        ON CONFLICT keeps a double-tap a no-op instead of a 500, and the DELETE
        branch makes unfavouriting a real removal rather than a tombstone row
        that would keep the item alive in "continue using this" queries.
        """
        validate_kind(kind)
        with self._db() as conn:
            if value:
                conn.execute(
                    "INSERT INTO tavuno_favourites (profile, kind, item_id)"
                    " VALUES (%s, %s, %s)"
                    " ON CONFLICT (profile, kind, item_id) DO NOTHING",
                    (int(profile_id), kind, int(item_id)),
                )
            else:
                conn.execute(
                    "DELETE FROM tavuno_favourites WHERE profile = %s AND kind = %s AND item_id = %s",
                    (int(profile_id), kind, int(item_id)),
                )
            conn.commit()
        return value

    def toggle(self, profile_id: int, kind: str, item_id: int) -> bool:
        """Flip the flag; returns the new value.

        The read and the write are separate statements on purpose: this is only
        ever driven by one client's own heart button, and making it atomic would
        mean a transaction that buys nothing but a deadlock risk against the
        concurrent grid reads.
        """
        current = self.get(profile_id, kind, item_id)
        return self.set(profile_id, kind, item_id, not current)

    def state(self, profile_id: int, kind: str, item_id: int) -> FavouriteState:
        """The flag plus the resolved row, for the toggle response."""
        validate_kind(kind)
        is_favourite = self.get(profile_id, kind, item_id)
        item = None
        if is_favourite:
            with self._db() as conn:
                fetched = fetch_items(conn, kind, [item_id])
            item = fetched.get(int(item_id))
        return FavouriteState(
            kind=kind,
            item_id=int(item_id),
            is_favourite=is_favourite,
            item=item,
        )