"""Authentication module for Tavuno Control API (M9)."""

from .password import hash_password, verify_password
from .tokens import create_access_token, create_refresh_token, verify_token, decode_token
from .service import AuthService
from .router import router
from .models import RegisterRequest, RegisterResponse, ActivationRequest, ActivationResponse, PasswordResetRequest, PasswordResetConfirmRequest
from .token_storage import TokenStorageService
from .email_service import EmailService

__all__ = [
    'hash_password',
    'verify_password',
    'create_access_token',
    'create_refresh_token',
    'verify_token',
    'decode_token',
    'AuthService',
    'router',
    'RegisterRequest',
    'RegisterResponse',
    'ActivationRequest',
    'ActivationResponse',
    'PasswordResetRequest',
    'PasswordResetConfirmRequest',
    'TokenStorageService',
    'EmailService',
]
