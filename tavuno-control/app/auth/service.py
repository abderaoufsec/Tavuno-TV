"""Authentication service for login, refresh, and logout logic."""

from typing import Optional, Dict, Any
from contextlib import contextmanager
import time
import hashlib
import re

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

    def _validate_email(self, email: str) -> bool:
        """Validate email format."""
        pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
        return re.match(pattern, email) is not None

    def _validate_password(self, password: str) -> None:
        """Validate password strength."""
        if len(password) < 8:
            raise ValueError("Password must be at least 8 characters")

    def register(self, username: str, email: str, password: str) -> Dict[str, Any]:
        """Register a new account."""
        # Validate email format
        if not self._validate_email(email):
            raise ValueError("Invalid email format")

        # Validate password strength
        self._validate_password(password)

        with self._db() as conn:
            # Check for duplicate email
            existing = conn.execute(
                "SELECT id FROM tavuno_profiles WHERE email = %s",
                (email,)
            ).fetchone()

            if existing:
                raise ValueError("Email already registered")

            # Determine initial status based on FREE_LAUNCH
            initial_status = 'active' if self.settings.free_launch else 'inactive'

            # Create profile
            password_hash = hash_password(password)
            result = conn.execute(
                """
                INSERT INTO tavuno_profiles
                (display_name, email, password_hash, role, status)
                VALUES (%s, %s, %s, 'user', %s)
                RETURNING id
                """,
                (username, email, password_hash, initial_status)
            ).fetchone()

            profile_id = result['id']

            # If FREE_LAUNCH, create default subscription and entitlements
            if self.settings.free_launch and self.settings.default_plan_id:
                # Create subscription
                subscription_result = conn.execute(
                    """
                    INSERT INTO tavuno_subscriptions
                    (profile, plan, status, starts_at, ends_at)
                    VALUES (%s, %s, 'active', NOW(), NULL)
                    RETURNING id
                    """,
                    (profile_id, self.settings.default_plan_id)
                ).fetchone()

                subscription_id = subscription_result['id']

                # Create default entitlements (all content)
                conn.execute(
                    """
                    INSERT INTO tavuno_entitlements
                    (subscription, resource_type, resource_key, is_active)
                    VALUES (%s, 'all', '*', TRUE)
                    """,
                    (subscription_id,)
                )

            conn.commit()

            return {
                'profile_id': profile_id,
                'email': email,
                'display_name': username,
                'status': initial_status
            }

    def activate_account(self, profile_id: int, plan_id: int, payment_reference: str) -> Dict[str, Any]:
        """Activate an account and create subscription (admin/webhook)."""
        with self._db() as conn:
            # Check if profile exists
            profile = conn.execute(
                "SELECT id, status FROM tavuno_profiles WHERE id = %s",
                (profile_id,)
            ).fetchone()

            if not profile:
                raise ValueError("Profile not found")

            # Check if subscription already exists
            existing_subscription = conn.execute(
                "SELECT id FROM tavuno_subscriptions WHERE profile = %s AND status = 'active'",
                (profile_id,)
            ).fetchone()

            if existing_subscription:
                raise ValueError("Subscription already exists")

            # Update profile status to active
            conn.execute(
                "UPDATE tavuno_profiles SET status = 'active' WHERE id = %s",
                (profile_id,)
            )

            # Create subscription
            subscription_result = conn.execute(
                """
                INSERT INTO tavuno_subscriptions
                (profile, plan, status, starts_at, ends_at)
                VALUES (%s, %s, 'active', NOW(), NULL)
                RETURNING id
                """,
                (profile_id, plan_id)
            ).fetchone()

            subscription_id = subscription_result['id']

            # Create default entitlements (all content)
            conn.execute(
                """
                INSERT INTO tavuno_entitlements
                (subscription, resource_type, resource_key, is_active)
                VALUES (%s, 'all', '*', TRUE)
                """,
                (subscription_id,)
            )

            conn.commit()

            return {
                'profile_id': profile_id,
                'status': 'active',
                'subscription_id': subscription_id
            }

    def get_subscription(self, profile_id: int) -> Dict[str, Any]:
        """Get subscription and entitlements for a profile."""
        with self._db() as conn:
            # Get subscription with plan
            subscription = conn.execute(
                """
                SELECT s.id, s.status, s.starts_at, s.ends_at,
                       p.id as plan_id, p.name as plan_name, p.code as plan_code,
                       p.max_devices, p.max_concurrent_streams
                FROM tavuno_subscriptions s
                JOIN tavuno_plans p ON p.id = s.plan
                WHERE s.profile = %s AND s.status = 'active'
                  AND (s.ends_at IS NULL OR s.ends_at > NOW())
                ORDER BY s.id DESC
                LIMIT 1
                """,
                (profile_id,)
            ).fetchone()

            if not subscription:
                return {
                    'subscription': None,
                    'plan': None,
                    'entitlements': []
                }

            # Get entitlements
            entitlements = conn.execute(
                """
                SELECT resource_type, resource_key
                FROM tavuno_entitlements
                WHERE subscription = %s AND is_active = TRUE
                """,
                (subscription['id'],)
            ).fetchall()

            return {
                'subscription': {
                    'id': subscription['id'],
                    'status': subscription['status'],
                    'starts_at': subscription['starts_at'].isoformat() if subscription['starts_at'] else None,
                    'ends_at': subscription['ends_at'].isoformat() if subscription['ends_at'] else None
                },
                'plan': {
                    'id': subscription['plan_id'],
                    'name': subscription['plan_name'],
                    'code': subscription['plan_code'],
                    'max_devices': subscription['max_devices'],
                    'max_concurrent_streams': subscription['max_concurrent_streams']
                },
                'entitlements': [
                    {
                        'resource_type': e['resource_type'],
                        'resource_key': e['resource_key']
                    }
                    for e in entitlements
                ]
            }

    def login(self, email: str, password: str, device_fingerprint: str, platform: str, ip: str) -> Dict[str, Any]:
        """Authenticate user and issue tokens."""
        # Check rate limit
        if not self._check_rate_limit(email, ip):
            raise ValueError("Too many login attempts. Please try again later.")

        with self._db() as conn:
            # Find profile by email
            profile = conn.execute(
                "SELECT id, email, display_name, role, password_hash, status FROM tavuno_profiles WHERE email = %s",
                (email,)
            ).fetchone()

            if not profile:
                # Use generic error to avoid email enumeration
                raise ValueError("Invalid credentials")

            # Check account status
            if profile['status'] == 'inactive':
                raise ValueError("ACCOUNT_INACTIVE")

            if profile['status'] != 'active':
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
                "SELECT id, email, display_name, role FROM tavuno_profiles WHERE id = %s",
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
                'profile_id': profile['id'],
                'email': profile['email'],
                'display_name': profile['display_name'],
                'role': profile['role'],
                'device_id': device_id,
            }
