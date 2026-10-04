"""FastAPI router for device management endpoints (M9)."""

from fastapi import APIRouter, Depends, HTTPException, status, Request
from typing import List

from app.auth.deps import current_principal
from app.devices.service import DeviceService
from app.devices.models import DeviceDto, DeviceRegisterRequest
from app.services import Services


router = APIRouter(prefix="/v1/devices", tags=["devices"])


@router.get("", response_model=List[DeviceDto])
def list_devices(
    request: Request,
    profile: dict = Depends(current_principal),
):
    """List the devices belonging to the current principal.

    Identity comes from `current_principal` like every other router, so
    AUTH_OPEN_ACCESS resolves to the seeded guest identity instead of failing
    401 on a header the free-launch client never sends (F-4).
    """
    services: Services = request.app.state.services
    device_service = DeviceService(services)
    return device_service.list_devices(profile["profile_id"], profile.get("device_id"))


@router.post("/register", response_model=DeviceDto, status_code=status.HTTP_201_CREATED)
def register_device(
    body: DeviceRegisterRequest,
    request: Request,
    profile: dict = Depends(current_principal),
):
    """Register a new device (idempotent on fingerprint).

    The device is always attached to the *authenticated* principal's profile.
    `body.profile_id` is accepted for contract compatibility but deliberately
    ignored: binding on it would let any caller register devices against
    someone else's profile (F-4).
    """
    services: Services = request.app.state.services
    device_service = DeviceService(services)
    try:
        return device_service.register_device(
            profile_id=profile["profile_id"],
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
    profile: dict = Depends(current_principal),
):
    """Revoke a device owned by the current principal."""
    services: Services = request.app.state.services
    device_service = DeviceService(services)
    try:
        device_service.revoke_device(profile["profile_id"], device_id)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND if "not found" in str(e).lower() else status.HTTP_403_FORBIDDEN,
            detail=str(e)
        )
