"""Favourites API models (A6).

Deliberately thin: the request carries the same ``(kind, item_id)`` pair the
storage is keyed on, and the response echoes the resolved item back so a client
that just toggled a heart does not need a second round trip to render it.
"""

from pydantic import BaseModel, Field
from typing import Optional, List, Any, Dict


class FavouriteRef(BaseModel):
    """A (kind, item_id) pointer to one favouritable piece of content."""

    kind: str = Field(min_length=1, max_length=32)
    item_id: int


class FavouriteState(BaseModel):
    """The stored flag for one item.

    ``is_favourite`` is what catalog reads project onto every item; ``item`` is
    the resolved entity, present only on the toggle/list responses so a client
    can render the change immediately without re-reading the grid.
    """

    kind: str
    item_id: int
    is_favourite: bool
    item: Optional[Dict[str, Any]] = None


class FavouriteToggle(BaseModel):
    """Toggle request. Omit ``is_favourite`` to flip the current value."""

    kind: str = Field(min_length=1, max_length=32)
    item_id: int
    is_favourite: Optional[bool] = None


class FavouriteList(BaseModel):
    """Every favourite of one kind for one profile, newest first."""

    kind: str
    items: List[Dict[str, Any]] = Field(default_factory=list)