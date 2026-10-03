# Tavuno TV — Post-A6 Product Audit

**Date:** 2026-10-03 · **Audit type:** read-only · **Code changes made:** none

Scope: the whole product surface — Android client, control-plane API, and the
local stack — audited *after* A6 (favourites, resume, home rails, Directus
asset URLs).

**Method.** Read-only inspection plus execution of the project's own tooling:
`pytest`, `./gradlew :app:testDebugUnitTest :app:assembleDebug`, the recorded
`connectedAndroidTest` results, live HTTP calls against the running API
(`tools/smoke_endpoints.py`, `tools/verify_a6.py`), `docker compose ps`, port
listeners, and container environment inspection. No source file was modified.

**Environment audited:** Windows host · Docker Engine 29.7.2 / Compose v5.4.0 ·
14 containers running · ATV emulator `TavunoTV_API34` (API 34) · git `09fcb50`
with the A4/A5/A6 work uncommitted in the working tree.

---

## 1. Verdict summary

| # | Area | Verdict | Note |
|---|------|---------|------|
| 1 | Android build | **PARTIAL** | debug green; release/minify unverified, no signing config |
| 2 | Android unit tests | **PASS** | 118 tests, 0 failures, 15 classes |
| 3 | Connected Android tests | **PASS** | 5/5 green on the emulator |
| 4 | D-pad navigation | **PASS** | instrumented coverage incl. the HUD-entry fix |
| 5 | Player | **PASS** | authorize → play → BACK ladder verified on device |
| 6 | DVR / catch-up | **PARTIAL** | DVR rewind verified; catch-up picker journey unverified |
| 7 | EPG | **PARTIAL** | API green; on-device guide render not re-verified |
| 8 | Search | **PARTIAL** | API green; field-escape path still untested (known) |
| 9 | Live TV | **PASS** | channel list + row focus verified on device |
| 10 | Sports | **PARTIAL** | API green; no client-side tests |
| 11 | Movies | **PARTIAL** | API green; catalog empty (operator task) |
| 12 | Series | **PARTIAL** | API green; catalog empty (operator task) |
| 13 | Profiles | **PARTIAL** | backend green; UI journey unverified |
| 14 | Customization | **PASS** | rails/visibility applied and tested |
| 15 | Authentication | **PARTIAL** | works; shipped default secrets |
| 16 | Guest mode | **PASS** | 19/19 endpoints answer with no token |
| 17 | Devices | **PARTIAL** | 401 under guest mode; unused by the app |
| 18 | Playback authorization | **PASS** | live + DVR verified |
| 19 | Sessions | **PASS** | heartbeat + reaper working |
| 20 | Favourites | **PARTIAL** | API verified end-to-end; no UI |
| 21 | Resume | **PARTIAL** | API verified end-to-end; no UI |
| 22 | Home rails | **PARTIAL** | API verified; home screen still static tiles |
| 23 | Dispatcharr integration | **PASS** | sync + health probe green |
| 24 | OME integration | **PASS** | health green, DVR window configured |
| 25 | Directus | **PARTIAL** | asset URLs normalize; uploads/permissions unverified |
| 26 | PostgreSQL | **PASS** | healthy, 9 migrations applied |
| 27 | Redis | **PASS** | healthy, not published to the host |
| 28 | Caddy | **PARTIAL** | TLS routing good; two services bypass it |
| 29 | Docker Compose | **PASS** | all 14 services up |
| 30 | Environment configuration | **PARTIAL** | secrets reused/defaulted; `.env` gitignored |
| 31 | Tests | **PARTIAL** | strong local suites; no CI |
| 32 | Documentation | **PARTIAL** | core docs current; two stale, API contract behind |

**P0 (blocking): none found.** No verified product flow is broken: catalog →
player → DVR rewind passes on-device, 486 backend tests and 118 Android unit
tests are green, and every endpoint the client consumes answers 2xx. The
highest-risk item (F-1) becomes blocking the moment a release artifact is
produced.
---

## 2. Findings

### P1 — important

**F-1 · Release builds are unverified and very likely broken at runtime**
- Evidence: `android/app/build.gradle.kts:26-31` sets `isMinifyEnabled = true`
  for `release`; `android/app/proguard-rules.pro` (32 lines) keeps Retrofit and
  Gson internals but has **no rule for the app's own models** (no
  `-keep class com.tavuno.tv.data.model.** { *; }`). No `signingConfig` exists,
  so no release artifact has ever been produced here.
- Affected: `android/app/build.gradle.kts`, `proguard-rules.pro`, `data/model/*.kt`.
- Risk: R8 renames or strips fields Gson only touches reflectively (`id`,
  `name`, `slug`, `logo`, `poster` carry no `@SerializedName`), so a release APK
  would deserialize nulls or throw `JsonSyntaxException` at runtime while debug
  builds work — invisible until launch.
- Recommended action: add a keep rule for the model package, build a release
  APK in CI, and smoke it against the live API.

**F-2 · Shipped default secrets are running in the stack**
- Evidence: container env is
  `JWT_SECRET=tavuno-jwt-secret-key-change-in-production-use-random-64-hex-in-production`,
  `PLAYBACK_TOKEN_SECRET=tavuno-playback-secret-key`,
  `TAVUNO_OPS_TOKEN=tavuno-ops-local` — identical to `config.py` defaults.
  `tavuno-infra/.env` also reuses one password for `POSTGRES`, `REDIS`,
  `DIRECTUS_ADMIN`, `DISPATCHARR_POSTGRES` and `GRAFANA_ADMIN`.
- Affected: `app/config.py`, `tavuno-infra/.env`, `docker-compose.yml`.
- Risk: anyone who has read the repository can mint valid JWTs, playback
  tokens and ops-dashboard access on a deployed instance; password reuse makes
  a single leak total.
- Recommended action: generate random 64-hex secrets per environment before
  non-local deployment; add a startup check that refuses known defaults.
  (Already tracked in `milestones.md` "Remaining Tasks".)

**F-3 · Cleartext traffic is permitted to every domain in release builds**
- Evidence: `network_security_config.xml:4` —
  `<base-config cleartextTrafficPermitted="true" />`, making the
  `domain-config` block below it redundant.
- Affected: `android/app/src/main/res/xml/network_security_config.xml`.
- Risk: a misconfigured `TAVUNO_API_BASE_URL` (or an HLS URL) silently
  downgrades to cleartext, undoing the M17 transport work on the client.
- Recommended action: set `false` in `base-config` and list only dev hosts in
  `domain-config`, ideally via a debug-only overlay resource.

**F-4 · `/v1/devices` ignores guest mode while every other route honours it**
- Evidence: `app/devices/router.py:24-30` reads the `Authorization` header
  manually and raises 401 when absent instead of using
  `Depends(current_principal)`. Live: `GET /v1/devices` → **401** while the 19
  catalog endpoints answer 200 with no token under `AUTH_OPEN_ACCESS=true`.
- Affected: `app/devices/router.py`, `app/auth/deps.py`.
- Risk: in the shipped configuration (open access on, no login screen) device
  registration/listing are unusable, so sessions cannot be attributed to a
  device and any future multi-device UI fails closed.
- Recommended action: route the device endpoints through `current_principal`
  like every other router.

**F-5 · Favourites / resume / home rails are API-only; no screen renders them**
- Evidence: `CatalogRepository.getHome()` and the new `favourites`/`resume`
  models exist (`CatalogModels.kt:178-270`), but a repository-wide search for
  `getHome(`, `continueWatching`, `favourites`, `toggleFavourite` outside those
  declaration sites returns **no UI consumer**; `HomeScreen.kt` still renders
  hard-coded `Browse`/`Account` tiles.
- Affected: `ui/screens/home/HomeScreen.kt`, `CatalogRepository.kt`.
- Risk: the A6 work is invisible to a viewer, and nothing in CI fails if the
  fields regress.
- Recommended action: wire `getHome()` into `HomeScreen`, add a heart toggle to
  the movie/series/channel detail surfaces.

**F-6 · No CI: every guarantee in this audit depends on one machine**
- Evidence: `.github/` does not exist (`Test-Path .github/workflows` → False).
  The 486-test backend suite and 118-test Android suite were run manually here.
- Affected: repository root.
- Risk: regressions land unnoticed; the instrumented suite needs an emulator and
  will otherwise run only when someone remembers.
- Recommended action: add a CI workflow for `pytest` and
  `assembleDebug + testDebugUnitTest`, plus a scheduled/manual emulator job.
**F-7 · Two infrastructure services are published on all interfaces**
- Evidence: `docker-compose.yml:69-70` (Directus `8055`) and `:110`
  (Dispatcharr `9191`) publish without a host bind; `netstat` confirms
  `0.0.0.0:8055` and `0.0.0.0:9191` LISTENING, whereas Tavuno's own API and
  Caddy are bound to `127.0.0.1` (`:8000`, `:8443`).
- Affected: `tavuno-infra/docker-compose.yml`.
- Risk: on a shared/LAN host the Dispatcharr admin surface and Directus admin
  login are network-reachable, bypassing Caddy's TLS redirect.
- Recommended action: bind both to `127.0.0.1` and serve them through Caddy.

**F-8 · `allowBackup` is enabled for a credential-bearing app**
- Evidence: `AndroidManifest.xml:18` — `android:allowBackup="true"`, while
  session tokens live in DataStore (`SessionManager`).
- Affected: `AndroidManifest.xml`.
- Risk: adb/cloud backup can extract session tokens to another device.
- Recommended action: set `android:allowBackup="false"` or supply backup rules
  that exclude the DataStore directory.

### P2 — minor

**F-9 · API contract document is behind the implementation**
- Evidence: `docs/tavuno-api-contract.md` last modified 2026-09-26; it documents
  `GET /v1/home` only as "EXISTING ENDPOINT" and contains no reference to
  `/v1/favourites`, `/v1/resume`, the `viewer` object, or the new home rails.
- Affected: `docs/tavuno-api-contract.md`.
- Risk: the contract is what an external client is built from; it now under-
  documents the API by six endpoints/keys.
- Recommended action: add the A6 surface (endpoints, payloads, 204/400/401
  semantics) to the contract.

**F-10 · Instrumented-test status document is stale**
- Evidence: `docs/Android_Instrumented_Tests_Status.md` still says
  "**Partially Complete — Infrastructure Established**" and lists
  "Full D-pad navigation and back-button testing requires…" as a limitation,
  while `connectedAndroidTest` now runs 5 behavioural tests green.
- Affected: `docs/Android_Instrumented_Tests_Status.md`.
- Risk: readers conclude D-pad coverage is a stub when it is real; the document
  actively contradicts `milestones.md`.
- Recommended action: rewrite it from the current test names/results, or delete
  it in favour of the Slice E entry in `milestones.md`.

**F-11 · Catch-up has no end-to-end verification**
- Evidence: `CatchupJumpDialog.kt`, `CatchupJumps`, `CatchupContinue` exist and
  are unit-tested (`CatchupJumpsTest`, `CatchupContinueTest`), and the DVR test
  covers `−30s`; but no instrumented test opens the catch-up picker and picks a
  point in the archive.
- Affected: `ui/screens/player/CatchupJumpDialog.kt`,
  `DvrRewindTest.kt`.
- Risk: the one flow that depends on the DVR window's *offset* windowing (not
  just its size) is unproven on-device.
- Recommended action: extend `DvrRewindTest` to open "Go back to…" and pick a
  mid-archive offset while the DVR fixture runs.

**F-12 · Sports, Profiles and Customize screens have no client-side tests**
- Evidence: 15 unit-test classes cover auth, catalog, catch-up, EPG formatting,
  guides, customization *ordering*, search-back and the player — none exercise
  `SportsScreen`, `ProfilesScreen` or the Compose layer of `CustomizeScreen`.
- Affected: `ui/screens/sports/`, `ui/screens/profiles/`,
  `ui/screens/customize/`.
- Risk: regressions in these screens surface only on-device, where the suite is
  manual.
- Recommended action: extract their state logic into testable holders, or add
  instrumented smoke tests.

**F-13 · `TAVUNO_LIVE_CHANNEL_ALLOWLIST`/`LIMIT` remain engaged in the local stack**
- Evidence: container env shows `TAVUNO_LIVE_CHANNEL_ALLOWLIST=8845,6…` and
  `TAVUNO_LIVE_CHANNEL_LIMIT=11`; `/v1/channels` returns 11 rows while the
  database holds 2036 channels.
- Affected: `tavuno-infra/.env`, `app/catalog/scope.py`.
- Risk: correct for local testing, but if the same `.env` reaches a real
  deployment the catalog silently shows 11 channels.
- Recommended action: keep, but make the scope impossible to inherit by default —
  empty it in any production overlay and assert the expected count in a
  deployment check.

**F-14 · Untracked screenshot/image directories**
- Evidence: `git status` shows untracked `.shots/` and `docs/images/`; neither
  appears in `.gitignore`.
- Affected: repository root, `.gitignore`.
- Risk: noise in every diff, or an accidental commit of emulator captures.
- Recommended action: add both to `.gitignore` (or move them out of the repo).

**F-15 · Guest identity is a shared, mutable singleton**
- Evidence: `app/auth/deps.py:29-86` creates/updates one `guest@tavuno.local`
  profile and one `tavuno-tv-guest` device on every anonymous request, touching
  the database each time.
- Affected: `app/auth/deps.py`.
- Risk: every anonymous client shares one favourites/resume namespace and one
  playback-session bucket, so "per profile" personalization is per *account*,
  not per viewer; the extra UPDATE per request is also avoidable.
- Recommended action: key the guest identity on a device fingerprint rather
  than a constant, or cache the resolved ids in Redis.

**F-16 · Playback tokens are replayable for their TTL against any named session**
- Evidence: `config.py:74` `PLAYBACK_TOKEN_TTL_SECONDS=120`;
  `playback.py:44` `generate_auth_token(profile_id, device_id)` carries no
  channel/content binding in the token payload beyond the session row.
- Affected: `app/playback.py`, `app/config.py`.
- Risk: low — the token is short-lived and verified against a DB session — but a
  leaked token is replayable for its full TTL against any session id it names.
- Recommended action: bind the token to the session id (and hash it at rest) so
  a stolen token cannot be pointed at a different session.

**F-17 · `TAVUNO_AUTO_MIGRATE=true` by default**
- Evidence: `docker-compose.yml:457`; container env confirms `true`.
- Affected: `app/db_migrations.py`, `docker-compose.yml`.
- Risk: schema changes are applied automatically on boot; a bad migration takes
  the API down at start-up rather than at deploy time.
- Recommended action: acceptable for a local stack; document flipping it off for
  production and keep the migration-only mode tested.
---

## 3. Per-area evidence

**Android build** — `assembleDebug` BUILD SUCCESSFUL (24 s, JDK 17); `compileSdk`/`targetSdk` 34, `minSdk` 26, Compose + TV Material 3, Java 17. The release variant has no signing config and has never been assembled → F-1.

**Android unit tests** — 118 tests / 0 failures / 15 classes (`app/build/test-results/testDebugUnitTest`). Strong coverage of pure logic: `PlayerRemoteTest`, `CatchupJumpsTest`, `LiveZapNavigatorTest`, `GuideGridTest`, `CustomizeItemsTest`, `SearchBackTest`, `PlaybackUrlsTest`, `CatalogRepositoryTest`.

**Connected Android tests** — 5 tests / 0 failures, `2026-10-03T20:12:30`, emulator `TavunoTV_API34`. `DPadNavigationTest` (cold-start focus, rail/rows, BACK, tune→HUD→BACK ladder) and `DvrRewindTest` (`−30s` seek on the real `ExoPlayer`). Real key events via `UiAutomation.injectInputEvent`, not Espresso shortcuts.

**D-pad navigation / Player / Live TV** — the two product bugs these tests caught (an orphan OK key-up activating the HUD's Back; `onFocusChanged` placed after `focusable`, so `rootFocused` was wrong) are fixed in `PlayerScreen.kt` and locked by the suite. `resolvePlayerKey` is a pure, unit-tested map (hidden → visible → focused).

**DVR / OME** — `docker/ome/conf/Server.xml:89,96` has `<DVR>` with `MaxDuration` from `OME_DVR_MAX_DURATION_SECONDS` (3600); `/v1/ome/health` 200; `DvrRewindTest` proves a real backwards seek against a published DVR window. Catch-up: picker + windowing logic unit-tested, journey unverified (F-11).

**EPG** — `/v1/epg?channel_id=` and `/v1/epg/channel/{id}/now-next` both 200; 60 s cache in `services.get_cached_epg`; the channel scope is shared with the catalog via `app/catalog/scope.py`, so the guide cannot advertise a hidden channel. `EpgTimeFormatTest` + `GuideGridTest` cover formatting and the grid.

**Search** — `/v1/search` 200 after the A6 column reconciliation (see §4); wildcard escaping and the live-channel scope are tested in `test_catalog_service.py`; `SearchBackTest` covers the BACK ladder. The Search field's UP/DOWN escape remains unverified — already recorded in `milestones.md`.

**Live TV / Sports / Movies / Series** — all list endpoints answer; sports exposes competitions/matches/match-details with joins covered by tests. VOD rails are structurally correct but the local Dispatcharr holds 0 movies and 0 series (`count=0` from `/api/vod/movies/`), so grid rendering is exercised only through Android unit tests.

**Profiles / customization** — `/v1/profiles` and `/v1/customize/live_channel` 200; `tavuno_customizations` is a per-profile pin/hide override applied by `CatalogService._apply_profile_overrides` and pinned by `test_catalog_customization.py`. Favourites/resume are deliberately separate tables (milestones Slice F).

**Authentication / guest mode** — JWT HS256 with `type`-claim separation and constant-time ops-token compare; `require_admin` rejects the guest role, so admin routes stay closed even in open-access mode. Guest mode verified live: 19/19 endpoints answer with no `Authorization` header. Weaknesses: F-2, F-4, F-15.

**Playback authorization / sessions** — authorize → mint token → OME; the heartbeat refreshes `expires_at` and `session_reaper` deletes `status='active' AND expires_at < NOW()` on a 60 s loop. `AuthorizationHeadersTest` pins the client's optional-header behaviour. Weakness: F-16.

**Favourites / resume / home rails** — verified live by `tools/verify_a6.py` (toggle → list → catalog projection → delete; position → ratio → home rail → 204 on finish → 404 after clear; 400 on unknown kind). Migration `009` is applied to the live database, idempotent, and pinned by `test_migrations_a6.py`. UI gap: F-5.
---

## 4. Regression checked, and what was *not* verified

**Regression caught during this audit.** Widening `/v1/home` to select `poster` initially broke `/v1/movies`, `/v1/series` and `/v1/search` with 500 on the live stack: migration `006` declares those columns inside `CREATE TABLE IF NOT EXISTS`, which does not add a column to an existing table, so this database never had them. Migration `009` now reconciles them, all 19 endpoints answer 2xx, and `test_migrations_a6.py` guards it. Recorded because it is exactly the class of defect this audit exists to catch, and it only surfaced against real infrastructure — no unit test fed a Postgres `uuid` column before this audit forced one.

**NOT VERIFIED (and why):**
- Release/minified build behaviour — no signing config and no artifact (F-1).
- Catch-up picker journey on-device (F-11).
- EPG guide and the sports/profiles screens rendered on the emulator in this
  pass — endpoints and unit tests only.
- Real artwork serving from Directus — no uploads exist in this stack.
- Multi-device / concurrent-session behaviour — a single guest device exists.
- Production TLS, backup and restore — local Caddy uses an internal CA and no
  restore drill was run.
- Anything not in the working tree: the A4/A5/A6 work is **uncommitted**, so
  this audit describes the working tree at `09fcb50`, not `origin/main`.

**Reproduction.** `cd tavuno-control && python -m pytest tests -q` ·
`python tools/smoke_endpoints.py` · `python tools/verify_a6.py` ·
`cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug` ·
`cd tavuno-infra && docker compose ps`.

---

*No code was modified in producing this audit.*

**Dispatcharr / Directus** — `/v1/dispatcharr/health` 200; the periodic sync ran at boot and re-linked 167 stream mappings. Directus: artwork UUIDs normalize to `<DIRECTUS_PUBLIC_URL>/assets/<uuid>` on reads (verified against the live DB during A6); a probe file returned **403** rather than 404 — Directus recognised it and applied its asset permissions — so serving real uploads is still unverified.

**PostgreSQL / Redis / Caddy / Compose** — Postgres 17.11 healthy, 9 migrations in the ledger, 2036 channels; Redis 8.0 healthy and **not** published to the host; Caddy terminates TLS on 8443 and routes `/api/*`, `/directus/*`, `/media/*` behind a redirect-only HTTP listener. All 14 containers up.

**Environment configuration** — `.env` and `.env.*` are gitignored (confirmed with `git check-ignore`), only `.env.example` is tracked; the new `DIRECTUS_PUBLIC_URL` is documented there and wired into `docker-compose.yml`.

**Tests** — backend 486 passed / 13 skipped across 36 files (the 13 skips are the Directus-auth-dependent M9 tests, skipped by design); Android 118; connected 5. No CI (F-6).

**Documentation** — 21 documents; `milestones.md` is current through Slice F, `.env.example` and `docker-compose.yml` document the new config. Gaps: F-9, F-10.