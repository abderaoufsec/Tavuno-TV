"""Email service interface for password reset and email verification (M8.5)."""

from typing import Optional
from app.config import Settings


class EmailService:
    """Email service for sending transactional emails."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.enabled = settings.email_enabled

    def send_password_reset_email(self, email: str, username: str, token: str) -> bool:
        """Send password reset email."""
        if not self.enabled:
            # In development, log instead of sending
            print(f"[EMAIL SERVICE] Password reset email would be sent to {email}")
            print(f"[EMAIL SERVICE] Reset link: {self.settings.email_base_url}/reset-password?token={token}")
            return True

        # TODO: Implement actual email sending with SMTP
        # For now, return True to simulate success
        return True

    def send_email_verification_email(self, email: str, username: str, token: str) -> bool:
        """Send email verification email."""
        if not self.enabled:
            # In development, log instead of sending
            print(f"[EMAIL SERVICE] Email verification would be sent to {email}")
            print(f"[EMAIL SERVICE] Verification link: {self.settings.email_base_url}/verify-email?token={token}")
            return True

        # TODO: Implement actual email sending with SMTP
        # For now, return True to simulate success
        return True
