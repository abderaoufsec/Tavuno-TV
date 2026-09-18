# Tavuno TV --- Implementation Roadmap

## Rule

Never implement the whole platform at once.

Each milestone must end with:

``` text
BUILD
TEST
DOCUMENT
COMMIT
```

------------------------------------------------------------------------

# M0 --- Repository and infrastructure proof

## Goal

Prove that the chosen stack can run together.

### Services

``` text
PostgreSQL
Directus
Redis
Dispatcharr
OvenMediaEngine
Prometheus
Grafana
Caddy
```

### Deliverables

``` text
tavuno-infra/
├── docker-compose.yml
├── .env.example
├── config/
│   ├── directus/
│   ├── dispatcharr/
│   ├── ome/
│   ├── prometheus/
│   └── grafana/
└── README.md
```

### Test

``` text
All containers start
↓
Directus reachable
↓
Postgres healthy
↓
Dispatcharr reachable
↓
OME reachable
↓
Prometheus receives metrics
↓
Grafana displays metrics
```

No Android development yet.

------------------------------------------------------------------------

# M1 --- Tavuno data foundation

## Goal

Create the Tavuno content model.

Implement:

``` text
users
profiles
devices

plans
subscriptions
entitlements

categories
channels
channel_sources

movies
series
seasons
episodes

epg_channels
epg_programmes

playback_sessions
```

Use Directus for administration.

### Test

Admin can:

``` text
create user
create category
create channel
upload logo
create plan
create subscription
assign entitlement
```

------------------------------------------------------------------------

# M2 --- Dispatcharr integration

## Goal

Connect Tavuno to IPTV middleware.

Implement a server-side integration:

``` text
Tavuno
  ↓
Dispatcharr
```

Import:

``` text
channels
channel groups
EPG
VOD
stream status
```

Store external IDs.

### Test

A channel created in Dispatcharr can appear in the Tavuno catalog.

------------------------------------------------------------------------

# M3 --- EPG

## Goal

Make the guide usable.

Pipeline:

``` text
XMLTV
 ↓
Dispatcharr
 ↓
Tavuno normalization
 ↓
PostgreSQL
 ↓
API
 ↓
TV client
```

### Test

For one channel:

``` text
Now
Next
Later
```

must be displayed correctly.

------------------------------------------------------------------------

# M4 --- Tavuno control service

## Goal

Create the small custom backend.

Recommended structure:

``` text
tavuno-control/
├── auth/
├── devices/
├── catalog/
├── entitlements/
├── playback/
├── sessions/
├── dispatcharr/
├── ome/
├── health/
└── tests/
```

Do not implement generic CRUD that Directus already provides.

------------------------------------------------------------------------

# M5 --- Playback authorization

## Goal

Make the first end-to-end playback flow.

``` text
TV
 ↓
Tavuno API
 ↓
authentication
 ↓
device check
 ↓
subscription check
 ↓
entitlement check
 ↓
Dispatcharr
 ↓
OME
 ↓
Media3
```

### Test

Unauthorized user:

``` text
403
```

Authorized user:

``` text
playback token
```

Expired session:

``` text
playback rejected
```

------------------------------------------------------------------------

# M6 --- Android TV foundation

## Goal

Fork/use the agreed StreamVault source and turn it into Tavuno TV.

First replace branding only.

``` text
StreamVault
 ↓
Tavuno TV
```

Then establish:

``` text
Login
Device registration
Home
Live TV
EPG
Player
```

Do NOT redesign everything simultaneously.

------------------------------------------------------------------------

# M7 --- Tavuno TV Live TV

Implement:

``` text
Home
 ↓
Live TV
 ↓
Category
 ↓
Channel
 ↓
EPG
 ↓
Player
```

Player:

``` text
Media3
 ↓
HLS / LL-HLS
 ↓
OME
```

------------------------------------------------------------------------

# M8 --- Sports

Implement sports metadata separately:

``` text
Sports
├── Football
├── Basketball
├── Tennis
└── Other
```

Match:

``` text
competition
teams
date
time
status
artwork
broadcast mapping
```

Then:

``` text
Match
 ↓
Watch
 ↓
Playback authorization
```

Only authorized broadcasts are used.

------------------------------------------------------------------------

# M9 --- VOD

Implement:

``` text
Movies
Series
Seasons
Episodes
Search
Genres
Continue Watching
Watch History
```

Playback continues through the same authorization system.

------------------------------------------------------------------------

# M10 --- Device and account management

Implement:

``` text
Device activation
Device list
Device removal
Concurrent stream limit
Session management
Profile management
```

------------------------------------------------------------------------

# M11 --- Admin operations

Directus already supplies the foundation.

Add Tavuno-specific screens only where necessary:

``` text
Dashboard
Users
Devices
Subscriptions
Channels
Streams
EPG
VOD
Sports
Sessions
Monitoring
```

Do not rebuild Directus Studio unnecessarily.

------------------------------------------------------------------------

# M12 --- Monitoring

Integrate:

``` text
Prometheus
Grafana
OME metrics
Dispatcharr status
Tavuno API metrics
```

Build dashboards for:

``` text
active viewers
stream failures
bandwidth
CPU
RAM
transcoding
API latency
database
```

------------------------------------------------------------------------

# M13 --- Reliability

Implement:

``` text
source failover
stream health checks
session cleanup
retry policy
circuit breakers
database backups
configuration backups
```

------------------------------------------------------------------------

# M14 --- Production media architecture

Only now evaluate:

``` text
origin
edge
CDN
multi-server
load balancing
ABR ladders
regional deployment
```

Do not build a multi-region system before the single-region system
works.

------------------------------------------------------------------------

# M15 --- Security hardening

Implement:

``` text
HTTPS
short-lived playback authorization
server-side credentials
rate limiting
audit logs
device restrictions
session limits
secret rotation
secure headers
container isolation
backup encryption
```

------------------------------------------------------------------------

# M16 --- Premium OTT features

After the core platform is stable:

``` text
Catch-up
DVR
Timeshift
Picture-in-picture
Profiles
Parental controls
Notifications
Recommendations
Search improvements
Subtitles
Multiple audio tracks
DRM
```

------------------------------------------------------------------------

# M17 --- Performance testing

Test:

``` text
1 viewer
10 viewers
50 viewers
100 viewers
500 viewers
```

depending on available infrastructure.

Measure:

``` text
startup time
buffering
API latency
stream latency
CPU
RAM
network
transcoding
failure recovery
```

Do not claim scalability numbers until tested.

------------------------------------------------------------------------

# M18 --- Release

Release pipeline:

``` text
Git
 ↓
CI
 ↓
tests
 ↓
Android build
 ↓
backend build
 ↓
Docker images
 ↓
staging
 ↓
acceptance tests
 ↓
production
```

------------------------------------------------------------------------

# Final product flow

``` text
                         USER
                          │
                          ▼
                  Tavuno TV Android
                          │
                    Authentication
                          │
                          ▼
                   Tavuno Control
                          │
             ┌────────────┼────────────┐
             ▼            ▼            ▼
         Directus      Dispatcharr     OME
             │            │            │
             ▼            ▼            ▼
         PostgreSQL   IPTV sources   Streaming
                          │
                          ▼
                     EPG / VOD
```

------------------------------------------------------------------------

# What the AI coding agent should do

AI agents should work in very small tasks.

Every prompt should contain:

``` text
CONTEXT
CURRENT FILES
TASK
CONSTRAINTS
EXPECTED OUTPUT
TESTS
STOP CONDITION
```

Example:

``` text
You are working on Tavuno TV.

Task:
Implement only device registration.

Do not modify playback.
Do not modify authentication.
Do not modify the database schema outside the device tables.

Requirements:
1. Register a device.
2. Return a stable device ID.
3. Reject duplicate active registration when appropriate.
4. Add unit tests.
5. Run the tests.

Stop after the tests pass.

Report:
- files changed
- tests executed
- test result
- remaining issues
```

This is the standard we should use throughout Tavuno TV development.
