"""Device management service (M9)."""

from typing import List, Dict, Any, Optional
from contextlib import contextmanager

from app.services import Services
from app.devices.models import DeviceDto


class DeviceService:
    def __init__(self, services: Services):
        self.services = services
        self.settings = services.settings

    @contextmanager
    def _db(self):
        with self.services.connection() as connection:
            yield connection

    def list_devices(self, profile_id: int, current_device_id: int) -> List[DeviceDto]:
        """List all devices for a profile."""
        with self._db() as conn:
            devices = conn.execute(
                """
                SELECT id, name as display_name, platform, last_seen_at, revoked_at, device_fingerprint
                FROM tavuno_devices
                WHERE profile = %s
                ORDER BY last_seen_at DESC NULLS LAST
                """,
                (profile_id,),
            ).fetchall()
            
            return [
                DeviceDto(
                    id=d['id'],
                    display_name=d['display_name'],
                    platform=d['platform'],
                    last_seen_at=d['last_seen_at'],
                    is_current=(d['id'] == current_device_id),
                    is_revoked=(d['revoked_at'] is not None)
                )
                for d in devices
            ]

    def register_device(self, profile_id: int, device_fingerprint: str, name: str, platform: str) -> DeviceDto:
        """Register or update a device (idempotent on fingerprint)."""
        with self._db() as conn:
            # Check if device exists
            existing = conn.execute(
                """
                SELECT id, profile, name, device_fingerprint, platform, is_active, revoked_at
                FROM tavuno_devices
                WHERE device_fingerprint = %s AND profile = %s
                """,
                (device_fingerprint, profile_id),
            ).fetchone()
            
            if existing:
                if existing['revoked_at']:
                    raise ValueError("Device has been revoked")
                # Update existing device
                conn.execute(
                    """
                    UPDATE tavuno_devices
                    SET name = %s, platform = %s, last_seen_at = NOW(), is_active = TRUE
                    WHERE id = %s
                    """,
                    (name, platform, existing['id']),
                )
                device_id = existing['id']
            else:
                # Create new device
                result = conn.execute(
                    """
                    INSERT INTO tavuno_devices (profile, name, device_fingerprint, platform, is_active, last_seen_at)
                    VALUES (%s, %s, %s, %s, TRUE, NOW())
                    RETURNING id, name, platform, last_seen_at, revoked_at
                    """,
                    (profile_id, name, device_fingerprint, platform),
                ).fetchone()
                device_id = result['id']
            
            conn.commit()
            
            # Fetch the device to return
            device = conn.execute(
                """
                SELECT id, name as display_name, platform, last_seen_at, revoked_at
                FROM tavuno_devices
                WHERE id = %s
                """,
                (device_id,),
            ).fetchone()
            
            return DeviceDto(
                id=device['id'],
                display_name=device['display_name'],
                platform=device['platform'],
                last_seen_at=device['last_seen_at'],
                is_current=True,
                is_revoked=(device['revoked_at'] is not None)
            )

    def revoke_device(self, profile_id: int, device_id: int) -> None:
        """Revoke a device for a profile."""
        with self._db() as conn:
            # Verify device belongs to profile
            device = conn.execute(
                "SELECT id, profile FROM tavuno_devices WHERE id = %s AND profile = %s",
                (device_id, profile_id),
            ).fetchone()
            
            if not device:
                raise ValueError("Device not found")
            
            # Revoke the device
            conn.execute(
                "UPDATE tavuno_devices SET revoked_at = NOW() WHERE id = %s",
                (device_id,),
            )
            conn.commit()
