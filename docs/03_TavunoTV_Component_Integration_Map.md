# Tavuno TV --- Component Integration Map

## 1. Selected components

  -----------------------------------------------------------------------
  Layer                   Selected component      Tavuno role
  ----------------------- ----------------------- -----------------------
  Android TV              StreamVault             Starting client
                                                  foundation

  Playback                AndroidX Media3         Client playback

  IPTV middleware         Dispatcharr             Sources, EPG, VOD,
                                                  routing, failover

  Media server            OvenMediaEngine         Ingest, ABR, LL-HLS,
                                                  WebRTC, transcoding,
                                                  DVR/DRM

  Backend foundation      Directus                Data API, admin Studio,
                                                  auth/access control,
                                                  workflows

  Database                PostgreSQL              Canonical Tavuno data

  Cache/session support   Redis                   Fast state/cache

  Admin UI                Directus Studio first   Avoid building CRUD UI

  Custom backend          tavuno-control          Product-specific
                                                  orchestration

  Metrics                 Prometheus              Metrics

  Dashboards              Grafana                 Operations

  Deployment              Docker Compose first    Local/staging

  Reverse proxy           Caddy                   TLS/routing
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 2. StreamVault

Repository:

`https://github.com/Davidona/StreamVault-IPTV`

Use it for:

``` text
Android TV shell
TV navigation
D-pad interaction
provider abstractions
Media3 integration
TV playback UX
EPG UI patterns
VOD UI patterns
```

Replace or adapt:

``` text
provider login
provider sync
provider-specific catalog
provider playback resolution
branding
business account logic
subscription logic
device logic
```

Target:

``` text
StreamVault architecture
        ↓
Tavuno TV client
        ↓
Tavuno API
```

------------------------------------------------------------------------

# 3. Dispatcharr

Repository:

`https://github.com/Dispatcharr/Dispatcharr`

Use it for:

``` text
M3U
Xtream
EPG
VOD
stream sources
stream filtering
stream profiles
failover
stream monitoring
catch-up
DVR
```

Do not duplicate these features in tavuno-control.

------------------------------------------------------------------------

# 4. OvenMediaEngine

Repository:

`https://github.com/OvenMediaLabs/OvenMediaEngine`

Use it for:

``` text
live ingest
protocol conversion
ABR
LL-HLS
WebRTC
HLS
transcoding
DVR
recording
DRM
origin-edge
monitoring
```

Do not implement a custom media server.

------------------------------------------------------------------------

# 5. Directus

Repository:

`https://github.com/directus/directus`

Use it for:

``` text
data administration
users
catalog
content metadata
plans
subscriptions
entitlements
images
permissions
workflow automation
REST
GraphQL
admin
```

Do not expose administrative privileges to the TV client.

------------------------------------------------------------------------

# 6. tavuno-control

This is the main custom component.

It should be small.

``` text
tavuno-control
│
├── auth
├── devices
├── entitlements
├── playback
├── sessions
├── dispatcharr-client
├── ome-client
├── catalog-aggregation
├── health
└── tests
```

The service is the bridge:

``` text
Directus
   ↕
tavuno-control
   ↕
Dispatcharr
   ↕
OvenMediaEngine
```

------------------------------------------------------------------------

# 7. Data ownership

## Directus/PostgreSQL owns

``` text
Tavuno users
Tavuno devices
Tavuno plans
Tavuno subscriptions
Tavuno entitlements
Tavuno catalog
Tavuno metadata
Tavuno playback sessions
```

## Dispatcharr owns

``` text
provider/source configuration
IPTV source normalization
middleware stream state
EPG source processing
stream routing
source failover
```

## OME owns

``` text
media sessions
ingest
transcoding
ABR
media delivery
media-level statistics
```

## Android owns

``` text
local UI state
cache
playback UI
local preferences
local watch state
```

------------------------------------------------------------------------

# 8. ID mapping

Never use external IDs as Tavuno primary IDs.

Example:

``` text
Tavuno channel
id = 7d3f...

Dispatcharr channel
id = 1829

OME stream
name = channel_1829
```

Database:

``` text
channels
----------------------------
id
name
dispatcharr_id
ome_stream_name
```

This lets us replace infrastructure later without rewriting the entire
platform.

------------------------------------------------------------------------

# 9. API boundaries

Android:

``` text
Android
   │
   ▼
Tavuno API
```

Tavuno API:

``` text
GET  /v1/home
GET  /v1/channels
GET  /v1/channels/{id}
GET  /v1/epg
GET  /v1/movies
GET  /v1/series
GET  /v1/sports
POST /v1/devices/register
POST /v1/playback/live/{id}
POST /v1/playback/vod/{id}
POST /v1/playback/heartbeat
POST /v1/playback/stop
```

The Android client should not directly call Dispatcharr or OME
administrative APIs.

------------------------------------------------------------------------

# 10. Playback token architecture

Conceptual:

``` text
User token
    ↓
Tavuno control
    ↓
entitlement validation
    ↓
session creation
    ↓
short-lived playback authorization
    ↓
OME
```

Tokens should be:

``` text
short-lived
scoped
revocable through session state
device-aware
content-aware
```

Do not create permanent public playback URLs for protected Tavuno
content.

------------------------------------------------------------------------

# 11. Infrastructure topology

Development:

``` text
localhost
│
├── Directus
├── PostgreSQL
├── Redis
├── Dispatcharr
├── OME
├── tavuno-control
├── Prometheus
└── Grafana
```

Staging:

``` text
tavuno-staging.example
│
├── control
├── directus
├── postgres
├── redis
├── dispatcharr
└── ome
```

Production:

``` text
                    Internet
                       │
                    Caddy
                       │
             ┌─────────┴─────────┐
             │                   │
        API/Admin             Media
             │                   │
        tavuno-control          OME
             │                   │
      ┌──────┴──────┐      ┌─────┴─────┐
      ▼             ▼      ▼           ▼
  Directus        Redis  Origin       Edge
      │
      ▼
 PostgreSQL
```

------------------------------------------------------------------------

# 12. Minimal custom-code principle

Before writing code, ask:

``` text
Does Directus already do this?
        ↓ yes → configure it

Does Dispatcharr already do this?
        ↓ yes → integrate it

Does OME already do this?
        ↓ yes → configure/integrate it

Does Media3 already do this?
        ↓ yes → use it

Only if all answers are NO:
        ↓
write Tavuno code
```

This is the central principle of the entire project.

------------------------------------------------------------------------

# 13. First working vertical slice

The first real Tavuno TV feature should be exactly:

``` text
1 authorized test stream
        ↓
Dispatcharr
        ↓
OME
        ↓
tavuno-control
        ↓
Tavuno Android TV
        ↓
Media3
        ↓
PLAY
```

Then add:

``` text
EPG
↓
user
↓
device
↓
subscription
↓
VOD
↓
sports
↓
DVR
↓
catch-up
```

Do not attempt all features simultaneously.

------------------------------------------------------------------------

# 14. Definition of done for the architecture

The architecture is accepted when:

-   one authorized live stream plays end-to-end
-   user authentication works
-   device registration works
-   entitlement checks work
-   playback sessions are tracked
-   EPG reaches the TV client
-   stream failure is visible in monitoring
-   the admin can manage the content
-   the TV client never receives infrastructure secrets
-   all components can be reproduced with documented Docker
    configuration
-   replacing one infrastructure component does not require rewriting
    the entire client

------------------------------------------------------------------------

# 15. Official repositories used as the technical baseline

StreamVault:

`https://github.com/Davidona/StreamVault-IPTV`

Dispatcharr:

`https://github.com/Dispatcharr/Dispatcharr`

OvenMediaEngine:

`https://github.com/OvenMediaLabs/OvenMediaEngine`

Directus:

`https://github.com/directus/directus`

MediaMTX is retained as a secondary/simple media-router option:

`https://github.com/bluenviron/mediamtx`

Supabase is not part of the frozen stack because Directus already covers
the database/API/admin foundation we need:

`https://github.com/supabase/supabase`

------------------------------------------------------------------------

# 16. Final decision

The Tavuno TV foundation is:

``` text
                  TAVUNO TV
                     │
        ┌────────────┼─────────────┐
        │            │             │
        ▼            ▼             ▼
    StreamVault   Directus     Dispatcharr
        │            │             │
      Media3      PostgreSQL       IPTV
        │            │             │
        └────────────┼─────────────┘
                     │
                     ▼
              tavuno-control
                     │
                     ▼
             OvenMediaEngine
                     │
                     ▼
              Authorized media
```

The project goal is **maximum reuse, minimum custom infrastructure, and
a small Tavuno-specific control layer**.
