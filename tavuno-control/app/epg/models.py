"""EPG guide models (Slice C).

Timestamps are carried as ISO-8601 strings rather than datetimes so the API
boundary matches every other Tavuno endpoint (and the Android client's Gson
models), and so a value that survives a Redis round-trip is byte-identical to
one that did not.
"""

from pydantic import BaseModel, Field
from typing import List, Optional


class EpgProgramme(BaseModel):
    """One programme inside a guide window."""

    id: int
    title: str
    starts_at: str
    ends_at: str
    description: Optional[str] = None
    channel_id: int


class GuideChannel(BaseModel):
    """A channel row in the guide: the channel plus whatever programmes fall inside the window.

    ``programmes`` may be empty — the guide still lists a channel whose EPG has
    not synced yet, so a viewer sees "no guide data" for that row rather than the
    row vanishing.
    """

    id: int
    name: str
    slug: str
    category_id: Optional[int] = None
    logo: Optional[str] = None
    programmes: List[EpgProgramme] = Field(default_factory=list)


class EpgWindow(BaseModel):
    """The whole guide payload for one request: the window plus its channel rows."""

    window_start: str
    window_end: str
    channels: List[GuideChannel] = Field(default_factory=list)