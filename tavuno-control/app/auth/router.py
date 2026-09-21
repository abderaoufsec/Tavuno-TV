"""FastAPI router for authentication endpoints."""

from fastapi import APIRouter, Depends, HTTPException, status, Request
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, Field
from typing import Optional

from app.auth.service import AuthService
from app.auth.models import RegisterRequest, RegisterResponse, ActivationRequest, ActivationResponse, PasswordResetRequest, PasswordResetConfirmRequest
from app.services import Services


router = APIRouter(prefix="/v1/auth", tags=["auth"])


class LoginRequest(BaseModel):
    email: str = Field(..., min_length=1, max_length=255)
    password: str = Field(..., min_length=1, max_length=255)
    device_fingerprint: str = Field(..., min_length=8, max_length=255)
    platform: str = Field(..., min_length=2, max_length=48)


class RefreshRequest(BaseModel):
    refresh_token: str


class LogoutRequest(BaseModel):
    refresh_token: str


class LoginResponse(BaseModel):
    access_token: str
    refresh_token: str
    access_expires_at: float
    refresh_expires_at: float
    profile: dict


def get_auth_service(request: Request) -> AuthService:
    """Dependency to get AuthService instance."""
    services: Services = request.app.state.services
    return AuthService(services)


def get_client_ip(request: Request) -> str:
    """Get client IP address."""
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host


@router.post("/login", response_model=LoginResponse, status_code=status.HTTP_200_OK)
def login(
    request: LoginRequest,
    auth_service: AuthService = Depends(get_auth_service),
    client_ip: str = Depends(get_client_ip),
):
    """Authenticate user with email/password and issue tokens."""
    try:
        return auth_service.login(
            email=request.email,
            password=request.password,
            device_fingerprint=request.device_fingerprint,
            platform=request.platform,
            ip=client_ip,
        )
    except ValueError as e:
        # Return 403 for device limit errors, 401 for credentials errors
        error_msg = str(e).lower()
        if "device_limit_reached" in error_msg or "device_revoked" in error_msg:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=str(e)
            )
        if "account_inactive" in error_msg:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail={
                    "error": "ACCOUNT_INACTIVE",
                    "message": "Your account is not activated yet. Please activate your subscription from your Tavuno account."
                }
            )
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(e)
        )


@router.post("/refresh", response_model=LoginResponse, status_code=status.HTTP_200_OK)
def refresh(request: RefreshRequest, auth_service: AuthService = Depends(get_auth_service)):
    """Refresh access token using refresh token."""
    try:
        return auth_service.refresh(request.refresh_token)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(e)
        )


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(request: LogoutRequest, auth_service: AuthService = Depends(get_auth_service)):
    """Logout by invalidating refresh token and revoking device."""
    auth_service.logout(request.refresh_token)


@router.get("/me")
def get_current_user(
    auth_service: AuthService = Depends(get_auth_service),
    credentials: HTTPAuthorizationCredentials = Depends(HTTPBearer()),
):
    """Get current user profile from access token."""
    try:
        profile = auth_service.get_profile_from_token(credentials.credentials)
        return {
            'id': profile['profile_id'],
            'email': profile['email'],
            'display_name': profile['display_name'],
            'role': profile['role'],
        }
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(e)
        )


@router.post("/register", response_model=RegisterResponse, status_code=status.HTTP_201_CREATED)
def register(
    request: RegisterRequest,
    auth_service: AuthService = Depends(get_auth_service),
):
    """Register a new account."""
    try:
        return auth_service.register(request.username, request.email, request.password)
    except ValueError as e:
        error_msg = str(e).lower()
        if "email" in error_msg and "registered" in error_msg:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=str(e)
            )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )


@router.get("/subscription")
def get_subscription(
    auth_service: AuthService = Depends(get_auth_service),
    credentials: HTTPAuthorizationCredentials = Depends(HTTPBearer()),
):
    """Get current subscription and entitlements."""
    try:
        profile = auth_service.get_profile_from_token(credentials.credentials)
        return auth_service.get_subscription(profile['profile_id'])
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(e)
        )


@router.post("/subscription/activate", response_model=ActivationResponse, status_code=status.HTTP_200_OK)
def activate_account(
    request: ActivationRequest,
    auth_service: AuthService = Depends(get_auth_service),
    credentials: HTTPAuthorizationCredentials = Depends(HTTPBearer()),
):
    """Activate an account (admin only)."""
    try:
        # Verify admin role
        profile = auth_service.get_profile_from_token(credentials.credentials)
        if profile.get('role') != 'admin':
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Admin access required"
            )

        return auth_service.activate_account(
            request.profile_id,
            request.plan_id,
            request.payment_reference
        )
    except ValueError as e:
        error_msg = str(e).lower()
        if "not found" in error_msg:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=str(e)
            )
        if "already exists" in error_msg:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=str(e)
            )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )


@router.post("/password-reset/request", status_code=status.HTTP_200_OK)
def request_password_reset(
    request: PasswordResetRequest,
    auth_service: AuthService = Depends(get_auth_service),
):
    """Request a password reset email."""
    try:
        auth_service.request_password_reset(request.email)
        # Always return success to prevent email enumeration
        return {"message": "If the email exists, a password reset link has been sent"}
    except ValueError as e:
        # Validate email format but don't reveal if email exists
        if "email" in str(e).lower():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=str(e)
            )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )


@router.post("/password-reset/confirm", status_code=status.HTTP_200_OK)
def confirm_password_reset(
    request: PasswordResetConfirmRequest,
    auth_service: AuthService = Depends(get_auth_service),
):
    """Confirm password reset with token."""
    try:
        auth_service.confirm_password_reset(request.token, request.new_password)
        return {"message": "Password has been reset successfully"}
    except ValueError as e:
        error_msg = str(e).lower()
        if "token" in error_msg:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=str(e)
            )
        if "password" in error_msg:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=str(e)
            )
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )
