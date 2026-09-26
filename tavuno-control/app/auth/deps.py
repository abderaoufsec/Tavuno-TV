"""Authentication dependencies for FastAPI routes."""

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from app.auth.service import AuthService

bearer = HTTPBearer(auto_error=False)


def current_principal(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
) -> dict:
    """Dependency to get current authenticated user from access token."""
    if credentials is None:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            "Authorization header required"
        )
    try:
        services = request.app.state.services
        return AuthService(services).get_profile_from_token(credentials.credentials)
    except Exception as exc:
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
