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
- Remaining (tracked as follow-up stages): LiveTv, Sports, MovieDetails, SeriesDetails, SeasonEpisodes, Player, Splash, Login still on legacy layout/FocusableCard; FocusableCard retirement gate

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
## Incomplete Milestones

### M13 — Catch-up / DVR / Timeshift ⚠️ PARTIAL
- Phase 1 "Timeshift / Live Rewind (DVR)" is in main history (commit b4712ac)
- Dual playback modes implemented: DIRECT_HLS and OME_DVR (commit b12df0a)
- Player offers DVR seek-back affordances (-30s/-10s)
- Remaining: catch-up from EPG, full DVR window management, operator-facing DVR config

### M14 — Subscription System ⚠️ PARTIAL
- Backend subscription system exists
- Plan management exists
- No production billing integration

### M15 — Admin Platform ⚠️ PARTIAL
- Directus admin interface available
- No custom admin UI

### M16 — Monitoring & Operations ⚠️ PARTIAL
- Prometheus configured
- Grafana configured
- No custom dashboards
- No alerting configured

### M17 — Security Hardening ⚠️ PARTIAL
- HTTPS needs production configuration
- Rate limiting exists
- RBAC exists
- No security audit performed

### M18 — Production Architecture ❌ NOT STARTED
- Only localhost development environment

### M19 — Scaling ❌ NOT STARTED
- Single instance only

### M20 — Quality / QA ⚠️ PARTIAL
- Backend tests passing (210 passed, 8 skipped as of 2026-10-01)
- Android tests passing
- No UI automation tests
- No load testing

### M21 — Production Release ❌ NOT STARTED
- Development only

### M22 — Advanced OTT Features ❌ NOT STARTED
- No advanced features implemented

## Remaining Tasks

- Restyle remaining legacy screens onto the design system (LiveTv, Sports, MovieDetails, SeriesDetails, SeasonEpisodes, Player, Splash, Login) and retire FocusableCard
- Backend: normalize Directus poster/backdrop UUIDs to asset URLs; windowed EPG endpoint; home rails/favourites/resume
- Real VOD content population in Dispatcharr (operator task - see docs/M12_VOD_Content_Setup.md)
