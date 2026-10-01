"""Authentication dependencies for FastAPI routes."""

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from app.auth.service import AuthService

bearer = HTTPBearer(auto_error=False)

GUEST_ROLE = "user"


def _request_services(request: Request):
    """Resolve the app services, lazily creating them like get_services does.

    current_principal can run before any ServicesDependency-bearing route has
    initialized app.state.services (e.g. on the very first request).
    """
    services = getattr(request.app.state, "services", None)
    if services is None:
        from app.config import get_settings
        from app.services import Services

        services = Services(get_settings())
        request.app.state.services = services
    return services


def _guest_principal(services) -> dict:
    """Build the anonymous guest principal used when AUTH_OPEN_ACCESS is enabled.

    Creates the seeded guest profile/device on first use so playback-session
    foreign keys stay valid, then returns the same identity on later calls.
    """
    settings = services.settings
    email = (getattr(settings, "auth_guest_email", None) or "guest@tavuno.local").strip().lower()
    with services.connection() as connection:
        profile = connection.execute(
            "SELECT id, role FROM tavuno_profiles WHERE email = %s",
            (email,),
        ).fetchone()
        if profile is None:
            created = connection.execute(
                """
                INSERT INTO tavuno_profiles (email, display_name, role, status)
                VALUES (%s, %s, 'user', 'active')
                RETURNING id, role
                """,
                (email, "Guest"),
            ).fetchone()
            connection.commit()
            profile = created
        profile_id = profile["id"]
        device_key = getattr(settings, "auth_guest_device_key", None) or "tavuno-tv-guest"
        device = connection.execute(
            "SELECT id FROM tavuno_devices WHERE device_key = %s AND profile = %s",
            (device_key, profile_id),
        ).fetchone()
        if device is None:
            created_device = connection.execute(
                """
                INSERT INTO tavuno_devices
                    (profile, name, device_key, platform, is_active, last_seen_at)
                VALUES (%s, %s, %s, 'android-tv', TRUE, NOW())
                RETURNING id
                """,
                (profile_id, "Guest TV", device_key),
            ).fetchone()
            connection.commit()
            device_id = created_device["id"]
        else:
            device_id = device["id"]
            connection.execute(
                "UPDATE tavuno_devices SET is_active = TRUE, last_seen_at = NOW() WHERE id = %s",
                (device_id,),
            )
            connection.commit()
    return {
        "profile_id": profile_id,
        "device_id": device_id,
        "device_key": device_key,
        "email": email,
        "display_name": "Guest",
        "role": profile.get("role") or GUEST_ROLE,
        "guest": True,
    }


def current_principal(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> dict:
    """Dependency to get current authenticated user from access token.

    When AUTH_OPEN_ACCESS is enabled, missing/invalid credentials resolve to
    the seeded guest identity so the free-launch app works without login.
    Valid Bearer tokens still resolve to their real profile.
    """
    services = _request_services(request)
    open_access = bool(getattr(services.settings, "auth_open_access", False))
    if open_access and credentials is None:
        return _guest_principal(services)
    if credentials is None:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            "Authorization header required"
        )
    try:
        return AuthService(services).get_profile_from_token(credentials.credentials)
    except Exception as exc:
        if open_access:
            # Tolerate expired/invalid tokens in free-launch mode so cached
            # clients keep working without forcing a login screen.
            return _guest_principal(services)
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            str(exc)
        ) from exc


def require_admin(principal: dict = Depends(current_principal)) -> dict:
    """Dependency to require admin role."""
    if principal.get("role") != "admin":
        raise HTTPException(
            status.HTTP_403_FORBIDDEN,
            "Admin access required"
        )
    return principal
