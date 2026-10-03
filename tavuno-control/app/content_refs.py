"""Favourites and resume both reference catalog content the same way.

A favourite ("heart this movie") and a resume position ("you were 40 minutes
into it") are the same pointer at different moments: a ``kind`` naming which
table the id belongs to plus the id itself. Resolving that pointer to a row —
and back into the Pydantic shape the catalog already returns — therefore has to
be one implementation, or the two features would disagree about what "movie 7"
is.

Keeping it here also puts the allowlist in one place. ``kind`` is a TEXT tag in
the database (see ``migrations/009``), which is only safe because the service
never interpolates it into SQL unvalidated: :data:`KIND_TABLES` is the single
place a kind can be added, and the table name is looked up from it rather than
formatted from user input.
"""

from __future__ import annotations

from typing import Any, Dict, Iterable, List, Optional, Tuple

# kind -> catalog table. The column each id is matched against is the table's
# primary key; ordering is applied per rail, not here.
KIND_TABLES: Dict[str, str] = {
    "channel": "tavuno_channels",
    "movie": "tavuno_movies",
    "series": "tavuno_series",
    "episode": "tavuno_episodes",
}


def validate_kind(kind: str) -> str:
    """Return ``kind`` if it names a known table, else raise.

    Raised before any SQL is built. The table name used downstream comes from
    this dict's *values*, never from the caller's string, so this check is what
    keeps a favourite of ``kind="channels; DROP TABLE"`` from ever reaching an
    interpolated query.
    """
    if kind not in KIND_TABLES:
        raise ValueError(f"unknown content kind: {kind!r}")
    return kind


def table_for(kind: str) -> str:
    """The catalog table backing ``kind`` (validated)."""
    return KIND_TABLES[validate_kind(kind)]


def fetch_items(
    connection: Any,
    kind: str,
    item_ids: Iterable[int],
    *,
    active_only: bool = True,
) -> Dict[int, Dict[str, Any]]:
    """Load the given ids of one kind, keyed by id.

    Missing ids are simply absent from the result: a favourite of a row that has
    since been deactivated should disappear from the rail rather than 404, so
    callers treat "not found" as "not listed".

    ``is_active`` filtering matches every other catalog read. A favourite is a
    pointer into the catalog, and once the catalog stops listing the item the
    pointer is not worth rendering.
    """
    table = table_for(kind)
    ids = [int(value) for value in item_ids]
    if not ids:
        return {}

    placeholders = ",".join(["%s"] * len(ids))
    query = f"SELECT * FROM {table} WHERE id IN ({placeholders})"
    if active_only:
        query += " AND is_active = TRUE"
    rows = connection.execute(query, tuple(ids)).fetchall()

    # `is_active` is not present on every table the dict maps (episodes and
    # seasons carry it, but this guard keeps a future kind from breaking the
    # query), so filter in Python instead of trusting the column.
    if active_only:
        rows = [row for row in rows if row.get("is_active", True)]

    ordered: Dict[int, Dict[str, Any]] = {}
    for row in rows:
        ordered[int(row["id"])] = dict(row)
    return ordered


def order_by_input(
    fetched: Dict[int, Dict[str, Any]],
    order: Iterable[int],
) -> List[Dict[str, Any]]:
    """Return the fetched rows in the caller's order, dropping unknown ids."""
    return [fetched[int(item_id)] for item_id in order if int(item_id) in fetched]


def first_present(order: Iterable[int], fetched: Dict[int, Dict[str, Any]]) -> Optional[int]:
    """The first id that actually resolved, or ``None``.

    "Continue watching" must not answer with a list of rows the catalog no
    longer lists; this is how a caller decides whether its rail is worth drawing
    at all.
    """
    for item_id in order:
        if int(item_id) in fetched:
            return int(item_id)
    return None