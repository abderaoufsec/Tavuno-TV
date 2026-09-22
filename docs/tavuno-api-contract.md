# Tavuno API Contract for Android TV Client

## Overview
This document describes the existing Tavuno backend API endpoints and their usage by the Android TV client. All endpoints are already implemented in the Tavuno backend.

## Base URL
- Development: `http://localhost:8000`
- Production: `https://api.tavuno.com` (to be configured)

## Authentication Flow

### 1. Register (New Account)
**EXISTING ENDPOINT**: `POST /v1/auth/register`

**Request**:
```json
{
  "username": "string",
  "email": "string",
  "password": "string"
}
```

**Response** (201):
```json
{
  "profile_id": 1,
  "email": "user@example.com",
  "display_name": "username",
  "status": "active"
}
```

### 2. Login
**EXISTING ENDPOINT**: `POST /v1/auth/login`

**Request**:
```json
{
  "email": "string",
  "password": "string",
  "device_fingerprint": "string",
  "platform": "android-tv"
}
```

**Response** (200):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "access_expires_at": 1790379645.0,
  "refresh_expires_at": 1792970745.0,
  "profile": {
    "id": 1,
    "email": "user@example.com",
    "display_name": "username"
  }
}
```

### 3. Refresh Token
**EXISTING ENDPOINT**: `POST /v1/auth/refresh`

**Request**:
```json
{
  "refresh_token": "string"
}
```

**Response** (200):
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "access_expires_at": 1790379670.0,
  "refresh_expires_at": 1792970770.0,
  "profile": {
    "id": 1,
    "email": "user@example.com",
    "display_name": "username"
  }
}
```

### 4. Logout
**EXISTING ENDPOINT**: `POST /v1/auth/logout`

**Request**:
```json
{
  "refresh_token": "string"
}
```

**Response** (204): No content

### 5. Get Current User
**EXISTING ENDPOINT**: `GET /v1/auth/me`

**Headers**: `Authorization: Bearer {access_token}`

**Response** (200):
```json
{
  "id": 1,
  "email": "user@example.com",
  "display_name": "username",
  "role": "user"
}
```

### 6. Get Subscription
**EXISTING ENDPOINT**: `GET /v1/auth/subscription`

**Headers**: `Authorization: Bearer {access_token}`

**Response** (200):
```json
{
  "subscription": {
    "id": 1,
    "status": "active",
    "plan_id": 1,
    "starts_at": "2024-01-01T00:00:00Z",
    "ends_at": null
  },
  "plan": {
    "id": 1,
    "name": "Premium",
    "max_concurrent_streams": 2,
    "max_devices": 4
  },
  "entitlements": [
    {
      "resource_type": "channel",
      "resource_key": "8814"
    }
  ]
}
```

## Catalog Endpoints

### 7. Get Home
**EXISTING ENDPOINT**: `GET /v1/home`

**Response** (200):
```json
{
  "channels": [
    {
      "id": 8814,
      "name": "M10 Test Channel",
      "slug": "m10-test-channel",
      "category_id": null,
      "logo": null,
      "is_active": true
    }
  ],
  "categories": [
    {
      "id": 1,
      "name": "News",
      "kind": "live",
      "parent_id": null,
      "sort_order": 1,
      "is_active": true
    }
  ],
  "movies": [],
  "series": []
}
```

### 8. Get Channels
**EXISTING ENDPOINT**: `GET /v1/channels?category_id={id}`

**Response** (200):
```json
[
  {
    "id": 8814,
    "name": "M10 Test Channel",
    "slug": "m10-test-channel",
    "category_id": null,
    "logo": null,
    "is_active": true
  }
]
```

### 9. Get Channel Details
**EXISTING ENDPOINT**: `GET /v1/channels/{channel_id}/details`

**Response** (200):
```json
{
  "id": 8814,
  "name": "M10 Test Channel",
  "slug": "m10-test-channel",
  "category_id": null,
  "logo": null,
  "is_active": true,
  "description": null,
  "category_name": null,
  "playback_available": true
}
```

### 10. Get Categories
**EXISTING ENDPOINT**: `GET /v1/categories?kind={kind}`

**Response** (200):
```json
[
  {
    "id": 1,
    "name": "News",
    "kind": "live",
    "parent_id": null,
    "sort_order": 1,
    "is_active": true
  }
]
```

### 11. Get Category
**EXISTING ENDPOINT**: `GET /v1/categories/{category_id}`

**Response** (200):
```json
{
  "id": 1,
  "name": "News",
  "kind": "live",
  "parent_id": null,
  "sort_order": 1,
  "is_active": true
}
```

### 12. Get Movies
**EXISTING ENDPOINT**: `GET /v1/movies?category_id={id}`

**Response** (200):
```json
[
  {
    "id": 1,
    "title": "Movie Title",
    "slug": "movie-title",
    "category_id": null,
    "synopsis": null,
    "release_year": null,
    "is_active": true
  }
]
```

### 13. Get Movie Details
**EXISTING ENDPOINT**: `GET /v1/movies/{movie_id}/details`

**Response** (200):
```json
{
  "id": 1,
  "title": "Movie Title",
  "slug": "movie-title",
  "category_id": null,
  "synopsis": "Movie description",
  "release_year": 2024,
  "is_active": true,
  "poster": null,
  "backdrop": null,
  "duration": "2h 30m",
  "category_name": "Action",
  "genres": ["Action", "Drama"],
  "playback_available": true
}
```

### 14. Get Series
**EXISTING ENDPOINT**: `GET /v1/series?category_id={id}`

**Response** (200):
```json
[
  {
    "id": 1,
    "title": "Series Title",
    "slug": "series-title",
    "category_id": null,
    "synopsis": null,
    "is_active": true
  }
]
```

### 15. Get Series Details
**EXISTING ENDPOINT**: `GET /v1/series/{series_id}/details`

**Response** (200):
```json
{
  "id": 1,
  "title": "Series Title",
  "slug": "series-title",
  "category_id": null,
  "synopsis": "Series description",
  "is_active": true,
  "poster": null,
  "backdrop": null,
  "release_year": 2024,
  "category_name": "Drama",
  "seasons": [
    {
      "season_number": 1,
      "name": "Season 1",
      "episode_count": 10
    }
  ],
  "episode_count": 10,
  "playback_available": true
}
```

## Sports Endpoints

### 16. Get Competitions
**EXISTING ENDPOINT**: `GET /v1/sports/competitions?sport={sport}`

**Response** (200):
```json
[
  {
    "id": 1,
    "name": "Premier League",
    "slug": "premier-league",
    "sport": "football",
    "category_id": null,
    "external_id": null,
    "is_active": true
  }
]
```

### 17. Get Competition
**EXISTING ENDPOINT**: `GET /v1/sports/competitions/{competition_id}`

**Response** (200):
```json
{
  "id": 1,
  "name": "Premier League",
  "slug": "premier-league",
  "sport": "football",
  "category_id": null,
  "external_id": null,
  "is_active": true
}
```

### 18. Get Competition Matches
**EXISTING ENDPOINT**: `GET /v1/sports/competitions/{competition_id}/matches?status={status}&limit={limit}`

**Response** (200):
```json
[
  {
    "id": 1,
    "competition_id": 1,
    "home_team_id": 1,
    "away_team_id": 2,
    "channel_id": 8814,
    "kickoff": "2024-01-01T15:00:00Z",
    "status": "live",
    "home_score": 2,
    "away_score": 1,
    "external_id": null,
    "is_active": true
  }
]
```

### 19. Get All Matches
**EXISTING ENDPOINT**: `GET /v1/sports/matches?status={status}&limit={limit}`

**Response** (200):
```json
[
  {
    "id": 1,
    "competition_id": 1,
    "home_team_id": 1,
    "away_team_id": 2,
    "channel_id": 8814,
    "kickoff": "2024-01-01T15:00:00Z",
    "status": "live",
    "home_score": 2,
    "away_score": 1,
    "external_id": null,
    "is_active": true
  }
]
```

### 20. Get Match Details
**EXISTING ENDPOINT**: `GET /v1/sports/matches/{match_id}/details`

**Response** (200):
```json
{
  "id": 1,
  "competition_id": 1,
  "competition_name": "Premier League",
  "home_team_id": 1,
  "home_team_name": "Team A",
  "home_team_logo": null,
  "away_team_id": 2,
  "away_team_name": "Team B",
  "away_team_logo": null,
  "channel_id": 8814,
  "channel_name": "M10 Test Channel",
  "kickoff": "2024-01-01T15:00:00Z",
  "status": "live",
  "home_score": 2,
  "away_score": 1,
  "is_active": true
}
```

## EPG Endpoints

### 21. Get EPG
**EXISTING ENDPOINT**: `GET /v1/epg?channel_id={id}`

**Response** (200):
```json
[
  {
    "id": 1,
    "title": "Program Title",
    "starts_at": "2024-01-01T10:00:00Z",
    "ends_at": "2024-01-01T11:00:00Z",
    "description": "Program description",
    "channel_id": 8814
  }
]
```

### 22. Get Channel Now/Next
**EXISTING ENDPOINT**: `GET /v1/epg/channel/{channel_id}/now-next`

**Response** (200):
```json
{
  "channel_id": 8814,
  "now": {
    "id": 1,
    "title": "Current Program",
    "starts_at": "2024-01-01T10:00:00Z",
    "ends_at": "2024-01-01T11:00:00Z",
    "description": "Current program description"
  },
  "next": {
    "id": 2,
    "title": "Next Program",
    "starts_at": "2024-01-01T11:00:00Z",
    "ends_at": "2024-01-01T12:00:00Z",
    "description": "Next program description"
  },
  "later": {
    "id": 3,
    "title": "Later Program",
    "starts_at": "2024-01-01T12:00:00Z",
    "ends_at": "2024-01-01T13:00:00Z",
    "description": "Later program description"
  }
}
```

## Playback Endpoints

### 23. Authorize Live Playback
**EXISTING ENDPOINT**: `POST /v1/playback/live/{channel_id}`

**Headers**: `Authorization: Bearer {access_token}`

**Response** (200):
```json
{
  "session_id": 1,
  "channel_id": 8814,
  "channel_name": "M10 Test Channel",
  "expires_at": "2024-01-01T12:00:00Z",
  "playback": {
    "protocol": "hls",
    "url": "http://localhost:8080/media/tavuno/channel_8814/llhls.m3u8?token=1.1234567890.abc123...",
    "stream_name": "channel_8814"
  }
}
```

**Error Responses**:
- 401: Invalid/missing authorization
- 402: Active subscription required
- 403: Device limit, entitlement, or account inactive
- 404: Channel not found
- 429: Concurrent stream limit reached
- 500: Authorization failed

### 24. Authorize Movie Playback
**EXISTING ENDPOINT**: `POST /v1/playback/movie/{movie_id}`

**Headers**: `Authorization: Bearer {access_token}`

**Response** (200):
```json
{
  "authorized": true,
  "movie_id": 1,
  "title": "Movie Title",
  "provider": "dispatcharr",
  "external_id": "movie_external_id",
  "message": "VOD playback authorization - TODO: implement full authorization logic"
}
```

**Note**: VOD playback authorization is partially implemented and marked as TODO in backend.

### 25. Authorize Episode Playback
**EXISTING ENDPOINT**: `POST /v1/playback/episode/{episode_id}`

**Headers**: `Authorization: Bearer {access_token}`

**Response** (200):
```json
{
  "authorized": true,
  "episode_id": 1,
  "title": "Episode Title",
  "series_id": 1,
  "series_title": "Series Title",
  "season_id": 1,
  "message": "VOD playback authorization - TODO: implement full authorization logic"
}
```

**Note**: VOD playback authorization is partially implemented and marked as TODO in backend.

### 26. Playback Heartbeat
**EXISTING ENDPOINT**: `POST /v1/playback/heartbeat`

**Request**:
```json
{
  "session_id": 1
}
```

**Response** (200):
```json
{
  "session_id": 1,
  "status": "active",
  "expires_at": "2024-01-01T12:02:00Z"
}
```

### 27. Stop Playback
**EXISTING ENDPOINT**: `POST /v1/playback/stop`

**Request**:
```json
{
  "session_id": 1
}
```

**Response** (200):
```json
{
  "session_id": 1,
  "status": "stopped"
}
```

### 28. Verify Media Token
**EXISTING ENDPOINT**: `GET /v1/media/verify?token={token}`

**Response** (200):
```json
{
  "valid": true,
  "session_id": 1,
  "profile_id": 1,
  "device_id": 10
}
```

## Device Endpoints

### 29. Register Device
**EXISTING ENDPOINT**: `POST /v1/devices/register`

**Request**:
```json
{
  "profile_id": 1,
  "name": "Living Room TV",
  "device_key": "unique-device-key",
  "platform": "android-tv"
}
```

**Response** (201):
```json
{
  "id": 10,
  "profile": 1,
  "name": "Living Room TV",
  "device_key": "unique-device-key",
  "platform": "android-tv",
  "is_active": true,
  "last_seen_at": "2024-01-01T10:00:00Z"
}
```

### 30. Get Devices
**EXISTING ENDPOINT**: `GET /v1/devices`

**Headers**: `Authorization: Bearer {access_token}`

**Response** (200):
```json
[
  {
    "id": 10,
    "profile": 1,
    "name": "Living Room TV",
    "device_key": "unique-device-key",
    "platform": "android-tv",
    "is_active": true,
    "last_seen_at": "2024-01-01T10:00:00Z"
  }
]
```

### 31. Revoke Device
**EXISTING ENDPOINT**: `DELETE /v1/devices/{device_id}`

**Headers**: `Authorization: Bearer {access_token}`

**Response** (204): No content

## Operations Endpoints

### 32. Health Check
**EXISTING ENDPOINT**: `GET /health`

**Response** (200):
```json
{
  "status": "ok",
  "services": {
    "postgres": "ok",
    "redis": "ok",
    "dispatcharr": "ok",
    "ome": "ok"
  },
  "last_dispatcharr_sync": "2024-01-01T10:00:00Z"
}
```

## Required New Endpoints

### Series Season/Episode Data
**REQUIRED NEW ENDPOINT**: `GET /v1/series/{series_id}/seasons`

**Rationale**: The current series details endpoint returns season counts but not detailed season/episode data needed for episode selection.

**Proposed Response** (200):
```json
{
  "series_id": 1,
  "seasons": [
    {
      "season_number": 1,
      "name": "Season 1",
      "episodes": [
        {
          "id": 1,
          "title": "Episode 1",
          "episode_number": 1,
          "season_number": 1,
          "synopsis": "Episode description",
          "duration": "45m",
          "thumbnail": null
        }
      ]
    }
  ]
}
```

## Android Client Implementation Notes

### Authentication Storage
- Store `access_token` and `refresh_token` securely using Android DataStore
- Implement automatic token refresh when access token expires
- Handle 401 responses by attempting token refresh, then redirect to login if failed

### Device Management
- Generate unique device fingerprint on first launch
- Auto-register device on first successful login
- Store device_key locally for session management

### Playback Flow
1. Request playback authorization with JWT access token
2. Receive short-lived playback URL with token
3. Use Media3/ExoPlayer to play HLS stream
4. Send periodic heartbeats to extend session
5. Call stop endpoint when playback ends

### Error Handling
- 401/403: Redirect to login or show subscription error
- 402: Show subscription required message
- 404: Show content not found
- 429: Show concurrent stream limit message
- 500+: Show generic error with retry option

### EPG Display
- Use `/v1/epg/channel/{channel_id}/now-next` for current program info
- Display current program on channel cards in Live TV
- Show now/next in channel detail view

### Sports Display
- Use `status=live` filter for live matches
- Use `status=upcoming` filter for upcoming matches
- Display match details with team names and scores
- Navigate to channel when match selected

### VOD Display
- Movies and series endpoints return basic data
- Details endpoints provide extended metadata
- VOD playback authorization is partially implemented - may need backend completion
