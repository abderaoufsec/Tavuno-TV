# Tavuno TV --- Master Platform Strategy

**Project name:** Tavuno TV\
**Strategy version:** 1.0\
**Date:** 18 September 2026\
**Objective:** Build a production-grade OTT/IPTV platform with as little
original infrastructure code as practical by composing mature
open-source projects.

> **Important scope:** Tavuno TV is intended for channels, VOD, sports
> programming, and other media that Tavuno TV is authorized to
> distribute. The architecture supports the technology; it does not
> grant broadcast or distribution rights.

------------------------------------------------------------------------

## 1. Final strategic decision

We are **not** going to turn one IPTV application into the entire
platform.

Tavuno TV will be assembled from four layers:

``` text
┌──────────────────────────────────────────────────────────────┐
│                         TAVUNO TV                             │
├──────────────────────────────────────────────────────────────┤
│  CLIENT                                                      │
│  StreamVault-based Android TV application                    │
│  Kotlin + Compose for TV + Media3                            │
├──────────────────────────────────────────────────────────────┤
│  IPTV / CONTENT MIDDLEWARE                                   │
│  Dispatcharr                                                  │
│  M3U / Xtream / XMLTV / VOD / EPG / stream management        │
├──────────────────────────────────────────────────────────────┤
│  MEDIA / STREAMING                                            │
│  OvenMediaEngine                                              │
│  ingest + transcoding + ABR + LL-HLS + WebRTC + DVR + DRM    │
├──────────────────────────────────────────────────────────────┤
│  PLATFORM CONTROL PLANE                                      │
│  Directus + PostgreSQL                                       │
│  users + devices + plans + entitlements + catalog + admin    │
│  + small custom Tavuno TV service for playback authorization │
└──────────────────────────────────────────────────────────────┘
```

### Why this architecture?

Because each major component already solves a hard problem:

-   **StreamVault** already solves a large part of the Android TV
    UX/player problem.
-   **Dispatcharr** already solves a large part of IPTV source, EPG,
    VOD, playlist, proxy, monitoring and failover management.
-   **OvenMediaEngine** already solves advanced live-media delivery,
    ABR, low latency, transcoding, DVR and DRM.
-   **Directus** already gives us a database-driven API,
    authentication/access-control capabilities and a visual admin
    Studio.
-   **PostgreSQL** is the system-of-record database.
-   A **small Tavuno TV control service** handles only product-specific
    orchestration that the existing projects should not own.

This minimizes custom code while keeping Tavuno TV's product logic under
our control.

------------------------------------------------------------------------

# 2. Chosen tools --- frozen for the first implementation

## Phase A --- Client

### Primary base: StreamVault

Repository:

`https://github.com/Davidona/StreamVault-IPTV`

StreamVault is explicitly TV-first and already supports Android TV,
Kotlin, Jetpack Compose, Room, Hilt, Media3, M3U, Xtream Codes, Stalker
Portal and Jellyfin, with Live TV, Movies, Series and guide flows.

Source:

`https://github.com/Davidona/StreamVault-IPTV`

### Tavuno TV client direction

We will not immediately rewrite the player.

We will preserve the mature playback foundation and progressively
replace provider/business logic with Tavuno TV APIs.

Target:

``` text
Tavuno TV Android TV
├── Home
├── Live TV
├── Sports
├── EPG
├── Movies
├── Series
├── Search
├── Favorites
├── Continue Watching
├── Profiles
├── Downloads / DVR
├── Notifications
└── Settings
```

Media playback remains Media3 unless a concrete requirement proves
otherwise.

------------------------------------------------------------------------

# 3. Phase B --- IPTV middleware

## Primary tool: Dispatcharr

Repository:

`https://github.com/Dispatcharr/Dispatcharr`

Dispatcharr currently provides:

-   M3U and Xtream Codes import
-   playlist filtering/organization
-   EPG matching and generation
-   XMLTV support
-   DVR
-   catch-up/timeshift
-   VOD
-   TMDB/IMDb metadata
-   M3U/XMLTV/Xtream/HDHomeRun output
-   monitoring
-   bandwidth tracking
-   automatic failover
-   stream profiles
-   output profiles
-   multi-user access control
-   plugins

Source:

`https://github.com/Dispatcharr/Dispatcharr`

### Strategic use

Dispatcharr is a **middleware/operations engine**, not our
customer-facing product.

Tavuno TV will integrate with it.

``` text
Authorized sources
        ↓
   Dispatcharr
        ↓
normalized channels / EPG / VOD / stream state
        ↓
Tavuno TV control plane
        ↓
Tavuno TV clients
```

Do not duplicate Dispatcharr functionality in Tavuno TV unless a real
product requirement demands it.

------------------------------------------------------------------------

# 4. Phase C --- Streaming infrastructure

## Primary tool: OvenMediaEngine

Repository:

`https://github.com/OvenMediaLabs/OvenMediaEngine`

OME currently supports:

-   WebRTC / WHIP
-   SRT
-   RTMP
-   RTSP
-   MPEG-TS
-   LL-HLS
-   HLS
-   adaptive bitrate
-   live transcoding
-   DVR/live rewind
-   VOD dump
-   subtitles
-   DRM
-   clustering/origin-edge architecture
-   monitoring
-   access control
-   REST API

Source:

`https://github.com/OvenMediaLabs/OvenMediaEngine`

This makes OME the primary media engine for Tavuno TV's advanced
streaming path.

### Important implementation principle

Do **not** force every source through transcoding.

Use pass-through whenever the source/device compatibility allows it.

Transcode only when required for:

-   resolution adaptation
-   codec compatibility
-   bitrate adaptation
-   audio compatibility
-   subtitles
-   DRM
-   device compatibility

This reduces CPU/GPU cost dramatically.

------------------------------------------------------------------------

# 5. Phase D --- Tavuno TV platform

## Primary backend/control platform: Directus + PostgreSQL

Repository:

`https://github.com/directus/directus`

Directus provides:

-   SQL database integration
-   REST API
-   GraphQL
-   visual Studio
-   authentication
-   access control
-   file management
-   data administration
-   automation/flows
-   extensions

Source:

`https://github.com/directus/directus`

### Why Directus?

Because building:

``` text
users
devices
channels
categories
VOD
plans
subscriptions
EPG metadata
images
permissions
admin CRUD
```

from zero would waste enormous development time.

Directus becomes the **Tavuno TV control-plane foundation**.

PostgreSQL remains the canonical database.

------------------------------------------------------------------------

# 6. The small amount of custom code we WILL write

We do not want a giant backend.

We need one thin Tavuno TV service responsible for product-specific
orchestration.

Call it:

``` text
tavuno-control
```

Its responsibilities:

``` text
1. Playback authorization
2. Device registration
3. Concurrent-session enforcement
4. Subscription/entitlement checks
5. Stream-session creation
6. Mapping Tavuno content IDs to middleware/media IDs
7. Client configuration
8. Security-sensitive server-side operations
9. Aggregating data from Directus + Dispatcharr + OME
10. Tavuno-specific business rules
```

It should NOT become another giant CMS.

------------------------------------------------------------------------

# 7. High-level request flow

A user opens a channel.

``` text
Android TV
    │
    │ POST /playback/channel/{id}
    ▼
tavuno-control
    │
    ├── authenticate user
    ├── validate device
    ├── validate subscription
    ├── validate entitlement
    ├── check concurrent streams
    │
    ▼
Dispatcharr
    │
    ├── resolve channel/source
    ├── select healthy source
    └── provide stream mapping
    │
    ▼
OvenMediaEngine
    │
    ├── ingest/proxy/transcode
    └── deliver authorized playback
    │
    ▼
Android TV / Media3
```

------------------------------------------------------------------------

# 8. Tavuno TV data model

The canonical business objects are:

``` text
User
 ├── Devices
 ├── Profiles
 └── Subscriptions
        │
        ▼
      Plans
        │
        ▼
   Entitlements
        │
        ├── Channels
        ├── Sports events
        ├── Movies
        └── Series
```

Content:

``` text
Channel
 ├── category
 ├── logo
 ├── language
 ├── country
 ├── EPG mapping
 └── middleware stream mapping

Movie
 ├── metadata
 ├── artwork
 ├── stream mapping
 └── entitlement

Series
 ├── seasons
 ├── episodes
 ├── metadata
 └── stream mapping
```

Playback:

``` text
PlaybackSession
 ├── user
 ├── device
 ├── content
 ├── source
 ├── start_time
 ├── last_heartbeat
 ├── expires_at
 └── status
```

------------------------------------------------------------------------

# 9. What we will NOT build

Do not write custom replacements for:

-   video codecs
-   HLS parser
-   WebRTC stack
-   RTMP server
-   RTSP server
-   transcoder
-   IPTV playlist parser unless a gap is proven
-   generic admin CRUD
-   database engine
-   authentication primitives
-   EPG format parser unless required
-   Android video renderer

Use the selected open-source infrastructure.

------------------------------------------------------------------------

# 10. Product boundaries

### Tavuno TV owns

``` text
Brand
UX
Customer experience
Accounts
Devices
Subscriptions
Entitlements
Catalog presentation
Playback authorization
Business rules
Admin workflows
Analytics
Product API
```

### Open-source infrastructure owns

``` text
Playback
Media protocols
Transcoding
IPTV source normalization
EPG ingestion
Stream routing
Database engine
Generic authentication primitives
Admin CRUD infrastructure
```

------------------------------------------------------------------------

# 11. Repository structure

Create one Tavuno TV organization/repository structure:

``` text
TavunoTV/
│
├── tavuno-tv-android/
│
├── tavuno-control/
│
├── tavuno-admin/
│
├── tavuno-infra/
│
├── tavuno-docs/
│
└── tavuno-deploy/
```

External services:

``` text
Dispatcharr
OvenMediaEngine
Directus
PostgreSQL
Redis
Prometheus
Grafana
Reverse proxy
Object storage
```

Do not fork every external repository into the Tavuno organization.

Only fork when we genuinely need source-level modification.

------------------------------------------------------------------------

# 12. Development philosophy

Every milestone follows:

``` text
RESEARCH
   ↓
DESIGN
   ↓
IMPLEMENT ONE SMALL PART
   ↓
BUILD
   ↓
TEST
   ↓
DOCUMENT
   ↓
COMMIT
   ↓
NEXT PART
```

Never give an AI coding agent the instruction:

> "Build the whole Tavuno TV platform."

Instead, give it one bounded task with acceptance tests.

------------------------------------------------------------------------

# 13. Definition of success

The first complete Tavuno TV prototype should achieve:

``` text
Admin creates user
        ↓
Admin creates subscription
        ↓
Admin assigns channels
        ↓
User logs into Tavuno TV
        ↓
Device is registered
        ↓
User sees Home
        ↓
User opens Live TV
        ↓
Channel list loads
        ↓
EPG appears
        ↓
User selects channel
        ↓
Backend authorizes playback
        ↓
Media engine delivers stream
        ↓
Media3 plays it
        ↓
Session is monitored
```

Then add:

``` text
Sports
VOD
Series
Catch-up
DVR
Multi-device
Profiles
Search
Recommendations
Notifications
Analytics
```

------------------------------------------------------------------------

# 14. Final stack

``` text
CLIENT
StreamVault → Tavuno TV Android TV
Kotlin
Compose for TV
Media3
Room
Hilt

IPTV MIDDLEWARE
Dispatcharr

MEDIA
OvenMediaEngine

CONTROL PLANE
Directus
PostgreSQL

CUSTOM LOGIC
tavuno-control

ADMIN
Directus Studio initially
Custom Tavuno Admin UI later where needed

CACHE
Redis

OBSERVABILITY
Prometheus
Grafana

REVERSE PROXY
Caddy or Nginx

CONTAINERS
Docker Compose initially

OBJECT STORAGE
S3-compatible storage
```

------------------------------------------------------------------------

# 15. Do not start coding the full platform yet

The next concrete task is:

**MILESTONE 0 --- Tavuno TV Infrastructure Proof**

We will run:

``` text
Directus
PostgreSQL
Dispatcharr
OvenMediaEngine
Redis
Prometheus
Grafana
Reverse proxy
```

locally with Docker Compose.

Then use **one authorized test stream**.

The success test is:

``` text
Source
  ↓
Dispatcharr
  ↓
OME
  ↓
Tavuno control
  ↓
Tavuno Android TV
  ↓
Playback
```

Only after that works do we start implementing customer accounts and
subscriptions.

------------------------------------------------------------------------

## Source repositories

-   StreamVault: https://github.com/Davidona/StreamVault-IPTV
-   Dispatcharr: https://github.com/Dispatcharr/Dispatcharr
-   OvenMediaEngine: https://github.com/OvenMediaLabs/OvenMediaEngine
-   Directus: https://github.com/directus/directus
-   Media3: https://github.com/androidx/media
