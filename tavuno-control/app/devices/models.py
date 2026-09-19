"""Device data models (M9)."""

from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime


class DeviceDto(BaseModel):
    """Device data transfer object."""
    id: int
    display_name: str
    platform: str
    last_seen_at: Optional[datetime] = None
    is_current: bool = False
    is_revoked: bool = False


class DeviceRegisterRequest(BaseModel):
    """Request to register a device."""
    profile_id: int
    name: str = Field(min_length=1, max_length=120)
    device_fingerprint: str = Field(min_length=8, max_length=255)
    platform: str = Field(min_length=2, max_length=48)
