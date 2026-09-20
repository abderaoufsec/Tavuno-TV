"""Catalog domain models (M11-M12).

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
from typing import Optional, List, Dict, Any


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


class ChannelDetails(BaseModel):
    """Extended channel details for content detail screens (M12).

    Includes additional metadata useful for channel detail views.
    Extends the base Channel model with UI-relevant fields.
    """
    id: int
    name: str
    slug: str
    category_id: Optional[int] = None
    logo: Optional[str] = None
    is_active: bool = True
    description: Optional[str] = None
    category_name: Optional[str] = None
    playback_available: bool = True


class MovieDetails(BaseModel):
    """Extended movie details for content detail screens (M12).

    Includes additional metadata useful for movie detail views.
    Extends the base Movie model with UI-relevant fields.
    """
    id: int
    title: str
    slug: str
    category_id: Optional[int] = None
    synopsis: Optional[str] = None
    release_year: Optional[int] = None
    is_active: bool = True
    poster: Optional[str] = None
    backdrop: Optional[str] = None
    duration: Optional[str] = None
    category_name: Optional[str] = None
    genres: Optional[List[str]] = None
    playback_available: bool = True


class SeriesDetails(BaseModel):
    """Extended series details for content detail screens (M12).

    Includes additional metadata useful for series detail views.
    Extends the base Series model with UI-relevant fields.
    """
    id: int
    title: str
    slug: str
    category_id: Optional[int] = None
    synopsis: Optional[str] = None
    is_active: bool = True
    poster: Optional[str] = None
    backdrop: Optional[str] = None
    release_year: Optional[int] = None
    category_name: Optional[str] = None
    seasons: Optional[List[Dict[str, Any]]] = None
    episode_count: Optional[int] = None
    playback_available: bool = True


class SeasonDetails(BaseModel):
    """Season details for series content (M12).

    Provides season-level information with episode metadata.
    """
    season_number: int
    name: str
    episode_count: int
    episodes: Optional[List[Dict[str, Any]]] = None


class EpisodeDetails(BaseModel):
    """Episode details for series content (M12).

    Provides episode-level information for playback.
    """
    id: int
    title: str
    season_number: int
    episode_number: int
    synopsis: Optional[str] = None
    duration: Optional[str] = None
    thumbnail: Optional[str] = None
    playback_available: bool = True
