"""FastAPI router for device management endpoints (M9)."""

from fastapi import APIRouter, HTTPException, status, Header, Request
from typing import List

from app.devices.service import DeviceService
from app.devices.models import DeviceDto, DeviceRegisterRequest
from app.services import Services
from app.auth.service import AuthService


router = APIRouter(prefix="/v1/devices", tags=["devices"])


@router.get("", response_model=List[DeviceDto])
def list_devices(
    request: Request,
    device_fingerprint: str = Header(..., alias="X-Device-Fingerprint"),
):
    """List all devices for the current user."""
    services: Services = request.app.state.services
    device_service = DeviceService(services)
    auth_service = AuthService(services)
    
    # Get current user from Authorization header
    auth_header = request.headers.get("authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid authorization header"
        )
    token = auth_header.replace("Bearer ", "")
    
    try:
        profile = auth_service.get_profile_from_token(token)
        return device_service.list_devices(profile['profile_id'], device_fingerprint)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(e)
        )


@router.post("/register", response_model=DeviceDto, status_code=status.HTTP_201_CREATED)
def register_device(
    body: DeviceRegisterRequest,
    request: Request,
):
    """Register a new device (idempotent on fingerprint)."""
    services: Services = request.app.state.services
    device_service = DeviceService(services)
    
    try:
        return device_service.register_device(
            profile_id=body.profile_id,
            device_fingerprint=body.device_fingerprint,
            name=body.name,
            platform=body.platform,
        )
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )


@router.delete("/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
def revoke_device(
    device_id: int,
    request: Request,
):
    """Revoke a device."""
    services: Services = request.app.state.services
    device_service = DeviceService(services)
    auth_service = AuthService(services)
    
    # Get current user from Authorization header
    auth_header = request.headers.get("authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid authorization header"
        )
    token = auth_header.replace("Bearer ", "")
    
    try:
        profile = auth_service.get_profile_from_token(token)
        device_service.revoke_device(profile['profile_id'], device_id)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND if "not found" in str(e).lower() else status.HTTP_403_FORBIDDEN,
            detail=str(e)
        )
