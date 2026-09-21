"""Authentication data models (M8.3)."""

from pydantic import BaseModel, Field
from typing import Optional


class RegisterRequest(BaseModel):
    """Request to register a new account."""
    username: str = Field(min_length=1, max_length=120)
    email: str = Field(min_length=5, max_length=255)
    password: str = Field(min_length=8, max_length=255)


class RegisterResponse(BaseModel):
    """Response from registration."""
    profile_id: int
    email: str
    display_name: str
    status: str


class ActivationRequest(BaseModel):
    """Request to activate an account (admin)."""
    profile_id: int
    plan_id: int
    payment_reference: str = Field(max_length=255)
    activation_date: Optional[str] = None


class ActivationResponse(BaseModel):
    """Response from account activation."""
    profile_id: int
    status: str
    subscription_id: int
