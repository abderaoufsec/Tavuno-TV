"""Token storage service for password reset and email verification (M8.5)."""

import json
import secrets
from typing import Optional, Dict, Any
from datetime import datetime, timezone, timedelta


class TokenStorageService:
    """Service for storing and retrieving tokens in Redis."""

    def __init__(self, redis):
        self.redis = redis

    def generate_token(self) -> str:
        """Generate a secure random token."""
        return secrets.token_urlsafe(32)

    def store_password_reset_token(
        self,
        profile_id: int,
        email: str,
        ttl_seconds: int = 3600
    ) -> str:
        """Store a password reset token."""
        token = self.generate_token()
        key = f"password_reset:{token}"
        value = json.dumps({
            'profile_id': profile_id,
            'email': email,
            'created_at': datetime.now(timezone.utc).isoformat()
        })
        self.redis.setex(key, ttl_seconds, value)
        return token

    def get_password_reset_token(self, token: str) -> Optional[Dict[str, Any]]:
        """Retrieve a password reset token."""
        key = f"password_reset:{token}"
        value = self.redis.get(key)
        if value:
            return json.loads(value)
        return None

    def delete_password_reset_token(self, token: str) -> bool:
        """Delete a password reset token."""
        key = f"password_reset:{token}"
        return bool(self.redis.delete(key))

    def store_email_verification_token(
        self,
        profile_id: int,
        email: str,
        ttl_seconds: int = 86400
    ) -> str:
        """Store an email verification token."""
        token = self.generate_token()
        key = f"email_verification:{token}"
        value = json.dumps({
            'profile_id': profile_id,
            'email': email,
            'created_at': datetime.now(timezone.utc).isoformat()
        })
        self.redis.setex(key, ttl_seconds, value)
        return token

    def get_email_verification_token(self, token: str) -> Optional[Dict[str, Any]]:
        """Retrieve an email verification token."""
        key = f"email_verification:{token}"
        value = self.redis.get(key)
        if value:
            return json.loads(value)
        return None

    def delete_email_verification_token(self, token: str) -> bool:
        """Delete an email verification token."""
        key = f"email_verification:{token}"
        return bool(self.redis.delete(key))
