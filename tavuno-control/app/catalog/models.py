"""Catalog domain models (M11).

These models provide a normalized Tavuno API boundary between external providers
(Dispatcharr) and clients (Android, web, etc.). They expose only the fields Tavuno
actually needs, without leaking provider-specific internals.

Database schema mapping:
- tavuno_channels: id, name, slug, category (FK), logo (UUID), is_active
- tavuno_categories: id, name, kind, parent (FK), sort_order, is_active
- tavuno_movies: id, title, slug, category (FK), synopsis, release_year, is_active
- tavuno_series: id, title, slug, category (FK), synopsis, is_active
"""

from pydantic import BaseModel, Field
from typing import Optional


class Channel(BaseModel):
    """Normalized Tavuno channel model.

    Maps to tavuno_channels table in the database.
    """
    id: int
    name: str = Field(min_length=1, max_length=160)
    slug: str = Field(min_length=1, max_length=160)
    category_id: Optional[int] = None
    logo: Optional[str] = None  # UUID as string for JSON serialization
    is_active: bool = True


class Category(BaseModel):
    """Normalized Tavuno category model.

    Maps to tavuno_categories table in the database.
    """
    id: int
    name: str = Field(min_length=1, max_length=120)
    kind: str = Field(min_length=1, max_length=32)  # e.g., "live", "movie", "series", "sports"
    parent_id: Optional[int] = None
    sort_order: int = 0
    is_active: bool = True


class Movie(BaseModel):
    """Normalized Tavuno movie model.

    Maps to tavuno_movies table in the database.
    """
    id: int
    title: str = Field(min_length=1, max_length=255)
    slug: str = Field(min_length=1, max_length=255)
    category_id: Optional[int] = None
    synopsis: Optional[str] = None
    release_year: Optional[int] = None
    is_active: bool = True


class Series(BaseModel):
    """Normalized Tavuno series model.

    Maps to tavuno_series table in the database.
    """
    id: int
    title: str = Field(min_length=1, max_length=255)
    slug: str = Field(min_length=1, max_length=255)
    category_id: Optional[int] = None
    synopsis: Optional[str] = None
    is_active: bool = True
