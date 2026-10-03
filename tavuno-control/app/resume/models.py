"""Resume / progress API models (A6).

``position_ms`` and ``duration_ms`` are milliseconds because that is what both
sides already speak: ExoPlayer reports ``currentPosition`` in ms and the
Android ``PosterCard`` wants a 0..1 ratio, which is the only place the division
happens (:attr:`ProgressPayload.progress`).
"""

from pydantic import BaseModel, Field
from typing import Optional, List, Any, Dict


class ProgressRef(BaseModel):
    """A (kind, item_id) pointer to one resumable piece of content."""

    kind: str = Field(min_length=1, max_length=32)
    item_id: int


class ProgressPayload(BaseModel):
    """Where the viewer got to, for one item.

    ``progress`` is the convenience ratio the grid needs (position / duration,
    clamped to 0..1, and ``None`` while the duration is unknown — a live stream
    has no end, and reporting 0.0 for it would paint an empty progress bar on
    every channel row).
    """

    kind: str
    item_id: int
    position_ms: int = 0
    duration_ms: int = 0
    progress: Optional[float] = None
    item: Optional[Dict[str, Any]] = None


class ProgressUpdate(BaseModel):
    """Report a playback position. Duration may be omitted/0 for live content."""

    kind: str = Field(min_length=1, max_length=32)
    item_id: int
    position_ms: int = Field(default=0, ge=0)
    duration_ms: int = Field(default=0, ge=0)


class ProgressList(BaseModel):
    """Continue-watching entries for one profile, most recently watched first."""

    items: List[Dict[str, Any]] = Field(default_factory=list)