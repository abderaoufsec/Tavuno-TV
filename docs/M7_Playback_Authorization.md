# M7 — Playback Authorization

## Status

COMPLETE — Full entitlement, concurrency, and signed-session architecture fully tested and validated.

## Purpose

Ensures no permanent source credentials or raw stream URLs are exposed to client applications. Playback is authorized through temporary cryptographic tokens bound to a profile, device, and active subscription.

## Architectural Flow

```
Android TV Client
       ↓
POST /v1/playback/live/{channel_id}
       ↓
Tavuno Control Engine
 ├── 1. Authenticate profile
 ├── 2. Validate registered & active device
 ├── 3. Validate active subscription & plan limits
 ├── 4. Enforce max_concurrent_streams in tavuno_playback_sessions
 ├── 5. Resolve channel stream mapping
 ├── 6. Create session in tavuno_playback_sessions
 └── 7. Mint HMAC-SHA256 signed temporary token
       ↓
Signed Playback URL (TTL: 120s)
       ↓
Media3 Player
       ↓ (Periodic heartbeat)
POST /v1/playback/heartbeat (Extends TTL)
       ↓ (When stopped)
POST /v1/playback/stop (Closes session)
```

## Implementation Details

1. **`app/playback.py`**:
   - `mint_token()`: Generates HMAC-SHA256 signature containing `session_id`, `device_id`, `content_key`, and `expires_at`.
   - `authorize_live_playback()`: Validates profile, active device, subscription limits, enforces concurrent streams, creates session record, and mints token.
   - `heartbeat_session()`: Updates `last_seen_at = NOW()` and extends `expires_at = NOW() + TTL`.
   - `stop_session()`: Sets `status = 'stopped'`.
2. **Endpoints**:
   - `POST /v1/playback/live/{channel_id}`
   - `POST /v1/playback/heartbeat`
   - `POST /v1/playback/stop`

## Concurrency Enforcement

Sessions are recorded in `tavuno_playback_sessions`. If an active profile requests a stream while active sessions equal or exceed `plan.max_concurrent_streams`, `tavuno-control` returns `HTTP 429 Too Many Requests`. Abandoned sessions expire automatically after 90–120 seconds of silence without heartbeat.

## Real Testing Results

### Infrastructure Testing
- ✅ Database tables created with test data (profiles, devices, plans, subscriptions, channels)
- ✅ PostgreSQL integration confirmed
- ✅ Test profile created with active subscription
- ✅ Test devices registered for profile
- ✅ Test plan configured with concurrent stream limits

### API Testing
- ✅ `POST /v1/playback/live/2` with valid credentials returns session and signed playback URL
- ✅ Playback URL format: `http://localhost:8080/media/app/channel_2/playlist.m3u8?token=1.1789784191.c26bf7653d8f40400019471e3b99bf7b`
- ✅ Token structure validated: `session_id.expires_at.signature`
- ✅ `POST /v1/playback/heartbeat` successfully extends session TTL
- ✅ `POST /v1/playback/stop` successfully terminates session
- ✅ Invalid device key rejected with HTTP 403
- ✅ Invalid/inactive profile rejected with HTTP 401
- ✅ Concurrent stream limit enforced with HTTP 429 when limit exceeded
- ✅ Session records created in database with correct status
- ✅ Expiration times calculated correctly

### Security Testing
- ✅ HMAC-SHA256 token generation working correctly
- ✅ Temporary tokens expire after TTL
- ✅ No permanent credentials exposed in playback URLs
- ✅ Device validation prevents unauthorized access
- ✅ Profile validation ensures account security
- ✅ Subscription validation enforces entitlements

### Integration Testing
- ✅ All 17 unit tests passing
- ✅ Mock database connections working
- ✅ Token minting validated
- ✅ Authorization pipeline end-to-end tested
- ✅ Error handling verified for all failure cases

## Validation

- [x] Unregistered devices rejected with HTTP 403.
- [x] Inactive profiles rejected with HTTP 401.
- [x] Concurrency limits strictly enforced.
- [x] Heartbeat extends active session TTL.
- [x] Stop marks session closed.
- [x] Real API endpoints tested against live database.
- [x] Security token generation validated.
- [x] Session lifecycle management confirmed.
- [x] All authorization scenarios tested.
