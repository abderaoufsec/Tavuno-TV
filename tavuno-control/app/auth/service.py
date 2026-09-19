"""Authentication service for login, refresh, and logout logic."""

from typing import Optional, Dict, Any
from contextlib import contextmanager
import time
import hashlib

from app.services import Services
from app.auth.password import hash_password, verify_password
from app.auth.tokens import create_access_token, create_refresh_token, verify_token


class AuthService:
    def __init__(self, services: Services):
        self.services = services
        self.settings = services.settings
        self.redis = services.redis

    @contextmanager
    def _db(self):
        with self.services.connection() as connection:
            yield connection

    def _get_rate_limit_key(self, email: str, ip: str) -> str:
        """Generate rate limit key for login attempts."""
        # Hash email+ip to avoid storing raw email in Redis
        identifier = hashlib.sha256(f"{email}:{ip}".encode()).hexdigest()
        return f"auth:login_rate:{identifier}"

    def _check_rate_limit(self, email: str, ip: str) -> bool:
        """Check if login rate limit is exceeded (10 attempts per minute)."""
        key = self._get_rate_limit_key(email, ip)
        try:
            current = self.redis.incr(key)
            if current == 1:
                self.redis.expire(key, 60)  # 1 minute window
            return current <= 10
        except Exception:
            return True  # Allow if Redis fails

    def login(self, email: str, password: str, device_fingerprint: str, platform: str, ip: str) -> Dict[str, Any]:
        """Authenticate user and issue tokens."""
        # Check rate limit
        if not self._check_rate_limit(email, ip):
            raise ValueError("Too many login attempts. Please try again later.")

        with self._db() as conn:
            # Find profile by email
            profile = conn.execute(
                "SELECT id, email, display_name, role, password_hash FROM tavuno_profiles WHERE email = %s AND status = 'active'",
                (email,)
            ).fetchone()
            
            if not profile:
                # Use generic error to avoid email enumeration
                raise ValueError("Invalid credentials")
            
            # Verify password (profile is guaranteed to exist here)
            if not verify_password(password, profile['password_hash']):
                raise ValueError("Invalid credentials")

            # Check or register device
            device = conn.execute(
                """
                SELECT id, profile, name, device_key, platform, is_active, revoked_at
                FROM tavuno_devices
                WHERE device_fingerprint = %s AND profile = %s
                """,
                (device_fingerprint, profile['id']),
            ).fetchone()

            device_id = None
            if device:
                if device['revoked_at']:
                    raise ValueError("Device has been revoked")
                # Update last_seen_at
                conn.execute(
                    "UPDATE tavuno_devices SET last_seen_at = NOW() WHERE id = %s",
                    (device['id'],)
                )
                device_id = device['id']
            else:
                # New device - check device limit
                # Get profile's active subscription → plan → max_devices
                subscription = conn.execute(
                    """
                    SELECT s.id, s.status, s.ends_at, p.max_devices
                    FROM tavuno_subscriptions s
                    JOIN tavuno_plans p ON s.plan = p.id
                    WHERE s.profile = %s AND s.status = 'active' AND s.ends_at > NOW()
                    ORDER BY s.ends_at DESC
                    LIMIT 1
                    """,
                    (profile['id'],),
                ).fetchone()
                
                # Default to 2 devices if no active subscription for testing
                max_devices = 2
                if subscription:
                    max_devices = subscription['max_devices'] or 2
                
                # Count active (non-revoked) devices for this profile
                active_count = conn.execute(
                    """
                    SELECT COUNT(*) as count
                    FROM tavuno_devices
                    WHERE profile = %s AND revoked_at IS NULL
                    """,
                    (profile['id'],),
                ).fetchone()['count']
                
                # Check if limit reached
                if active_count >= max_devices:
                    raise ValueError("device_limit_reached")
                
                # Create new device
                result = conn.execute(
                    """
                    INSERT INTO tavuno_devices (profile, name, device_key, device_fingerprint, platform, is_active, last_seen_at)
                    VALUES (%s, %s, %s, %s, %s, TRUE, NOW())
                    RETURNING id
                    """,
                    (profile['id'], f"{platform} device", device_fingerprint, device_fingerprint, platform),
                ).fetchone()
                device_id = result['id']

            conn.commit()

            # Create tokens
            access_token = create_access_token(
                {'profile_id': profile['id'], 'device_id': device_id},
                self.settings.jwt_access_ttl_seconds
            )
            refresh_token = create_refresh_token(
                {'profile_id': profile['id'], 'device_id': device_id},
                self.settings.jwt_refresh_ttl_seconds
            )

            return {
                'access_token': access_token,
                'refresh_token': refresh_token,
                'access_expires_at': (time.time() + self.settings.jwt_access_ttl_seconds),
                'refresh_expires_at': (time.time() + self.settings.jwt_refresh_ttl_seconds),
                'profile': {
                    'id': profile['id'],
                    'email': profile['email'],
                    'display_name': profile['display_name'],
                }
            }

    def refresh(self, refresh_token: str) -> Dict[str, Any]:
        """Refresh access token using refresh token."""
        try:
            payload = verify_token(refresh_token)
            if payload.get('type') != 'refresh':
                raise ValueError('Invalid token type')
        except ValueError as e:
            raise ValueError('Invalid refresh token')

        profile_id = payload['profile_id']
        device_id = payload['device_id']

        with self._db() as conn:
            # Verify device is still active
            device = conn.execute(
                "SELECT id, profile, is_active, revoked_at FROM tavuno_devices WHERE id = %s AND profile = %s",
                (device_id, profile_id),
            ).fetchone()
            
            if not device or not device['is_active'] or device['revoked_at']:
                raise ValueError('Device not found or revoked')

            # Invalidate old refresh token (in production, use token blacklist)
            # For now, we'll just issue new tokens

            # Create new tokens
            new_access = create_access_token(
                {'profile_id': profile_id, 'device_id': device_id},
                self.settings.jwt_access_ttl_seconds
            )
            new_refresh = create_refresh_token(
                {'profile_id': profile_id, 'device_id': device_id},
                self.settings.jwt_refresh_ttl_seconds
            )

            return {
                'access_token': new_access,
                'refresh_token': new_refresh,
                'access_expires_at': (time.time() + self.settings.jwt_access_ttl_seconds),
                'refresh_expires_at': (time.time() + self.settings.jwt_refresh_ttl_seconds),
            }

    def logout(self, refresh_token: str) -> None:
        """Logout by revoking the device session."""
        try:
            payload = verify_token(refresh_token)
            if payload.get('type') != 'refresh':
                return  # Ignore non-refresh tokens

            profile_id = payload['profile_id']
            device_id = payload['device_id']

            with self._db() as conn:
                conn.execute(
                    "UPDATE tavuno_devices SET revoked_at = NOW() WHERE id = %s AND profile = %s",
                    (device_id, profile_id),
                )
                conn.commit()
        except ValueError:
            pass  # Ignore invalid tokens

    def get_profile_from_token(self, access_token: str) -> Dict[str, Any]:
        """Load profile from access token."""
        payload = verify_token(access_token)
        if payload.get('type') != 'access':
            raise ValueError('Invalid token type')

        profile_id = payload['profile_id']
        device_id = payload['device_id']

        with self._db() as conn:
            profile = conn.execute(
                "SELECT id, email, display_name, role FROM tavuno_profiles WHERE id = %s AND status = 'active'",
                (profile_id,),
            ).fetchone()
            
            if not profile:
                raise ValueError('Profile not found')

            device = conn.execute(
                "SELECT id, is_active, revoked_at FROM tavuno_devices WHERE id = %s AND profile = %s",
                (device_id, profile_id),
            ).fetchone()
            
            if not device or not device['is_active'] or device['revoked_at']:
                raise ValueError('Device not found or revoked')

            return {
                'id': profile['id'],
                'email': profile['email'],
                'display_name': profile['display_name'],
                'role': profile['role'],
                'device_id': device_id,
            }
