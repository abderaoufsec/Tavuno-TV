"""Persistence for per-profile catalog customization (Slice D).

Reads are cheap and always scoped to ``(profile, kind)``, which the migration
indexes. Writes are **replace-all**: a PUT for a kind deletes that profile's rows
for the kind and inserts the payload. That contract is deliberate — the client
always sends the complete list it just rendered, so a merge would make a removed
pin impossible to express — and it keeps the write a single delete + N inserts
rather than a diff.

The decision half (what order results, what is hidden) lives in
:mod:`app.customize.ordering` so it can be tested without a database.
"""

from contextlib import contextmanager
from typing import Any, List, Sequence

from .models import CustomizationItem, CustomizationSet
from .ordering import Overrides, build_overrides

# The kinds a profile may customize. Validated here so a typo cannot silently
# create rows nothing ever reads.
KINDS = ("live_channel", "live_category", "movie_category", "series_category")


class CustomizeService:
    def __init__(self, services):
        self.services = services

    @contextmanager
    def _db(self):
        with self.services.connection() as connection:
            yield connection

    @staticmethod
    def _validate_kind(kind: str) -> str:
        if kind not in KINDS:
            raise ValueError(f"unknown customization kind: {kind!r}")
        return kind

    def overrides(self, profile_id: int, kind: str) -> Overrides:
        """The profile's overrides for one kind, ready for the catalog to apply."""
        self._validate_kind(kind)
        with self._db() as conn:
            rows = conn.execute(
                "SELECT item_id, sort_order, is_hidden FROM tavuno_customizations"
                " WHERE profile = %s AND kind = %s",
                (profile_id, kind),
            ).fetchall()
        return build_overrides(rows)

    def list(self, profile_id: int, kind: str) -> CustomizationSet:
        """Return the stored overrides for one kind, hidden last then by order."""
        self._validate_kind(kind)
        with self._db() as conn:
            rows = conn.execute(
                "SELECT item_id, sort_order, is_hidden FROM tavuno_customizations"
                " WHERE profile = %s AND kind = %s"
                " ORDER BY is_hidden, sort_order, item_id",
                (profile_id, kind),
            ).fetchall()
        return CustomizationSet(
            kind=kind,
            items=[
                CustomizationItem(
                    item_id=int(row["item_id"]),
                    sort_order=int(row.get("sort_order") or 0),
                    is_hidden=bool(row.get("is_hidden")),
                )
                for row in rows
            ],
        )

    def save(self, profile_id: int, kind: str, items: Sequence[CustomizationItem]) -> CustomizationSet:
        """Replace this profile's overrides for one kind with ``items``.

        Duplicate ids in the payload are collapsed (last wins), because the table
        is keyed on ``(profile, kind, item_id)`` and a client-side double-tap
        should not surface as a database error.
        """
        self._validate_kind(kind)
        deduped: dict = {}
        for item in items:
            deduped[int(item.item_id)] = CustomizationItem(
                item_id=int(item.item_id),
                sort_order=int(item.sort_order),
                is_hidden=bool(item.is_hidden),
            )

        with self._db() as conn:
            conn.execute(
                "DELETE FROM tavuno_customizations WHERE profile = %s AND kind = %s",
                (profile_id, kind),
            )
            for item in deduped.values():
                conn.execute(
                    "INSERT INTO tavuno_customizations"
                    " (profile, kind, item_id, sort_order, is_hidden, updated_at)"
                    " VALUES (%s, %s, %s, %s, %s, NOW())",
                    (profile_id, kind, item.item_id, item.sort_order, item.is_hidden),
                )
            conn.commit()

        return CustomizationSet(kind=kind, items=list(deduped.values()))

    def reset(self, profile_id: int, kind: str) -> int:
        """Drop every override for one kind; returns how many rows were removed."""
        self._validate_kind(kind)
        with self._db() as conn:
            cursor = conn.execute(
                "DELETE FROM tavuno_customizations WHERE profile = %s AND kind = %s",
                (profile_id, kind),
            )
            conn.commit()
        return int(getattr(cursor, "rowcount", 0) or 0)