# Tavuno TV — Milestone Status

## Completed Milestones

### M0 — Project Foundation ✅ COMPLETE
- Development environment established
- Docker infrastructure running
- GitHub repository configured

### M1 — Infrastructure Foundation ✅ COMPLETE
- All Docker services running and healthy
- PostgreSQL, Redis, Directus, Dispatcharr, OME, Prometheus, Grafana, Caddy operational

### M2 — Tavuno Database ✅ COMPLETE
- Complete schema implemented
- All tables created (users, profiles, devices, plans, subscriptions, categories, channels, EPG, movies, series, seasons, episodes, playback_sessions)

### M3 — Tavuno Control Backend ✅ COMPLETE
- FastAPI backend implemented
- All API endpoints functional
- PostgreSQL/Directus integration complete
- Redis integration complete
- Authentication system complete
- Device management complete

### M4 — Dispatcharr Integration ✅ COMPLETE
- API connection established
- Channel sync working
- Category sync working
- Stream mapping working
- EPG sync working
- VOD sync working

### M5 — EPG System ✅ COMPLETE
- EPG ingestion working
- NOW/NEXT/LATER resolution working
- Channel matching working
- Caching implemented

### M6 — Streaming Foundation ✅ COMPLETE
- OvenMediaEngine configured
- HLS/LL-HLS tested
- Stream authentication working
- Caddy reverse proxy configured

### M7 — Playback Authorization ✅ COMPLETE
- Live playback authorization implemented
- Movie playback authorization implemented
- Episode playback authorization implemented
- Session management working
- Heartbeat working
- Token minting working

### M8 — Android TV Foundation ✅ COMPLETE
- Complete Android TV application built from scratch
- Jetpack Compose for TV
- Media3/ExoPlayer integration
- TV-optimized UI
- D-pad navigation support

### M9 — Authentication + Devices ✅ COMPLETE
- Login/Logout implemented
- Session persistence working
- Token refresh working
- Device registration working
- Device limits enforced

**Post-M9 Remediation (2026-09-26):**
The following authentication/authorization defects identified before M9 were remediated in branch `fix/pre-m13-auth-hardening`:
- S1-03: Logout and password-reset device bricking (separated is_active from revoked_at)
- S2-01: Refresh token rotation and revocation (added JTI, Redis denylist, replay detection)
- S1-05: Catalog authentication requirements (added auth dependencies to all catalog routes)
- S1-06: Playback auto-registration removed (403 for unregistered devices)
- S1-04: Duplicate device registration endpoint removed
- S2-06: Spoofable X-Device-Fingerprint header removed (uses device_id from JWT)
- S2-03: Hardcoded 2-device default removed (requires active subscription)
- S2-02: Subscription validity consistency fixed (NULL check for ends_at)
- S2-04: Dead signature check removed (hmac.compare_digest for constant-time comparison)
- Android LoginRequest.deviceFingerprint serialization fixed (@SerializedName annotation)

### M10 — Tavuno Live TV ✅ COMPLETE
- Live TV screen implemented
- Category filtering working
- Channel listing working
- Navigation to player working
- EPG now/next display wired into channel cards (M10/M12 gap closure)
- Compose coroutine anti-pattern fixed (LaunchedEffect instead of CoroutineScope in composable body)

### M11 — Sports ✅ COMPLETE
- Sports screen implemented
- Live matches display
- Upcoming matches display
- Competition support

### M12 — VOD ✅ COMPLETE
- Movies catalog implemented
- Movie details implemented
- Series catalog implemented
- Series details implemented
- Seasons/episodes navigation implemented
- Movie playback authorization complete
- Episode playback authorization complete
- VOD sync logic verified against realistic fixtures (M10/M12 gap closure)
- Human-facing VOD content setup runbook added (docs/M12_VOD_Content_Setup.md)
- Note: Real VOD content still needs to be added in Dispatcharr by operator following the runbook

### Design-System Port — OwnTV-Baseline ✅ COMPLETE (2026-10-01, commit 6b57b57)
- Full theme system ported: Color.kt, AccentColor.kt, TavunoColors.kt, Dimens, Type.kt, Animations.kt
- Theme.kt gained a phone Material 3 compat bridge (phoneSchemeFrom + nested PhoneMaterialTheme) so legacy phone-composable screens keep working under the TV theme
- Components: FocusableSurface, TavunoButton, RoundedPanel, PosterCard, ChannelLogoTile, StateViews (replaces ErrorState.kt)
- Persistent shell: Sidebar, TopBar, CategoryRail, PreviewPane, ContentPane, DialogPanel compose into TavunoShell; TavunoMainScreen hosts all six browsing destinations in one shell (tab state, sidebar focus preserved across switches); TavunoNavigation only routes splash/login before it and details/player full-screen on top
- Rewritten onto the system: Home (scrollable rails), Settings, Movies, Series
- Sidebar initial focus lands on content (LEFT reaches the rail); Settings "Back to Home" uses SECONDARY button style
- Phone bridge verified: E2E D-pad login → shell → Home → Settings on emulator, zero FATALs
- New unit test: EpgTimeFormatTest; `gradlew test assembleDebug` BUILD SUCCESSFUL (0 errors)
- Remaining (tracked as follow-up stages): LiveTv, Sports, MovieDetails, SeriesDetails, SeasonEpisodes, Player, Splash, Login still on legacy layout/legacy card component; focus-card retirement gate

### Playback Guest-Mode Fix + Live Test Scope ✅ COMPLETE (2026-10-01)
**Symptom:** opening any stream showed *"No access token available"* and the player never started.

**Root causes (two, both Android-side):**
1. `PlaybackRepository.getAccessToken()` threw when no token existed, but splash had already been changed to skip login (free launch / `AUTH_OPEN_ACCESS`). Every playback authorization therefore failed before it reached the network.
2. `PlayerScreen` drove ExoPlayer from `CoroutineScope(Dispatchers.IO)`, so as soon as playback *was* authorized it died with `IllegalStateException: Player is accessed on the wrong thread` (`FATAL EXCEPTION: DefaultDispatcher-worker-1`).

**Fixes:**
- Authorization headers are now nullable end-to-end (`TavunoApiService` `@Header` params, `ApiAuthorization.bearerAuthorization()`); a guest sends no header and the backend resolves its seeded identity instead of the app throwing.
- `PlayerScreen` performs authorization in a `LaunchedEffect` (main dispatcher) with a composition `rememberCoroutineScope()` for heartbeats, so ExoPlayer is only ever touched on the thread that created it.
- Heartbeats no longer stack on repeated `ON_RESUME`.

**Verified on the Android TV emulator:** `POST /v1/playback/live/8845` → `200 OK` with no `Authorization` header; ExoPlayer decoded H.264 video + audio; playback session `129` recorded `active` for `guest@tavuno.local`; zero FATALs.

**Live channel test scope (reversible):** new `TAVUNO_LIVE_CHANNEL_ALLOWLIST` / `TAVUNO_LIVE_CHANNEL_LIMIT` settings filter `/v1/channels` and `/v1/home` at read time (synced data untouched). `tavuno-control/scripts/find_working_channels.py` finds channels whose upstream really plays (playback answers `protocol: "http_hls"`) and writes the scope into `tavuno-infra/.env`. Currently scoped to **10 verified-working channels**; clear with `--clear` to restore all 2036. See `docs/Live_Channel_Test_Scope.md`.

**Tests:** `TestLiveChannelTestScope` (8 cases) in `tests/test_catalog_service.py` (53 passed); `AuthorizationHeadersTest` on Android. Backend suite: 219 passed / 9 skipped with `AUTH_OPEN_ACCESS=false`.
### OwnTV Parity Port — Slice A: Player ✅ COMPLETE (2026-10-01, commit 807a699)
Source plan: `docs/OwnTV_Feature_Port_Plan.md`. OwnTV-Baseline's player is libmpv/FFmpeg, so
player-adjacent code was re-targeted to Media3/ExoPlayer rather than copied verbatim.

- **Zap engine.** `playback/LiveZapNavigator` + `core/LiveChannelQueue`: a browse screen arms the
  exact list the viewer was scrolling; CH± and D-pad ▲▼ step it with wrap-around at both ends, and
  every hop re-runs the same authorize effect the first tune uses (closing the session it replaces).
  A single-channel tune (Sports) clears the list, so zapping degrades honestly instead of walking a
  stale one. Live TV and Search both arm it, so a viewer can surf straight out of a search hit.
- **Remote map.** Every "which key does what, in which state" decision lives in
  `ui/screens/player/PlayerRemote.kt` (`resolvePlayerKey`), so it is unit-testable without a decoder:
  media keys are global; an open channel list owns the D-pad; the first OK reveals the controls and
  the second enters them; BACK is a ladder (list → controls → exit), never a stray exit from a stream.
- **Layers.** `PlayerHud` — an auto-hiding control strip that replaced Media3's touch-first
  `PlayerView` controller — and `PlayerChannelOverlay` (in-player channel list with now/next), both
  drawn in the Tavuno design system. Guide data is decoration: playback never waits on it.

### OwnTV Parity Port — Slice A2: Subtitles ✅ COMPLETE (2026-10-01)
- **Text tracks start disabled.** Media3's `DefaultTrackSelector` auto-selects a caption track nobody
  asked for; the player now disables `C.TRACK_TYPE_TEXT` at construction, so captions appear only when
  the viewer asks for them.
- **One key, one walk.** SUBTITLE/CAPTIONS (global, like the media rocker) or the HUD pill steps
  `Off → track 1 → … → Off`, pinning the exact track with a `TrackSelectionOverride` — deterministic
  even on a stream carrying several subtitle languages, and never dead-ending on the last track. The
  HUD names the active track so a viewer who cycled past the wanted one can see where they landed.
- **Decision layer.** `ui/screens/player/PlayerTracks.kt` holds the pure logic (`subtitleTrackInfos`,
  `subtitleTrackLabel`, `nextSubtitleIndex`, `selectedSubtitleIndex`); "on/off" is *derived* from the
  decoder's own `Tracks`, so the HUD can never claim a track is on that isn't.

**Tests (Slices A + A2):** `LiveZapNavigatorTest` (12), `PlayerRemoteTest` (17), `PlayerTracksTest`
(12), `AuthorizationHeadersTest` (3). Android suite: **65 passed / 0 failed**; `assembleDebug` clean.

### OwnTV Parity Port — Slice B: Search ✅ COMPLETE (commit 8320ae1)
- **Backend:** `GET /v1/search` — a case-insensitive `CatalogService.search()` across channels, movies
  and series, returning grouped results and applying the same live-channel test scope as the other
  catalog reads.
- **Android:** a Search tab in the shell — a debounced field (focused on entry, system IME for input,
  deliberately button-free) over three labelled result rows. Channel hits play immediately *and* arm
  the zap list; movie/series hits push their detail routes.

### OwnTV Parity Port — Slice C: Full EPG Guide ✅ COMPLETE (2026-10-02)
- **Backend:** `GET /v1/epg/window` (`app/epg/service.py`) answers every channel’s programmes that
  overlap one window in a single query — one request per grid screen instead of one per row —
  name-ordered, row-capped, and filtered by the same live-channel test scope as `/v1/channels`.
- **Android:** `ui/screens/guide/GuideScreen` draws that window as a D-pad grid with the focused
  cell’s programme detail beside it. Tuning from a row arms the zap list through the guide’s own
  channel mapping, so CH± and ▲▼ surf the guide rather than a stale catalog list.

### OwnTV Parity Port — Slice D: Customize + Profiles ✅ COMPLETE (2026-10-02)
- **Backend, customization** (`app/customize/`): `GET`/`PUT`/`DELETE /v1/customize/{kind}` for
  `live_channel`, `live_category` and `movie_category`. A PUT is a full replace for one kind, which
  is exactly what makes "un-pin" expressible; the ordering decision is a pure function
  (`app/customize/ordering.py`), so the API and the screen cannot disagree.
- **Backend, profiles** (`app/profiles/`): `GET`/`POST`/`PATCH`/`DELETE /v1/profiles` — the
  caller’s account row plus any number of child viewers. The account row is editable but never
  deletable (that would orphan the subscription), so DELETE only ever removes an extra viewer.
- **Android:** `CustomizeScreen` (reorder / pin / hide, three controls per row so UP/DOWN walk one
  column) and `ProfilesScreen` (rename via the system IME, add / switch / delete).
- **Schema:** migration `008_m14_customization_profiles.sql`.

### OwnTV Parity Port — Slice F: Viewer State & Artwork ✅ COMPLETE (2026-10-03)
- **Favourites** (`/v1/favourites`, migration `009`): a per-profile flag per
  `(kind, item_id)`, deliberately *not* folded into `tavuno_customizations` — that
  table owns rail order/visibility, and making "favourite" mean two things would
  make "still a favourite, just not first" inexpressible. Writes are idempotent
  upserts, so a double-tapped heart is a no-op rather than a 500.
- **Resume** (`/v1/resume`): a durable per-profile position, deliberately *not*
  written to `tavuno_playback_sessions` — that is an authorization lease the
  reaper deletes, so progress recorded there would vanish minutes later. Past 95%
  (finished) or back at 0 (rewound) the row is cleared, because a "continue
  watching" rail full of 99% bars is noise.
- **Home rails**: `/v1/home` now answers `channels`/`categories`/`movies`/`series`
  — the shape the Android client has always modeled — plus `continue_watching` and
  `favourites`. The change is **additive**: `featured_channels` is still returned
  as an alias, because removing it would break callers outside this repo while
  adding keys breaks nobody (Gson ignores absent keys).
- **Directus UUIDs → asset URLs** (`app/assets.py`): the artwork columns hold a
  `directus_files` relation, so the stored value is a bare UUID and an image loader
  silently renders a placeholder. Reads now normalize it to `<base>/assets/<uuid>`
  via `DIRECTUS_PUBLIC_URL`, and the sync writes the references it previously
  dropped. Live-stack verification caught a bug unit tests could not: a Postgres
  `uuid` column comes back from psycopg as `uuid.UUID`, not `str`, so the first
  version of the mapper passed every test and returned `None` for every real row.
- **Tests:** backend `test_assets.py`, `test_favourites_resume.py`,
  `test_favourites_resume_api.py`, `test_home_rails.py` (66 new cases);
  `tools/verify_a6.py` drives the live stack end-to-end. Backend suite: 480 passed
  / 13 skipped.

**Tests (Slices C + D):** Android `GuideGridTest` (14), `CustomizeItemsTest` (14),
`EpgTimeFormatTest` (8); backend `test_epg_service.py`, `test_customize_ordering.py`,
`test_customize_service.py`, `test_catalog_customization.py`, `test_customize_profiles_api.py`,
`test_profiles_service.py`.

### OwnTV Parity Port — Slice E: D-pad Behavioural Coverage ✅ COMPLETE (2026-10-03)
- **`DPadNavigationTest` / `DvrRewindTest` are real instrumented tests, not compile-only stubs.**
  Presses go through `UiAutomation.injectInputEvent` as genuine `KeyEvent`s — the same path a
  remote's key takes into the window — so `onPreviewKeyEvent` resolution, focus traversal, click
  activation and the HUD all see the real thing. Focus ownership is read geometrically (the
  smallest focused node plus the labels its bounds contain), because a `FocusableSurface` never
  merges its label into the focused node.
- **Coverage:** a cold start has no focus owner until the first press; the rail walk and the Home
  tiles; rail activation, channel-row focus and DOWN through the `LazyColumn`; BACK unwinds the
  tab to Home; CENTER tunes, the first OK reveals the strip, the second enters it and BACK is a
  ladder; and a −30s DVR seek measured on the real `ExoPlayer` behind a real DVR window
  (`tavuno-infra\start_dvr_test_stream.ps1` publishes a looping stream into OME).
- **Two product bugs fell out of that walk, both fixed:**
  1. An OK key-down that `FocusHud` consumed left an orphan key-up; focus had already moved onto
     the strip's first control by then, so that key-up *activated Back* and popped the player out
     of the stream. `PlayerScreen` now carries the paired key-up with the key-down
     (`consumedOkDown`).
  2. `onFocusChanged` sat after `focusable`, so it observed the strip's controls instead of the
     player's own `Box`: `rootFocused` read `false` while the root held focus, the second OK
     resolved to `Ignore` instead of `FocusHud`, and the unconsumed key pair leaked into the strip
     (same visible pop). Moving the modifier in front of `focusable` restores the real signal.

### Phase 1 — Reproducibility: Schema Migration Runner ✅ COMPLETE (2026-10-02)
- **The gap:** `tavuno-control/migrations/` held eight hand-written SQL files and there was no
  runner anywhere in the repository — a fresh clone had no supported path to a working schema, and
  nothing recorded which files a given database had already seen.
- **The runner:** `app/db_migrations.py` discovers the SQL files in filename order (the zero-padded
  prefix *is* the order), keeps a `tavuno_schema_migrations` ledger, and applies only what is
  pending. It runs from the API’s lifespan when `TAVUNO_AUTO_MIGRATE=true`, and a failure is fatal
  rather than leaving a half-migrated server answering traffic.
- **No baseline stamping needed:** every migration guards its own statements (`IF NOT EXISTS` /
  `ON CONFLICT`), so replaying the whole set against a hand-migrated database is a no-op. The runner
  can therefore start from an empty ledger anywhere, with no "mark everything as applied" step.
- **It patches — it does not bootstrap:** the tables these files alter (`tavuno_profiles`,
  `tavuno_devices`, `tavuno_plans`, `tavuno_categories`, `tavuno_channels`, `tavuno_channel_sources`)
  are created by the Directus base schema, `tavuno-infra/scripts/apply-m2-schema.ps1`, not by any
  migration. Run the set against a database where that has never executed and `001` fails with
  `relation "tavuno_profiles" does not exist`. `tests/test_migrations_live.py` pins that failure and
  the full apply either side of it, against a real PostgreSQL 17.11 server.
- **Second bug found and fixed:** replaying the set against the live database exposed that
  `006_m12_vod_schema.sql` was not re-runnable either, for two reasons. It declared `category_id`
  where `catalog/service.py` and `sync_service.py` both read and write `category`; and
  `CREATE TABLE IF NOT EXISTS` leaves an already-existing table untouched, so such a table had
  none of the `category` / `external_id` / `updated_at` / `stream_url` columns that the file's own
  indexes and triggers then referenced. Both `category_id` uses are renamed, and a reconciliation
  block of `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` now runs before the indexes — so the file
  converges whether the table is new or predates the file.
- **Bug found and fixed:** `003_m11_sports_schema.sql` created its three triggers without dropping
  them first. `CREATE TRIGGER` has no `IF NOT EXISTS`, so replaying that file against a database
  that already had the sports tables failed with "trigger ... already exists" — it was the one
  migration that was not actually re-runnable. It now uses `006`’s drop-then-create pattern, and
  `PackagedMigrationsAreRerunnableTests` fails the build if a future migration repeats the mistake.
- **Packaging:** the `Dockerfile` now copies `migrations/` beside `app/` (it previously copied only
  `app` and `tests`), so the container can see the SQL files.
- **Tests:** `tests/test_migrations.py` (21 cases) — discovery order, ledger reads, apply / skip,
  cursor draining for multi-statement scripts, connection lifecycle, and the re-runnability guards.

### M15 — Admin Platform ✅ COMPLETE (2026-10-02)
- **Scope decision, per the recorded strategy:** Directus stays the CRUD admin
  surface (`00_TavunoTV_Master_Strategy.md`: "Directus Studio initially, custom
  Tavuno Admin UI later where needed"). Rebuilding channel/EPG/VOD editors would
  duplicate a platform that already runs, and that effort buys more as features.
- **Read-only ops dashboard added instead — `GET /v1/ops`** (HTML shell) and
  **`GET /v1/ops/summary`** (JSON), in `app/ops.py`. Six independently-guarded
  sections: health, Dispatcharr sync, catalog counts, active playback sessions,
  migration-ledger state, and the live-channel test scope. One broken section
  yields a field, not a 500 — the dashboard matters most during an outage.
- **Gated by `X-Ops-Token`, not `require_admin`.** Under `AUTH_OPEN_ACCESS` the
  guest principal has role `user`, so every admin-gated route answers 403 while
  the app ships without login; gating on a login system that is deliberately off
  would make it unreachable. Compared with `hmac.compare_digest`.
- **The HTML shell carries no data and no token** — the page reads the operator's
  token from `sessionStorage` and sends it as a header, keeping the secret out of
  the document, the URL and the access log.
- **Read-only by construction:** `tests/test_ops.py` asserts no write verb exists
  under `/v1/ops`.
- **Verified live:** page 200 without a token; summary 401 with none or a wrong
  one, 200 with the right one, returning real values (2036 channels, 9828 EPG
  programmes, 8 migrations, all four dependencies healthy).
- **Tests:** `tests/test_ops.py` (20 cases). Suite: **407 passed / 13 skipped**.
- **Docs:** `docs/M15_Admin.md`.

### M16 — Monitoring & Operations ✅ COMPLETE (2026-10-02)
- **`GET /metrics`** on `tavuno-control` (`app/metrics.py`): request count and
  latency labelled by **route template** (never the concrete path — `/v1/channels/{channel_id}`
  rather than `/v1/channels/123456`, with 404s collapsing into one `unmatched`
  bucket, so neither a large catalog nor a scanner can mint time series),
  playback sessions started and active, the app's own PostgreSQL/Redis probes,
  and last-sync age.
- **`tavuno_active_playback_sessions` uses the same predicate as `playback.py`**
  (`status='active' AND last_seen_at >= NOW() - 90s`), so the dashboard cannot
  disagree with what `max_concurrent_streams` enforces.
- **Dispatcharr and OME are probed, not scraped, from outside.** Both go through
  the retrying circuit breaker in `app/resilience.py`, so probing them inside
  `/metrics` would stall the scrape that reports them. blackbox-exporter does it
  with a 5s timeout, and its module requires `"status": "ok"` **in the body** —
  `/v1/ome/health` answers 200 with `{"status":"unreachable"}` when OME is down,
  so a status-code probe would have reported a dead media server as healthy.
- **`postgres-exporter` uses `DATA_SOURCE_URI`/`USER`/`PASS`, not a URL DSN:**
  this deployment's database password contains `#`, a URL fragment delimiter, so
  URL-form DSNs truncate it and fail auth. v0.15.0 passes them as libpq keyword
  parameters; v0.20.1 rebuilds a URL and fails — verified both ways. Its
  `stat_bgwriter` collector is disabled because PostgreSQL 17 split
  `pg_stat_bgwriter` and this exporter still reads the old view.
- **8 alert rules** (`promtool`-validated) across availability, freshness and
  performance. The staleness rule is guarded by `> 0` so a stack with
  `DISPATCHARR_API_KEY` unset does not page forever for a deliberately disabled job.
- **3 Grafana dashboards** provisioned from files (Tavuno API, Playback,
  Infrastructure), with the datasource `uid` pinned so they bind on a fresh volume.
- **Alertmanager is configured but delivers nowhere — deliberately.** The route
  points at a receiver with no integrations: alerts are recorded and visible in
  the UI, but nothing is pushed, because the repo has no SMTP
  (`email_service.py` is a TODO stub) and an invented webhook URL would look
  configured while silently discarding pages. Adding `webhook_configs` is the one
  edit to enable delivery.
- **Verified live:** 6/6 Prometheus targets up, all health gauges 1, **0 firing
  alerts**, 3 dashboards returning data through the datasource proxy,
  `promtool`/`amtool`/blackbox config checks all pass.
- **Tests:** `tests/test_metrics.py` (19 cases) — route-template labelling, the
  concrete path never appearing as a label, 404 collapsing, the endpoint
  answering 200 with every dependency down, and timestamp parsing. Suite: **387
  passed / 13 skipped**.
- **Docs:** `docs/M16_Monitoring.md`.

### M17 — Security Hardening ✅ COMPLETE (2026-10-02)
- **TLS added.** The proxy served cleartext only; Caddy now terminates TLS
  (`tls internal`, Caddy's own CA — no domain needed) and `:80` exists purely to
  redirect. Verified: 301 → `https://localhost:8443/...`, followed to a 200,
  certificate issued by `CN=Caddy Local Authority - ECC Intermediate`. Switching
  to Let's Encrypt is one edit, documented in the Caddyfile and
  `docs/M17_Security_Hardening.md`.
- **Two Caddy subtleties found by testing, not assumed.** (a) Automatic HTTPS
  adds its own redirect using Caddy's internal port 443, which beat the explicit
  `:80` block and sent clients to a dead port — disabled with
  `auto_https disable_redirects`. (b) `{host}` already carries the request port,
  so `https://{host}:8443` rendered as an invalid double-port URL and was
  sanitised down to port 443; the redirect host is now explicit.
- **Unmatched paths no longer answer 200.** Every unproxied path used to return
  an empty `200`, which reads as success to a monitor and tells a scanner
  nothing. An explicit catch-all refuses them: `/v1/ops` and `/metrics` are now
  **404** through the proxy (verified) rather than accidentally reachable.
- **Security headers:** `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: strict-origin-when-cross-origin`, `Server` suppressed.
- **Startup warning on published default secrets** (`app/config.py`):
  `JWT_SECRET`, `PLAYBACK_TOKEN_SECRET`, `OPS_TOKEN`, `OME_API_TOKEN` are warned
  about outside development. Warns rather than raises — refusing to boot would
  turn a config smell into an outage and hide the log explaining it.
- **Already in place and verified:** bcrypt hashing, HS256 JWT (access 15m,
  refresh 30d), refresh rotation with a Redis JTI denylist and replay detection,
  120s playback tokens with `hmac.compare_digest`, login rate limiting
  (10/min per hashed email+IP), device limits and revocation.
- **Deferred, with reasons** (documented in full): no login in the shipped
  free-launch build (`AUTH_OPEN_ACCESS=true` — product decision; loopback bind is
  the only boundary), `service.py:309` password reset does not denylist refresh
  tokens, SMTP unimplemented, rate limiting is login-only and fails open on Redis
  errors, playback tokens not enforced at the proxy, and **no independent review
  — this is a self-assessment, not a penetration test**.
- **Tests:** `tests/test_config_secrets.py` (7 cases). Suite: **414 passed / 13 skipped**.
- **Docs:** `docs/M17_Security_Hardening.md`.

## Incomplete Milestones

### M13 — Catch-up / DVR / Timeshift ⚠️ PARTIAL
- Phase 1 "Timeshift / Live Rewind (DVR)" is in main history (commit b4712ac)
- Dual playback modes implemented: DIRECT_HLS and OME_DVR (commit b12df0a)
- Player offers DVR seek-back affordances (-30s/-10s)
- Remaining: full DVR window management, operator-facing DVR config
- Catch-up is implemented in the Android player: a "Go back to..." picker (offset list + exact-time wheels) driven by the DVR window the backend grants, with the windowing rules in pure, unit-tested logic (`CatchupJumps`, `CatchupContinue`)
- Catch-up from the EPG: the guide's detail strip offers "Watch from start" for a programme that is on right now, and the player enters the archive straight away

### M14 — Subscription System ⚠️ PARTIAL
- Backend subscription system exists
- Plan management exists
- No production billing integration

### M18 — Production Architecture ❌ NOT STARTED
- Only localhost development environment

### M19 — Scaling ❌ NOT STARTED
- Single instance only

### M20 — Quality / QA ⚠️ PARTIAL
- Backend tests passing (414 passed, 13 skipped on the host as of 2026-10-02 with the monitoring, ops and config suites added; 386 passed, 14 skipped in-container)
- Android tests passing (93 tests, 12 suites)
- No UI automation tests
- No load testing

### M21 — Production Release ❌ NOT STARTED
- Development only

### M22 — Advanced OTT Features ❌ NOT STARTED
- No advanced features implemented

## Remaining Tasks

- **M13 catch-up (next up).** DVR-window catch-up: port `CatchupContinue` /
  `CatchupJumpDialog` from `OwnTV-Baseline` (source at
  `C:\Users\benab\CyberLab\OwnTV-Baseline\OwnTV\app\src\main\java\tv\own\owntv\features\live\`),
  rebuild the dialog on Tavuno's components rather than copying it, and add
  EPG-driven watch-from-start inside the existing OME DVR window. No new
  recording infrastructure.
- **M14 is deferred by decision, not blocked.** Free launch means no billing;
  `AUTH_OPEN_ACCESS` already bypasses entitlement checks in `playback.py`.
- D-pad regression tests: `DPadNavigationTest.kt` / `DvrRewindTest.kt` are now behavioural
  instrumented tests (Slice E) — real key events through the emulator's input pipeline. The
  Search tab's "does DOWN escape the text field" case is the obvious next one: Compose's
  `performKeyPress` reaches the field before the IME, so it needs that injection path rather than
  the remote-style `KeyEvent` the current suite uses.
- Real VOD content population in Dispatcharr (operator task - see docs/M12_VOD_Content_Setup.md)
- Rotate the four default secrets before any public exposure (see
  `docs/M17_Security_Hardening.md`).

## Known Issues

- **Two Phase 1 D-pad fixes are in code but have neither unit tests nor an emulator walk.** Settings
  handing focus back to the row that opened a sub-screen (`restoreFocusKey` in `TavunoMainScreen` /
  `SettingsScreen`) and the Search tab's UP/DOWN escape from the text field (`onPreviewKeyEvent` in
  `SearchScreen`) are implemented, but no test in `src/test` or `src/androidTest` references either
  — the 118 Android unit tests cover none of them. Slice E's emulator harness is where both belong
  now; each still needs a behavioural pass (what does CENTER activate?) before being called done,
  and the focus hand-back in particular wants a regression test to lock it in.
- **`TAVUNO_AUTO_MIGRATE` is on in the dev stack and off in the app by default** — deliberate:
  `tavuno-infra/docker-compose.yml` and both `.env.example` files set `true` so an initialized clone
  reaches a working schema unattended, while `app/config.py` defaults to `false` so a library-style
  use never touches a schema on its own. Worth knowing when a deployed schema changes on its own.
- **The runner patches the Directus base schema; it does not create it.** On a database where
  `tavuno-infra/scripts/apply-m2-schema.ps1` has never run, startup with `TAVUNO_AUTO_MIGRATE=true`
  fails at `001` with `relation "tavuno_profiles" does not exist`. `tavuno-control` also does not
  `depends_on` `directus`, so that ordering is not enforced either. The failure is loud and fatal by
  design, but a fully unattended first boot needs the base schema applied and ordered too.
