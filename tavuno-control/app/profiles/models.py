"""Profile API models (Slice D)."""

from pydantic import BaseModel, Field
from typing import Optional


class ProfileSummary(BaseModel):
    """A viewing profile as the client sees it.

    ``is_owner`` marks the account row itself (the one carrying credentials and
    any subscription); the rest are child profiles that exist only to split one
    subscription across viewers.
    """

    id: int
    display_name: str
    email: Optional[str] = None
    role: str = "user"
    status: Optional[str] = None
    avatar: Optional[str] = None
    is_kids: bool = False
    is_owner: bool = False


class CreateProfileRequest(BaseModel):
    """Add a viewing profile under the caller's account."""

    display_name: str = Field(min_length=1, max_length=60)
    avatar: Optional[str] = Field(default=None, max_length=255)
    is_kids: bool = False


class UpdateProfileRequest(BaseModel):
    """Edit a profile. Unset fields are left alone; ``avatar=""`` clears the avatar."""

    display_name: Optional[str] = Field(default=None, min_length=1, max_length=60)
    avatar: Optional[str] = Field(default=None, max_length=255)
    is_kids: Optional[bool] = None