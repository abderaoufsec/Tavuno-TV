"""Pure ordering/visibility logic for profile customization (Slice D).

Split out from the service so the *decision* — which items a profile pinned,
which it hid, and what order results — is unit-testable without a database. The
service only supplies the rows; this module owns the semantics, and the catalog
applies them through :func:`apply_overrides` so a hidden channel is hidden on
every read that can reach it.
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List, Sequence, Set


@dataclass(frozen=True)
class Overrides:
    """A profile's overrides for one kind, in the shape the catalog consumes.

    ``order`` maps item id -> sort key for pinned items (lower sorts first);
    ``hidden`` is the set of ids the profile has hidden. An item in neither
    keeps the catalog's natural ordering.
    """

    order: Dict[int, int] = field(default_factory=dict)
    hidden: Set[int] = field(default_factory=set)

    @property
    def is_empty(self) -> bool:
        """True when the profile has expressed no preference for this kind."""
        return not self.order and not self.hidden


def build_overrides(rows: Sequence[dict]) -> Overrides:
    """Fold ``tavuno_customizations`` rows into an :class:`Overrides`.

    Hidden wins over order: a row that is both pinned and hidden hides the item
    (hiding is the stronger statement of intent, and the alternative would be an
    item that is invisible yet consumes a sort slot).
    """
    order: Dict[int, int] = {}
    hidden: Set[int] = set()
    for row in rows:
        item_id = int(row["item_id"])
        if row.get("is_hidden"):
            hidden.add(item_id)
            order.pop(item_id, None)
            continue
        order[item_id] = int(row.get("sort_order") or 0)
    return Overrides(order=order, hidden=hidden)


def apply_overrides(items: List[Any], overrides: Overrides) -> List[Any]:
    """Return ``items`` reordered and filtered by ``overrides``.

    Semantics, chosen to make an untouched profile a no-op:

    * no overrides at all -> the list is returned unchanged (same objects, same
      order), so behaviour is byte-identical to before this feature existed;
    * hidden items are removed;
    * pinned items come first, sorted by their ``sort_order`` key;
    * every other item keeps its original relative position, after the pinned
      block.

    Items are matched by their ``id`` attribute, which every catalog model has.
    """
    if overrides.is_empty:
        return list(items)

    visible = [item for item in items if getattr(item, "id", None) not in overrides.hidden]
    if not overrides.order:
        return visible

    pinned = [item for item in visible if getattr(item, "id", None) in overrides.order]
    unpinned = [item for item in visible if getattr(item, "id", None) not in overrides.order]
    pinned.sort(key=lambda item: overrides.order[getattr(item, "id")])
    return pinned + unpinned