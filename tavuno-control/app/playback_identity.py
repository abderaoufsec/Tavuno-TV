"""Playback identity resolution (open-access guest vs Bearer auth)."""

import logging

from fastapi import HTTPException, Request, status

from app.auth.service import AuthService
from app.services import Services

logger = logging.getLogger("tavuno-control.playback_identity")


def _database(active: Services):
    return active.connection()


def _get_services(request: Request | None, services: Services | None) -> Services:
    """Return the Services instance, preferring the explicit argument.

    The playback routes call the resolver directly (unit tests invoke them as
    plain functions), so `request.app.state.services` may not be populated.
    """
    if services is not None:
        return services
    if request is not None:
        stored = getattr(request.app.state, "services", None)
        if stored is not None:
            return stored
    raise HTTPException(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        detail="Services not available",
    )


def _guest_principal_from_request(request: Request | None, services: Services | None) -> dict:
    """Resolve the seeded guest principal without requiring a Bearer token."""
    from app.auth.deps import _guest_principal

    return _guest_principal(_get_services(request, services))


def resolve_playback_identity(
    request: Request | None,
    services: Services | None,
    authorization: str | None,
) -> tuple[int, str]:
    """Resolve (profile_id, device_key) for playback routes.

    Open-access mode tries a supplied Bearer token first so logged-in users
    keep their own profile, then falls back to the seeded guest identity.
    Auth mode validates the Bearer token exactly as before, preserving
    existing error semantics.
    """
    active = _get_services(request, services)
    open_access = bool(getattr(active.settings, "auth_open_access", False))
    if open_access and not authorization:
        guest = _guest_principal_from_request(request, active)
        return guest["profile_id"], guest["device_key"]
    if not authorization:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authorization header required")
    if not authorization.startswith("Bearer "):
        if open_access:
            # Malformed header from a legacy client: degrade to guest rather
            # than locking the free-launch app out of playback.
            guest = _guest_principal_from_request(request, active)
            return guest["profile_id"], guest["device_key"]
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authorization header")
    token = authorization.replace("Bearer ", "")
    try:
        auth_service = AuthService(active)
        auth_data = auth_service.get_profile_from_token(token)
        profile_id = auth_data["profile_id"]
        device_id = auth_data["device_id"]
    except HTTPException:
        if open_access:
            guest = _guest_principal_from_request(request, active)
            return guest["profile_id"], guest["device_key"]
        raise
    except ValueError as exc:
        if open_access:
            logger.info("Playback token invalid, falling back to guest identity")
            guest = _guest_principal_from_request(request, active)
            return guest["profile_id"], guest["device_key"]
        logger.exception("Playback identity resolution failed - invalid token")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token") from exc
    except Exception as exc:
        if open_access:
            logger.exception("Playback identity resolution failed, falling back to guest")
            guest = _guest_principal_from_request(request, active)
            return guest["profile_id"], guest["device_key"]
        logger.exception("Playback identity resolution failed")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Authorization failed") from exc
    with _database(active) as connection:
        device = connection.execute(
            "SELECT device_key FROM tavuno_devices WHERE id = %s AND profile = %s",
            (device_id, profile_id),
        ).fetchone()
        if not device:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
        return profile_id, device["device_key"]
