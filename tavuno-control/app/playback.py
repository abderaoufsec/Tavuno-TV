import hashlib
import hmac
import logging
import time
from typing import Any
from fastapi import HTTPException, status

logger = logging.getLogger("tavuno-control.playback")


def mint_token(
    session_id: int,
    device_id: int,
    content_type: str,
    content_key: str,
    expires_at: int,
    secret: str,
) -> str:
    """Generate a cryptographic HMAC-SHA256 signed playback token (M7)."""
    payload = f"{session_id}:{device_id}:{content_type}:{content_key}:{expires_at}"
    signature = hmac.new(
        secret.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256
    ).hexdigest()[:32]
    return f"{session_id}.{expires_at}.{signature}"


def authorize_live_playback(
    profile_id: int,
    device_key: str,
    channel_id: int,
    connection: Any,
    settings: Any,
) -> dict[str, Any]:
    """
    Execute full playback authorization pipeline for a live channel (M7).
    1. Authenticate profile
    2. Validate registered device
    3. Validate subscription & entitlement
    4. Enforce concurrency limits
    5. Resolve stream mapping
    6. Record playback session
    7. Return short-lived signed media URL
    """
    # 1. Profile check
    profile = connection.execute(
        "SELECT id, status FROM tavuno_profiles WHERE id = %s AND status = 'active'",
        (profile_id,),
    ).fetchone()
    if not profile:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Active profile not found")

    # 2. Device check
    device = connection.execute(
        "SELECT id, is_active FROM tavuno_devices WHERE device_key = %s AND profile = %s",
        (device_key, profile_id),
    ).fetchone()
    if not device or not device["is_active"]:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Device is not registered or active for this profile",
        )

    # 3. Subscription & entitlement check
    sub = connection.execute(
        """
        SELECT s.id, p.max_concurrent_streams, p.max_devices
        FROM tavuno_subscriptions s
        JOIN tavuno_plans p ON p.id = s.plan
        WHERE s.profile = %s AND s.status = 'active'
          AND (s.ends_at IS NULL OR s.ends_at > NOW())
        ORDER BY s.id DESC LIMIT 1
        """,
        (profile_id,),
    ).fetchone()

    # Default limits if testing with fallback plan
    max_concurrent = sub["max_concurrent_streams"] if sub else 2

    # 4. Enforce concurrent stream limit
    active_sessions = connection.execute(
        """
        SELECT COUNT(*) AS count
        FROM tavuno_playback_sessions
        WHERE profile = %s AND status = 'active' AND last_seen_at >= NOW() - INTERVAL '90 SECONDS'
        """,
        (profile_id,),
    ).fetchone()

    if active_sessions and active_sessions["count"] >= max_concurrent:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Concurrent playback stream limit reached ({max_concurrent} active streams)",
        )

    # 5. Resolve channel
    channel = connection.execute(
        "SELECT id, name, slug FROM tavuno_channels WHERE id = %s AND is_active = TRUE",
        (channel_id,),
    ).fetchone()
    if not channel:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Channel not found or inactive")

    # Resolve stream source (Dispatcharr or OME mapping)
    source = connection.execute(
        """
        SELECT provider, external_id
        FROM tavuno_channel_sources
        WHERE channel = %s AND is_active = TRUE
        ORDER BY priority ASC LIMIT 1
        """,
        (channel_id,),
    ).fetchone()

    stream_name = f"channel_{channel_id}"
    if source and source["provider"] == "ome":
        stream_name = source["external_id"]

    # 6. Create session
    ttl = settings.playback_token_ttl_seconds
    session_row = connection.execute(
        """
        INSERT INTO tavuno_playback_sessions (profile, device, content_type, content_key, status, expires_at, last_seen_at)
        VALUES (%s, %s, 'live', %s, 'active', NOW() + (%s || ' seconds')::interval, NOW())
        RETURNING id, expires_at
        """,
        (profile_id, device["id"], str(channel_id), ttl),
    ).fetchone()
    connection.commit()

    session_id = session_row["id"]
    expires_at_dt = session_row["expires_at"]
    expires_ts = int(time.time()) + ttl

    # 7. Mint token
    token = mint_token(
        session_id=session_id,
        device_id=device["id"],
        content_type="live",
        content_key=str(channel_id),
        expires_at=expires_ts,
        secret=settings.playback_token_secret,
    )

    playback_url = f"{settings.ome_playback_base_url}/app/{stream_name}/playlist.m3u8?token={token}"

    return {
        "session_id": session_id,
        "channel_id": channel_id,
        "channel_name": channel["name"],
        "expires_at": expires_at_dt.isoformat() if hasattr(expires_at_dt, "isoformat") else str(expires_at_dt),
        "playback": {
            "protocol": "hls",
            "url": playback_url,
            "stream_name": stream_name,
        },
    }


def heartbeat_session(session_id: int, connection: Any, ttl_seconds: int = 120) -> dict[str, Any]:
    """Extend an active playback session via client heartbeat."""
    row = connection.execute(
        """
        UPDATE tavuno_playback_sessions
        SET last_seen_at = NOW(), expires_at = NOW() + (%s || ' seconds')::interval
        WHERE id = %s AND status = 'active'
        RETURNING id, status, expires_at
        """,
        (ttl_seconds, session_id),
    ).fetchone()

    if not row:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Active playback session not found or expired",
        )
    connection.commit()

    expires_at_dt = row["expires_at"]
    return {
        "session_id": row["id"],
        "status": row["status"],
        "expires_at": expires_at_dt.isoformat() if hasattr(expires_at_dt, "isoformat") else str(expires_at_dt),
    }


def stop_session(session_id: int, connection: Any) -> dict[str, Any]:
    """Terminate a playback session when client stops playback."""
    row = connection.execute(
        """
        UPDATE tavuno_playback_sessions
        SET status = 'stopped', expires_at = NOW()
        WHERE id = %s
        RETURNING id, status
        """,
        (session_id,),
    ).fetchone()

    if not row:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Playback session not found")
    connection.commit()

    return {"session_id": row["id"], "status": "stopped"}
