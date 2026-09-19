Yes. We’ll treat **Tavuno TV as one serious software project**, and build it from the foundation upward. The milestones below are the complete path from an empty workspace to a finished production-ready platform.

# Tavuno TV — Complete Milestone Plan

## M0 — Project Foundation

**Goal:** Prepare the development environment and repositories.

### Tasks

* [ ] Create Tavuno TV GitHub organization/repositories
* [ ] Install Git
* [ ] Install Docker Desktop
* [ ] Install JDK 17+
* [ ] Install Android Studio
* [ ] Install Node.js LTS
* [ ] Install Python
* [ ] Install VS Code/IDE
* [ ] Configure SSH/GitHub
* [ ] Create project directory
* [ ] Create `.gitignore`
* [ ] Create `.env.example`
* [ ] Create documentation structure
* [ ] Freeze selected software versions

### Structure

```text
TavunoTV/
├── tavuno-tv-android/
├── tavuno-control/
├── tavuno-admin/
├── tavuno-infra/
├── tavuno-docs/
└── tavuno-deploy/
```

### Done when

All repositories clone/build correctly and the development machine is ready.

---

# M1 — Infrastructure Foundation

**Goal:** Run the entire backend infrastructure locally.

### Install with Docker

```text
PostgreSQL
Redis
Directus
Dispatcharr
OvenMediaEngine
Prometheus
Grafana
Caddy
```

### Tasks

* [ ] Create `docker-compose.yml`
* [ ] Configure persistent volumes
* [ ] Configure networks
* [ ] Configure `.env`
* [ ] Configure PostgreSQL
* [ ] Configure Redis
* [ ] Configure Directus
* [ ] Configure Dispatcharr
* [ ] Configure OME
* [ ] Configure Prometheus
* [ ] Configure Grafana
* [ ] Configure Caddy

### Test

```text
docker compose up -d
```

Every service must become healthy.

### Done when

```text
Docker
 ├── PostgreSQL     ✓
 ├── Redis          ✓
 ├── Directus       ✓
 ├── Dispatcharr    ✓
 ├── OME            ✓
 ├── Prometheus     ✓
 ├── Grafana        ✓
 └── Caddy          ✓
```

---

# M2 — Tavuno Database

**Goal:** Define Tavuno's actual data model.

Create:

```text
users
profiles
devices

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
audit_logs
```

### Tasks

* [ ] Design schema
* [ ] Create collections/tables in Directus
* [ ] Configure relationships
* [ ] Configure permissions
* [ ] Add test data
* [ ] Create database seed

### Done when

You can create:

```text
User
 ↓
Device
 ↓
Subscription
 ↓
Plan
 ↓
Channel entitlement
```

through Directus.

---

# M3 — Tavuno Control Backend

**Goal:** Create our own small backend.

```text
tavuno-control/
├── auth/
├── users/
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

### Tasks

* [x] Choose backend framework
* [x] Create project
* [x] Environment configuration
* [x] Logging
* [x] Error handling
* [x] PostgreSQL/Directus integration
* [x] Redis integration
* [x] API structure
* [x] OpenAPI documentation
* [x] Automated tests

### First APIs

```text
GET /health

GET /v1/home
GET /v1/channels
GET /v1/channels/{id}

POST /v1/devices/register

GET /v1/epg
GET /v1/movies
GET /v1/series
GET /v1/sports
```

### Done when

Tavuno has a working API independent of the Android application.

---

# M4 — Dispatcharr Integration

**Goal:** Connect Tavuno to IPTV middleware.

```text
Tavuno Control
      │
      ▼
Dispatcharr
```

### Tasks

* [x] Connect API
* [x] Authenticate server-side
* [x] Import channels
* [x] Import categories
* [x] Import stream information
* [x] Import EPG
* [x] Import VOD
* [x] Store external IDs
* [x] Synchronization job
* [x] Error handling
* [x] Health monitoring

### Important

Tavuno database keeps its own IDs:

```text
Tavuno channel ID
        +
Dispatcharr channel ID
```

Never depend entirely on Dispatcharr IDs.

### Done when

A Dispatcharr channel appears in the Tavuno API.

---

# M5 — EPG System

**Goal:** Complete the electronic program guide.

### Pipeline

```text
EPG Source
    ↓
XMLTV
    ↓
Dispatcharr
    ↓
Tavuno normalization
    ↓
PostgreSQL
    ↓
Tavuno API
```

### Tasks

* [x] EPG ingestion
* [x] Channel matching
* [x] Programme normalization
* [x] Current programme
* [x] Next programme
* [x] Schedule
* [x] EPG caching
* [x] Automatic synchronization

### Done when

A channel returns:

```text
NOW
NEXT
LATER
```

correctly.

---

# M6 — Streaming Foundation

**Goal:** Get one authorized stream from source → server → player.

```text
Authorized Source
       ↓
Dispatcharr
       ↓
OvenMediaEngine
       ↓
HLS / LL-HLS
       ↓
Media3
```

### Tasks

* [x] Configure OME
* [x] Configure ingest
* [x] Configure output
* [x] Test HLS
* [x] Test LL-HLS
* [x] Test stream authentication
* [x] Test stream failure
* [x] Test recovery
* [x] Measure latency
* [x] Measure CPU/RAM/network

### Done when

One authorized stream plays reliably.

**This is our first major vertical slice.**

---

# M7 — Playback Authorization

**Goal:** Never expose permanent source credentials/URLs to clients.

Flow:

```text
Android
   ↓
POST /playback
   ↓
Tavuno Control
   ↓
User check
   ↓
Device check
   ↓
Subscription check
   ↓
Entitlement check
   ↓
Session creation
   ↓
Temporary playback authorization
   ↓
OME
```

### Tasks

* [x] Authentication
* [x] Device validation
* [x] Subscription validation
* [x] Entitlement validation
* [x] Session creation
* [x] Temporary authorization
* [x] Expiration
* [x] Heartbeat
* [x] Session termination

### Done when

Authorized users can play; unauthorized users cannot.

---

# M8 — Android TV Foundation

**Goal:** Convert the selected client foundation into Tavuno TV.

Starting point:

**StreamVault**

### First tasks

* [ ] Fork/copy approved source
* [ ] Build untouched application
* [ ] Replace package name
* [ ] Replace application name
* [ ] Tavuno branding
* [ ] Tavuno icon
* [ ] Configure API URL
* [ ] Configure environments
* [ ] Remove unnecessary providers
* [ ] Connect Tavuno authentication

Do **not** redesign the whole UI yet.

### Done when

```text
Tavuno TV APK
      ↓
Installs on Android TV
      ↓
Connects to Tavuno API
```

---

# M9 — Authentication + Devices

**Goal:** Build the complete account/device system.

### Features

```text
Login
Logout
Refresh session
Device registration
Device list
Device activation
Device removal
Device limits
```

### Example

```text
Account
├── Living Room TV
├── Bedroom TV
└── Phone
```

### Done when

The backend knows exactly which device is using the account.

---

# M10 — Tavuno Live TV

**Goal:** Complete the main TV experience.

```text
Home
 ↓
Live TV
 ↓
Categories
 ↓
Channels
 ↓
EPG
 ↓
Player
```

### Features

* [ ] Categories
* [ ] Channel list
* [ ] Channel logos
* [ ] Favorites
* [ ] EPG
* [ ] Now/Next
* [ ] Search
* [ ] Player
* [ ] Channel switching
* [ ] Recently watched

### Done when

A user can comfortably watch live TV from beginning to end.

---

# M11 — Sports

**Goal:** Create Tavuno's sports experience.

```text
Sports
├── Football
├── Basketball
├── Tennis
└── Other
```

### Data

```text
Competition
Season
Team
Match
Kickoff
Status
Broadcast mapping
```

### Features

* [ ] Sports homepage
* [ ] Competitions
* [ ] Match list
* [ ] Upcoming matches
* [ ] Live matches
* [ ] Match details
* [ ] Watch button
* [ ] EPG connection

### Done when

A sports event can lead to its authorized live broadcast through the normal playback system.

---

# M12 — VOD

**Goal:** Movies and series.

### Movies

```text
Movies
├── Popular
├── Latest
├── Genres
└── Search
```

### Series

```text
Series
 └── Season
      └── Episode
```

### Features

* [ ] Posters
* [ ] Backdrops
* [ ] Metadata
* [ ] Genres
* [ ] Search
* [ ] Watch
* [ ] Continue watching
* [ ] Watch history
* [ ] Resume position

### Done when

Movie and series playback works through the same authorization architecture.

---

# M13 — Catch-up / DVR / Timeshift

**Goal:** Add time-shift functionality.

### Features

* [ ] Catch-up
* [ ] Programme playback
* [ ] DVR
* [ ] Recording
* [ ] Timeshift
* [ ] Recorded content
* [ ] Storage management

Use Dispatcharr/OME capabilities wherever possible instead of implementing media infrastructure ourselves.

### Done when

A user can select an eligible previous programme and watch it.

---

# M14 — Subscription System

**Goal:** Turn accounts into a real product.

### Plans

```text
Free
Basic
Premium
Sports
```

Example properties:

```text
duration
max_devices
max_concurrent_streams
channel_packages
vod_packages
sports_access
```

### Tasks

* [ ] Plans
* [ ] Subscriptions
* [ ] Activation
* [ ] Expiration
* [ ] Renewal state
* [ ] Entitlements
* [ ] Concurrent limits
* [ ] Grace periods

### Done when

Changing a user's plan immediately changes what they can access.

---

# M15 — Admin Platform

**Goal:** Give operators complete control.

Start with **Directus Studio**.

### Admin capabilities

```text
Dashboard
Users
Devices
Plans
Subscriptions
Channels
Categories
EPG
Movies
Series
Sports
Streams
Sessions
```

### Add custom UI only when Directus is insufficient.

### Done when

An administrator can operate Tavuno without touching the database manually.

---

# M16 — Monitoring & Operations

**Goal:** Know what is happening in real time.

### Prometheus

Collect:

```text
CPU
RAM
network
API latency
errors
active users
active sessions
stream count
OME metrics
Dispatcharr metrics
database health
```

### Grafana

Create:

```text
Overview
Live Streams
Users
Playback
Infrastructure
Errors
```

### Alerts

```text
OME down
Dispatcharr down
Database down
high CPU
high RAM
stream failure
API failure
disk almost full
```

### Done when

A serious failure generates an observable alert.

---

# M17 — Security Hardening

**Goal:** Secure the entire platform.

### Backend

* [ ] HTTPS
* [ ] Rate limiting
* [ ] Input validation
* [ ] Secure authentication
* [ ] RBAC
* [ ] Audit logs
* [ ] Secret management
* [ ] Token expiration
* [ ] Session controls

### Media

* [ ] Temporary playback authorization
* [ ] Source credentials hidden
* [ ] Access control
* [ ] Anti-abuse controls
* [ ] Connection limits

### Infrastructure

* [ ] Firewall
* [ ] Container isolation
* [ ] Secure ports
* [ ] Backups
* [ ] Secret rotation
* [ ] Dependency updates

---

# M18 — Production Architecture

**Goal:** Move beyond localhost.

```text
                 Internet
                    │
                 Caddy
             ┌──────┴──────┐
             │             │
            API           Media
             │             │
      Tavuno Control      OME
             │             │
      ┌──────┼──────┐      │
      ▼      ▼      ▼      ▼
 Directus Redis PostgreSQL Origin
```

### Tasks

* [ ] Production server
* [ ] DNS
* [ ] HTTPS
* [ ] Firewall
* [ ] Docker deployment
* [ ] Database backups
* [ ] Monitoring
* [ ] Logging
* [ ] CI/CD
* [ ] Staging environment

---

# M19 — Scaling

Only after M18 is stable.

### Architecture

```text
                 Load Balancer
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
      API Server 1            API Server 2
          │                       │
          └───────────┬───────────┘
                      │
                   Redis
                      │
                 PostgreSQL
                      
                  MEDIA
                    │
            ┌───────┴────────┐
            ▼                ▼
        OME Origin        OME Edge
            │                │
            └───────┬────────┘
                    ▼
                  Users
```

### Test progressively

```text
1
10
50
100
500
1000+
```

Only record numbers that we actually measure.

---

# M20 — Quality / QA

**Goal:** Make Tavuno TV reliable.

Test:

### Backend

* [ ] Unit tests
* [ ] Integration tests
* [ ] API tests
* [ ] Authentication tests
* [ ] Authorization tests
* [ ] Load tests

### Android TV

* [ ] Login
* [ ] Navigation
* [ ] EPG
* [ ] Playback
* [ ] Channel switching
* [ ] VOD
* [ ] Sports
* [ ] Network loss
* [ ] Server failure
* [ ] Remote control

### Infrastructure

* [ ] Restart services
* [ ] Database recovery
* [ ] Stream failure
* [ ] Source failover
* [ ] Backup restore

---

# M21 — Production Release

### Release checklist

```text
Code
 ✓

Tests
 ✓

Security
 ✓

Monitoring
 ✓

Backups
 ✓

Recovery
 ✓

Android TV APK
 ✓

Backend
 ✓

Admin
 ✓

Documentation
 ✓
```

Then:

```text
Tavuno TV v1.0
```

---

# M22 — Advanced OTT Features

Only after v1.0.

```text
Profiles
Parental controls
Recommendations
Notifications
Multiple audio tracks
Subtitles
Picture-in-picture
Multi-language UI
DRM
Adaptive bitrate improvements
Low-latency sports mode
Advanced analytics
CDN
Multi-region
```

---

# Final dependency order

This is the order you should actually follow:

```text
M0
 │
 ▼
M1 Infrastructure
 │
 ▼
M2 Database
 │
 ▼
M3 Tavuno Control
 │
 ▼
M4 Dispatcharr
 │
 ▼
M5 EPG
 │
 ▼
M6 Streaming
 │
 ▼
M7 Playback Authorization
 │
 ▼
M8 Android TV
 │
 ▼
M9 Accounts + Devices
 │
 ▼
M10 Live TV
 │
 ├──────────────┐
 ▼              ▼
M11 Sports     M12 VOD
 │              │
 └──────┬───────┘
        ▼
M13 Catch-up/DVR
        │
        ▼
M14 Subscriptions
        │
        ▼
M15 Admin
        │
        ▼
M16 Monitoring
        │
        ▼
M17 Security
        │
        ▼
M18 Production
        │
        ▼
M19 Scaling
        │
        ▼
M20 QA
        │
        ▼
M21 V1.0
        │
        ▼
M22 Advanced OTT
```

## Your immediate task: **M0**

Do **not** start modifying StreamVault yet.

First we establish the Tavuno TV workspace, versions, Git repositories, Docker, Java/Android environment, and baseline documentation.

Once M0 is finished, we move **one milestone at a time**.
