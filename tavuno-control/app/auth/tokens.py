"""JWT token management for authentication."""

import jwt
import uuid
from datetime import datetime, timedelta, timezone
from typing import Dict, Any
from app.config import get_settings


def create_access_token(data: Dict[str, Any], expires_in_seconds: int = 900) -> str:
    """Create a JWT access token."""
    settings = get_settings()
    expire = datetime.now(timezone.utc) + timedelta(seconds=expires_in_seconds)
    payload = {
        **data,
        'exp': expire,
        'iat': datetime.now(timezone.utc),
        'type': 'access'
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm='HS256')


def create_refresh_token(data: Dict[str, Any], expires_in_seconds: int = 2592000) -> str:
    """Create a JWT refresh token (30 days default)."""
    settings = get_settings()
    expire = datetime.now(timezone.utc) + timedelta(seconds=expires_in_seconds)
    payload = {
        **data,
        'exp': expire,
        'iat': datetime.now(timezone.utc),
        'type': 'refresh',
        'jti': str(uuid.uuid4())  # JWT ID for revocation tracking
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm='HS256')


def verify_token(token: str) -> Dict[str, Any]:
    """Verify and decode a JWT token."""
    settings = get_settings()
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=['HS256'])
        return payload
    except jwt.ExpiredSignatureError:
        raise ValueError('Token has expired')
    except jwt.InvalidTokenError:
        raise ValueError('Invalid token')


def decode_token(token: str) -> Dict[str, Any]:
    """Decode a JWT token without verification (for debugging only)."""
    try:
        return jwt.decode(token, options={'verify_signature': False}, algorithms=['HS256'])
    except Exception:
        return {}
