"""Customization API models (Slice D)."""

from pydantic import BaseModel, Field
from typing import List


class CustomizationItem(BaseModel):
    """One override: pin ``item_id`` at ``sort_order`` and/or hide it."""

    item_id: int
    sort_order: int = 0
    is_hidden: bool = False


class CustomizationPayload(BaseModel):
    """The full override set for one kind — a PUT replaces, it never merges."""

    items: List[CustomizationItem] = Field(default_factory=list)


class CustomizationSet(BaseModel):
    """The stored overrides for one kind."""

    kind: str
    items: List[CustomizationItem] = Field(default_factory=list)