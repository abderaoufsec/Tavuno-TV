import hashlib
import hmac
import logging
import time
from typing import Any
from fastapi import HTTPException, status, Header
import jwt

logger = logging.getLogger("tavuno-control.playback")

JWT_SECRET = "tavuno-jwt-secret-key"
JWT_ALGORITHM = "HS256"
JWT_EXPIRATION_HOURS = 24


def generate_auth_token(profile_id: int, device_id: int) -> str:
    """Generate JWT authentication token for profile and device."""
    payload = {
        "profile_id": profile_id,
        "device_id": device_id,
        "exp": time.time() + (JWT_EXPIRATION_HOURS * 3600),
        "iat": time.time(),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)


def verify_auth_token(token: str) -> dict[str, Any]:
    """Verify JWT authentication token and return payload."""
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")


def get_profile_from_token(authorization: str | None = None) -> dict[str, Any]:
    """Extract and verify profile from Authorization header."""
    if authorization is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authorization header required")
    
    # Handle both FastAPI Header objects and raw strings
    auth_value = str(authorization) if not isinstance(authorization, str) else authorization
    
    if not auth_value.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authorization header")
    
    token = auth_value.replace("Bearer ", "")
    payload = verify_auth_token(token)
    
    return {
        "profile_id": payload["profile_id"],
        "device_id": payload["device_id"],
    }


def verify_playback_token(token: str, connection: Any, secret: str) -> dict[str, Any]:
    """Verify a playback token and return session info."""
    if not token or "." not in token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token format")
    
    parts = token.split(".")
    if len(parts) != 3:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token format")
    
    try:
        session_id = int(parts[0])
        expires_at = int(parts[1])
        signature = parts[2]
    except ValueError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token format")
    
    # Check expiration
    if int(time.time()) > expires_at:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
    
    # Verify signature
    # For security, we need the full payload including device_id and content_key
    # So we look up the session and verify
    session = connection.execute(
        """
        SELECT id, profile, device, content_type, content_key, status, expires_at
        FROM tavuno_playback_sessions
        WHERE id = %s AND status = 'active' AND expires_at > NOW()
        """,
        (session_id,),
    ).fetchone()
    
    if not session:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Session not found or expired")
    
    # Verify signature with actual session data
    actual_payload = f"{session_id}:{session['device']}:{session['content_type']}:{session['content_key']}:{expires_at}"
    actual_signature = hmac.new(
        secret.encode("utf-8"), actual_payload.encode("utf-8"), hashlib.sha256
    ).hexdigest()[:32]
    
    if not hmac.compare_digest(signature, actual_signature):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token signature")
    
    return {
        "session_id": session["id"],
        "profile_id": session["profile"],
        "device_id": session["device"],
        "content_type": session["content_type"],
        "content_key": session["content_key"],
        "status": session["status"],
    }


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
        "SELECT id, status FROM tavuno_profiles WHERE id = %s",
        (profile_id,),
    ).fetchone()
    if not profile:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Profile not found")
    if profile['status'] != 'active':
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is not active"
        )

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

    if not sub:
        raise HTTPException(
            status_code=status.HTTP_402_PAYMENT_REQUIRED,
            detail="Active subscription required for playback",
        )

    max_concurrent = sub["max_concurrent_streams"]
    max_devices = sub["max_devices"]

    # 2. Device check (device_id comes from JWT, should always be registered)
    device = connection.execute(
        "SELECT id, is_active FROM tavuno_devices WHERE device_key = %s AND profile = %s",
        (device_key, profile_id),
    ).fetchone()
    if not device or not device["is_active"]:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="device_not_registered",
        )
    device_id = device["id"]

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

    # 5.5. Entitlement check - verify profile has access to this channel
    entitlement = connection.execute(
        """
        SELECT e.id FROM tavuno_entitlements e
        JOIN tavuno_subscriptions s ON s.id = e.subscription
        WHERE s.profile = %s AND s.status = 'active'
          AND (s.ends_at IS NULL OR s.ends_at > NOW())
          AND e.resource_type = 'channel' AND e.resource_key = %s AND e.is_active = TRUE
        """,
        (profile_id, str(channel_id)),
    ).fetchone()
    
    if not entitlement:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Channel not included in subscription entitlements",
        )

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
        (profile_id, device_id, str(channel_id), ttl),
    ).fetchone()
    connection.commit()

    session_id = session_row["id"]
    expires_at_dt = session_row["expires_at"]
    expires_ts = int(time.time()) + ttl

    # 7. Mint token
    token = mint_token(
        session_id=session_id,
        device_id=device_id,
        content_type="live",
        content_key=str(channel_id),
        expires_at=expires_ts,
        secret=settings.playback_token_secret,
    )

    playback_url = f"{settings.ome_playback_base_url}/tavuno/{stream_name}/llhls.m3u8?token={token}"

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
        # Check if session exists but is stopped/expired
        existing = connection.execute(
            "SELECT id, status FROM tavuno_playback_sessions WHERE id = %s",
            (session_id,),
        ).fetchone()
        if existing and existing["status"] == "stopped":
            raise HTTPException(
                status_code=status.HTTP_410_GONE,
                detail="Playback session has been stopped",
            )
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


def authorize_movie_playback(
    profile_id: int,
    device_key: str,
    movie_id: int,
    connection: Any,
    settings: Any,
) -> dict[str, Any]:
    """
    Execute full playback authorization pipeline for a movie (M12).
    1. Authenticate profile
    2. Validate registered device
    3. Validate subscription & entitlement
    4. Enforce concurrency limits
    5. Resolve movie and stream URL
    6. Record playback session
    7. Return short-lived signed media URL
    """
    # 1. Profile check
    profile = connection.execute(
        "SELECT id, status FROM tavuno_profiles WHERE id = %s",
        (profile_id,),
    ).fetchone()
    if not profile:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Profile not found")
    if profile['status'] != 'active':
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is not active"
        )

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

    if not sub:
        raise HTTPException(
            status_code=status.HTTP_402_PAYMENT_REQUIRED,
            detail="Active subscription required for playback",
        )

    max_concurrent = sub["max_concurrent_streams"]
    max_devices = sub["max_devices"]

    # 2. Device check (device_id comes from JWT, should always be registered)
    device = connection.execute(
        "SELECT id, is_active FROM tavuno_devices WHERE device_key = %s AND profile = %s",
        (device_key, profile_id),
    ).fetchone()
    if not device or not device["is_active"]:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="device_not_registered",
        )
    device_id = device["id"]

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

    # 5. Resolve movie
    movie = connection.execute(
        "SELECT id, title, slug, stream_url FROM tavuno_movies WHERE id = %s AND is_active = TRUE",
        (movie_id,),
    ).fetchone()
    if not movie:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Movie not found or inactive")

    # Check if movie has a stream URL
    if not movie["stream_url"]:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Movie stream URL not available. Please try again later or contact support.",
        )

    # 6. Create session
    ttl = settings.playback_token_ttl_seconds
    session_row = connection.execute(
        """
        INSERT INTO tavuno_playback_sessions (profile, device, content_type, content_key, status, expires_at, last_seen_at)
        VALUES (%s, %s, 'movie', %s, 'active', NOW() + (%s || ' seconds')::interval, NOW())
        RETURNING id, expires_at
        """,
        (profile_id, device_id, str(movie_id), ttl),
    ).fetchone()
    connection.commit()

    session_id = session_row["id"]
    expires_at_dt = session_row["expires_at"]
    expires_ts = int(time.time()) + ttl

    # 7. Mint token
    token = mint_token(
        session_id=session_id,
        device_id=device_id,
        content_type="movie",
        content_key=str(movie_id),
        expires_at=expires_ts,
        secret=settings.playback_token_secret,
    )

    # Use the stream URL directly (no OME wrapping for VOD)
    playback_url = f"{movie['stream_url']}?token={token}"

    return {
        "session_id": session_id,
        "movie_id": movie_id,
        "title": movie["title"],
        "expires_at": expires_at_dt.isoformat() if hasattr(expires_at_dt, "isoformat") else str(expires_at_dt),
        "playback": {
            "protocol": "hls",
            "url": playback_url,
            "stream_name": f"movie_{movie_id}",
        },
    }


def authorize_episode_playback(
    profile_id: int,
    device_key: str,
    episode_id: int,
    connection: Any,
    settings: Any,
) -> dict[str, Any]:
    """
    Execute full playback authorization pipeline for an episode (M12).
    1. Authenticate profile
    2. Validate registered device
    3. Validate subscription & entitlement
    4. Enforce concurrency limits
    5. Resolve episode and stream URL
    6. Record playback session
    7. Return short-lived signed media URL
    """
    # 1. Profile check
    profile = connection.execute(
        "SELECT id, status FROM tavuno_profiles WHERE id = %s",
        (profile_id,),
    ).fetchone()
    if not profile:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Profile not found")
    if profile['status'] != 'active':
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is not active"
        )

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

    if not sub:
        raise HTTPException(
            status_code=status.HTTP_402_PAYMENT_REQUIRED,
            detail="Active subscription required for playback",
        )

    max_concurrent = sub["max_concurrent_streams"]
    max_devices = sub["max_devices"]

    # 2. Device check (device_id comes from JWT, should always be registered)
    device = connection.execute(
        "SELECT id, is_active FROM tavuno_devices WHERE device_key = %s AND profile = %s",
        (device_key, profile_id),
    ).fetchone()
    if not device or not device["is_active"]:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="device_not_registered",
        )
    device_id = device["id"]

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

    # 5. Resolve episode
    episode = connection.execute(
        """
        SELECT e.id, e.title, e.episode_number, e.stream_url, s.id as season_id, s.series_id, s.title as series_title
        FROM tavuno_episodes e
        JOIN tavuno_seasons s ON s.id = e.season
        WHERE e.id = %s AND e.is_active = TRUE
        """,
        (episode_id,),
    ).fetchone()
    if not episode:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Episode not found or inactive")

    # Check if episode has a stream URL
    if not episode["stream_url"]:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Episode stream URL not available. Please try again later or contact support.",
        )

    # 6. Create session
    ttl = settings.playback_token_ttl_seconds
    session_row = connection.execute(
        """
        INSERT INTO tavuno_playback_sessions (profile, device, content_type, content_key, status, expires_at, last_seen_at)
        VALUES (%s, %s, 'episode', %s, 'active', NOW() + (%s || ' seconds')::interval, NOW())
        RETURNING id, expires_at
        """,
        (profile_id, device_id, str(episode_id), ttl),
    ).fetchone()
    connection.commit()

    session_id = session_row["id"]
    expires_at_dt = session_row["expires_at"]
    expires_ts = int(time.time()) + ttl

    # 7. Mint token
    token = mint_token(
        session_id=session_id,
        device_id=device_id,
        content_type="episode",
        content_key=str(episode_id),
        expires_at=expires_ts,
        secret=settings.playback_token_secret,
    )

    # Use the stream URL directly (no OME wrapping for VOD)
    playback_url = f"{episode['stream_url']}?token={token}"

    return {
        "session_id": session_id,
        "episode_id": episode_id,
        "title": episode["title"],
        "series_id": episode["series_id"],
        "series_title": episode["series_title"],
        "season_id": episode["season_id"],
        "expires_at": expires_at_dt.isoformat() if hasattr(expires_at_dt, "isoformat") else str(expires_at_dt),
        "playback": {
            "protocol": "hls",
            "url": playback_url,
            "stream_name": f"episode_{episode_id}",
        },
    }
