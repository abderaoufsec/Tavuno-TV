# Tavuno TV --- Technical Architecture

## 1. System architecture

``` text
                         TAVUNO TV
                            │
          ┌─────────────────┼──────────────────┐
          │                 │                  │
          ▼                 ▼                  ▼
      Android TV       Tavuno Admin        Operations
          │                 │                  │
          └─────────────────┼──────────────────┘
                            │
                            ▼
                    TAVUNO CONTROL
                            │
          ┌─────────────────┼──────────────────┐
          │                 │                  │
          ▼                 ▼                  ▼
      Directus          Dispatcharr          OME
          │                 │                  │
          ▼                 ▼                  ▼
     PostgreSQL       IPTV sources        Media delivery
          │                 │                  │
          └─────────────────┼──────────────────┘
                            │
                          Redis
```

------------------------------------------------------------------------

## 2. Component responsibilities

### Android TV

Use the StreamVault codebase as the starting point after
commercial/source-use permission is confirmed.

Responsibilities:

-   authentication
-   device registration
-   catalog rendering
-   EPG
-   channel browsing
-   sports browsing
-   VOD browsing
-   playback UI
-   Media3 playback
-   favorites
-   history
-   profiles
-   settings
-   telemetry

The TV client should never contain secret server credentials.

------------------------------------------------------------------------

## 3. Directus

Directus is the business-data foundation.

Use it for:

-   users
-   content metadata
-   categories
-   plans
-   subscriptions
-   entitlements
-   devices
-   images
-   localization
-   admin operations
-   permissions
-   audit-oriented workflows

Do not expose privileged Directus credentials to the Android client.

------------------------------------------------------------------------

## 4. PostgreSQL

PostgreSQL is the source of truth for Tavuno business state.

Core tables:

``` text
users
profiles
devices
device_sessions

plans
subscriptions
entitlements

categories
channels
channel_sources

epg_channels
epg_programmes

movies
series
seasons
episodes

playback_sessions
stream_events

audit_logs
```

External IDs should be stored for middleware/media systems:

``` text
dispatcharr_channel_id
dispatcharr_stream_id
ome_application
ome_stream
```

Never make an external system's internal ID the only Tavuno identifier.

------------------------------------------------------------------------

## 5. Dispatcharr integration

Dispatcharr is responsible for IPTV middleware functions.

Tavuno should consume normalized information from Dispatcharr instead of
rebuilding:

-   M3U import
-   Xtream import
-   EPG matching
-   stream filtering
-   failover
-   stream profiles
-   VOD metadata
-   output generation
-   stream statistics

Integration pattern:

``` text
Tavuno control
     │
     │ API/integration
     ▼
Dispatcharr
     │
     ├── source A
     ├── source B
     └── source C
```

Tavuno's database stores the Tavuno-facing representation.

------------------------------------------------------------------------

## 6. OvenMediaEngine integration

OME is the media delivery layer.

Recommended flow:

``` text
authorized source
       ↓
OME ingest
       ↓
transcode only when needed
       ↓
ABR ladder
       ↓
LL-HLS / WebRTC / HLS
       ↓
Tavuno player
```

Start with HLS/LL-HLS.

Add WebRTC only when a real low-latency requirement exists.

------------------------------------------------------------------------

## 7. Playback authorization

This is one of the most important Tavuno services.

Endpoint concept:

``` text
POST /v1/playback/live/{channelId}
```

Server sequence:

``` text
1. Validate access token
2. Identify user
3. Identify device
4. Validate device status
5. Validate subscription
6. Validate entitlement
7. Count active sessions
8. Enforce concurrent-stream limit
9. Resolve channel
10. Resolve healthy source
11. Create playback session
12. Return short-lived playback authorization
```

Response concept:

``` json
{
  "session_id": "uuid",
  "expires_at": "timestamp",
  "playback": {
    "protocol": "hls",
    "url": "authorized-playback-url"
  }
}
```

Do not return permanent source credentials.

------------------------------------------------------------------------

## 8. Session lifecycle

``` text
START
  ↓
PLAYING
  ↓
HEARTBEAT
  ↓
PLAYING
  ↓
STOP / TIMEOUT
  ↓
CLOSED
```

Heartbeat should update:

``` text
last_seen
device
channel
session
```

A server-side timeout closes abandoned sessions.

------------------------------------------------------------------------

## 9. Concurrent playback

Example:

``` text
Plan:
  max_devices = 3
  max_streams = 2
```

User can have:

``` text
TV 1 → playing
TV 2 → playing
Phone → registered but not playing
```

A third simultaneous playback request is rejected until one session
closes or expires.

------------------------------------------------------------------------

## 10. EPG pipeline

``` text
EPG source
   ↓
XMLTV / provider format
   ↓
Dispatcharr
   ↓
channel matching
   ↓
Tavuno EPG records
   ↓
Tavuno API
   ↓
Android TV
```

The TV app should receive a clean Tavuno EPG model rather than
provider-specific XML.

------------------------------------------------------------------------

## 11. VOD pipeline

``` text
Authorized VOD source
       ↓
Dispatcharr / storage
       ↓
metadata normalization
       ↓
Tavuno catalog
       ↓
entitlement
       ↓
playback authorization
       ↓
OME / storage delivery
       ↓
Media3
```

------------------------------------------------------------------------

## 12. Sports model

Separate sports metadata from video delivery.

``` text
Competition
  ↓
Season
  ↓
Match
  ├── home_team
  ├── away_team
  ├── kickoff
  ├── status
  └── broadcast mapping
          ↓
      channel/source
```

The sports data layer identifies what is happening.

The media layer delivers the authorized video.

Do not hard-code sports stream URLs into the Android app.

------------------------------------------------------------------------

## 13. Security boundaries

### Android TV knows

-   public API URL
-   authenticated user token
-   device identifier
-   temporary playback authorization

### Android TV must NOT know

-   Dispatcharr admin credentials
-   OME admin credentials
-   database credentials
-   source-provider credentials
-   server private keys
-   service-role API keys

### Server knows

Everything required to authorize and orchestrate playback.

------------------------------------------------------------------------

## 14. Deployment

Initial development:

``` text
Docker Compose
│
├── directus
├── postgres
├── redis
├── dispatcharr
├── ovenmediaengine
├── tavuno-control
├── prometheus
├── grafana
└── caddy
```

Production later:

``` text
                Load Balancer
                     │
        ┌────────────┴────────────┐
        │                         │
   Control/API                 Admin
        │
   ┌────┴─────┐
   │          │
Directus    Redis
   │
PostgreSQL

        Media plane
             │
      ┌──────┴──────┐
      │             │
    OME Origin    OME Edge
      │             │
      └──────┬──────┘
             │
           Users
```

Do not introduce Kubernetes during the prototype.

Docker Compose is enough until there is a real scaling requirement.

------------------------------------------------------------------------

## 15. Monitoring

Prometheus should collect:

``` text
CPU
RAM
network
stream count
active sessions
errors
latency
transcoding load
OME health
Dispatcharr health
API latency
database health
```

Grafana dashboards:

``` text
Tavuno Overview
Live Streams
Playback Errors
Server Resources
Users
Sessions
Media Infrastructure
```

------------------------------------------------------------------------

## 16. Failure handling

Every stream should eventually support:

``` text
PRIMARY
   │
   ├── healthy → use
   │
   └── unhealthy
          ↓
       BACKUP 1
          ↓
       BACKUP 2
          ↓
       unavailable
```

Dispatcharr already provides automatic failover and stream monitoring
functionality that we should leverage rather than rebuild.

------------------------------------------------------------------------

## 17. Design rule

Tavuno TV is the **product**.

Dispatcharr, OME, Directus, PostgreSQL, Redis and monitoring are
**infrastructure**.

Keep that distinction throughout development.

If a feature already exists reliably in infrastructure, integrate it.

If it defines how Tavuno TV behaves as a product, implement it in
Tavuno.
