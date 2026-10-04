# Tavuno TV — Master Implementation Plan

Status: active
Owner: engineering
Created: 2026-10-04
Recovery point: tag `pre-master-implementation` → commit `06108fb`

This document is the single source of truth for bringing Tavuno TV to a production-ready
state. It records what exists, what is reused, what must be fixed, and the order of work.

---

## 1. Scope decisions (settled)

| Decision | Value |
| --- | --- |
| Product client | `android/` (`com.tavuno.tv`). OwnTV-Baseline is a **read-only reference**, not a dependency and not part of this repository. |
| Repository strategy | Single repository. No `android` sub-remote, no repo split. `origin` = `abderaoufsec/Tavuno-TV`. |
| Backend | `tavuno-control/` (FastAPI + Postgres + Redis). Tavuno-owned, server-authoritative. |
| Platform | `tavuno-infra/` Docker Compose (16 services). Directus = content admin, Dispatcharr = IPTV source aggregation. |

### Explicitly out of scope

OwnTV-Baseline implements these platform features. Tavuno TV has no backend counterpart for
them and the product does not require them, so they are **not** ported:

multiview · downloads / offline sync · DVR recording · TMDB metadata authoring ·
i18n / Weblate · Stalker portal + M3U/Xtream setup wizard · companion remote HTTP server ·
backup & restore · database recovery · in-app self-update.

The OwnTV-side `features/tavuno/` working tree (8 uncommitted files) is scratch work in a
third-party checkout. It is not tracked here and is not a deliverable.

---

## 2. Architecture

```
android/ (Compose for TV, com.tavuno.tv)
    ui/theme, ui/components, ui/shell        design system + persistent shell
    ui/navigation, ui/screens/*              27 screens
    data/api, data/repository, data/model    Retrofit + Gson, bearer auth
    playback, core                           Media3/ExoPlayer, zap navigation
                     │  HTTPS + bearer token
                     ▼
tavuno-control/ (FastAPI)
    app/{auth,devices,catalog,playback,epg,sports,profiles,customize,favourites,resume,home}
    migrations/*.sql (001..009), auto-migrate gated by TAVUNO_AUTO_MIGRATE
                     │
                     ▼
tavuno-infra/ (Docker Compose)
    postgres · redis · directus (CMS/asset origin) · caddy (TLS front)
    dispatcharr (IPTV aggregation) · OME (streaming) · grafana · prometheus
```

**Authority split.** Tavuno is server-authoritative: catalog, artwork URLs, EPG, profiles,
favourites and resume state are served by `tavuno-control`. OwnTV is local-first (Room DB).
Any ported idea must be re-targeted to Tavuno's repositories — never copied as a Room query.

**Artwork.** Directus is the asset origin; `app/assets.py` converts Directus UUIDs into public
URLs, and Caddy fronts `:8055` so the Android client only needs one host.

---

## 3. Baselines (must not regress)

| Surface | Baseline |
| --- | --- |
| Backend tests | 486 passed / 13 skipped |
| Android unit tests | 118 passed (17 suites) |
| Android connected tests | 5 passed (2 suites) |
| Endpoint smoke | 19 / 19 return 2xx (`tools/smoke_endpoints.py`) |
| A6 verifier | `tools/verify_a6.py` green |

Every phase must end with these green. A phase that cannot demonstrate them is not done.

---

## 4. What already exists

Backend A6 is complete: favourites and resume services + routes, migration `009` reconciling
artwork columns, `/v1/home` contract, Directus UUID→URL resolution, artwork write-through.

Client slices already landed from the OwnTV reference (documented in
`docs/OwnTV_Feature_Port_Plan.md`):

| Slice | Delivered |
| --- | --- |
| A | zap list, remote shortcut map, player HUD, channel overlay |
| A2 | subtitle track selection via Media3 |
| B | global search (endpoint + screen) |
| C | windowed EPG grid (endpoint + D-pad grid) |
| D | per-profile customization + profiles (endpoints + screens) |

---

## 5. Reuse matrix (OwnTV reference → Tavuno TV)

Measured against the current trees: OwnTV app = 202 Kotlin files; Tavuno client = 71.

Action vocabulary: **USE** (behaviour reference), **PORT** (re-target and keep),
**BUILD** (Tavuno-specific, do not port), **DROP** (not wanted).

### 5.1 Design system

OwnTV ships 46 components and 11 theme files. Tavuno ported **5 components + 1 renamed**:

| OwnTV | Tavuno | Status |
| --- | --- | --- |
| `ui/components/FocusableSurface` | `ui/components/FocusableSurface` | PORTED |
| `ui/components/PosterCard` | `ui/components/PosterCard` | PORTED |
| `ui/components/RoundedPanel` | `ui/components/RoundedPanel` | PORTED |
| `ui/components/StateViews` | `ui/components/StateViews` | PORTED |
| `ui/components/ChannelLogoTile` | `ui/components/ChannelLogoTile` | PORTED |
| `ui/components/OwnTVButton` | `ui/components/TavunoButton` | PORTED (renamed) |
| `Glass`, `PopupTheme`, `StackBlur`, `FontCustomization` | — | BUILD if needed (polish, optional) |
| `ChannelNumber`, `ProgressRing`, `CountBadge`, `SortChip`, `ProviderChip`, `GenreColor`, `NavDuotoneIcon`, `MenuAction`, `GridFocus`, `FocusTrap` | — | PORT (small, improves rails/grids) |
| `ResumeDialog`, `InAppToast`, `NumberInputDialog`, `TextInputDialog`, `DayStepperDialog` | — | PORT (needed for F-5 resume UI) |
| `CompanionFailureText`, `DownloadStatusStrip`, `RecordingBadge`, `StorageBrowser`, `TrailerPlayerScreen`, `MoveOrderOverlay` | — | DROP (feature out of scope) |

### 5.2 Player

OwnTV `player/` = 19 files against libmpv. Tavuno uses Media3, so player code is a
**behaviour** reference only.

| Area | Action |
| --- | --- |
| subtitle selection | PORTED (Media3 text tracks; OwnTV's `SubtitleOverlay` deliberately not needed) |
| remote shortcuts | PORTED as `PlayerRemote.resolvePlayerKey` |
| zap list | PORTED as `playback/LiveZapNavigator` + `core/LiveChannelQueue` |
| catch-up continue/jump | PORTED (`CatchupContinue`, `CatchupJumps`, `CatchupJumpDialog`) — runtime verification outstanding (F-11) |
| `DirectTuneResolver` | DROP — Tavuno already receives the browsed channel list |
| `StreamInfoOverlay`, `PlayerClock`, `PlayerHudChrome/Controls/Dialogs` | USE as layout reference for HUD polish |
| `MiniPlayer`, `AudioOnlyBadge`, `AudioNowPlayingBar`, `RecordingBadge`, `RecordingConflict`, `FrameRateController`, `AutoFrameRatePrompt`, `VideoZoom`, `MpvVideoSurface` | DROP |
| audio-track switching | PORT only if audio-track selection is wanted |

### 5.3 Features

| OwnTV feature | Tavuno status | Action |
| --- | --- | --- |
| `features/search` (2) | `ui/screens/search` (2) + `/v1/search` | DONE |
| `features/epg` (3, incl. `GuideCore`) | `ui/screens/guide` (2) + `/v1/epg/window` | DONE |
| `features/customize` (6, incl. bulk rename) | `ui/screens/customize` (2) + `/v1/customize` | PARTIAL — bulk rename, move-to-category not ported |
| `features/profiles` (4, incl. `ProfileGate`) | `ui/screens/profiles` (1) + `/v1/profiles` | PARTIAL — verify gate/switch flow |
| `features/home` (4) | `ui/screens/home` (1) + `/v1/home` | PARTIAL — rails exist in API only (F-5) |
| `features/live` (7) | `ui/screens/live` (2) | PARTIAL — catch-up runtime unverified (F-11) |
| `features/movies` (2) / `series` (2) | `ui/screens/movies` (2) / `series` (3) | PARTIAL — cinematic layout variant not ported |
| `features/settings` (36) | `ui/screens/settings` (1) | USE selectively; most of OwnTV's settings are libmpv/Room-specific → DROP |
| `features/shell` (19) | `ui/shell` (8) | USE as reference; Tavuno shell is already the ported seam |
| `features/setup` (7) | `ui/screens/splash` + `auth` | BUILD — Tavuno onboarding is its own (device register + login) |
| `features/subtitles` (3, OpenSubtitles) | — | DROP unless external subtitle search is wanted |
| `features/downloads`, `recordings`, `multiview`, `recovery`, `update`, `more` | — | DROP (out of scope) |
| `ui/format` (`TimeFormat`, `NumberFormat`) | `ui/screens/live/EpgTimeFormat` | PORT — consolidate formatting into one place |
| `core/i18n` (2) | — | DROP (Tavuno ships English) |

---

## 6. Known issues

Full detail in `docs/POST_A6_PRODUCT_AUDIT.md` (F-1..F-17). Corrections to that audit are
folded back below; where the two disagree, the entries here are authoritative.

Priority: **P1** = release/security/correctness; **P2** = quality/completeness.

| ID | P | Issue | Evidence |
| --- | --- | --- | --- |
| F-1 | P1 | Release R8 has no keep rule for `com.tavuno.tv.data.model.**`; Gson reads model fields reflectively, so release builds can fail at runtime. No release build has ever been produced. | `android/app/build.gradle.kts:27` `isMinifyEnabled = true`; `proguard-rules.pro` keeps Retrofit/OkHttp/Gson/Media3/DataStore only |
| F-3 | P1 | Cleartext HTTP permitted to **every** host, not just dev hosts. | `network_security_config.xml` `<base-config cleartextTrafficPermitted="true">` |
| F-4 | P1 | `/v1/devices` trusts caller-supplied identity without binding it to the authenticated principal. | `tavuno-control/app/devices/router.py` |
| F-5 | P1 | Favourites, resume and home rails are API-only — no client UI. | no references under `ui/screens/*` |
| F-6 | P1 | No CI: nothing runs the suites automatically. | `.github/workflows` absent |
| F-7 | P1 | Directus `:8055` and Dispatcharr `:9191` bound to `0.0.0.0`. | `tavuno-infra/docker-compose.yml` |
| F-8 | P1 | `android:allowBackup="true"` — token cache is eligible for backup extraction. | `AndroidManifest.xml` |
| F-2 | **P1** | **Restated and upgraded.** Locally `.env` files hold real secrets (zero `CHANGE_ME`), so the audit's "defaults present" was wrong about `.env`. The real defect is in the **compose layer**: five `${VAR:-literal}` fallbacks supply publicly-known secrets when a deployment forgets to set them. A deploy missing `JWT_SECRET` gets `tavuno-jwt-secret-key-change-in-production` and **anyone can forge tokens**. | `tavuno-infra/docker-compose.yml:430,433,435,443,462` |
| F-9 | P2 | `docs/tavuno-api-contract.md` predates favourites/resume/home. | docs |
| F-10 | P2 | `docs/Android_Instrumented_Tests_Status.md` predates the A5 test rewrite. | docs |
| F-11 | P2 | Catch-up continue/jump is unit-tested but has never been exercised against a live catch-up-enabled source. | Phase 10 pending |
| F-12 | P2 | No tests for sports, profiles or customize screens. | test tree |
| F-13 | P2 | Local-network allowlist leaks dev hosts. | infra config |
| F-14 | P2 | `.shots/` untracked and unignored. `docs/images/` untracked and **unreferenced** — two branding JPEGs (`app-icon.jpg`, `app-background.jpg`) that no file points at. | `git status`, repo-wide `git grep` |
| F-15 | P2 | Guest/playback singleton shared across profiles. | playback layer |
| F-16 | P2 | Token not bound to device on refresh. | auth layer |
| F-17 | P2 | **Narrowed.** Auto-migration is already gated by `TAVUNO_AUTO_MIGRATE` with rollback via the connection context manager. Remaining: production default of that flag + a documented rollback path. | `app/main.py:45` |
| F-18 | **P1** | **New, found while fixing F-3.** Playback URLs are issued from `OME_PLAYBACK_BASE_URL`, which defaults to `http://localhost:8080/media` — cleartext **and** a host no remote client can resolve. `PlaybackUrls.rewriteLoopbackForEmulator` patches the host to `10.0.2.2` for the emulator but leaves the scheme HTTP, so this only ever worked in local development. With F-3 now refusing cleartext, a release build cannot play until the base URL is HTTPS and publicly resolvable (Caddy in front of OME). | `app/config.py:76`, `tavuno-infra/docker-compose.yml:431`, `tavuno-infra/.env.example:33`, `android/.../playback/PlaybackUrls.kt` |

### F-14 correction (action differs from the audit)

- `.shots/` → add to `.gitignore` (done in Phase 0).
- `docs/images/` → **committed, but the audit's implication was wrong.** A repo-wide grep finds
  no reference to `app-icon.jpg` or `app-background.jpg` anywhere; the Android client ships only
  vector launcher resources. They are branding assets with no consumer. They are now tracked so
  they cannot be lost, and remain unreferenced — a later decision, not a silent assumption.

### F-2 detail — the five compose fallbacks

| Variable | Fallback | Consequence if unset |
| --- | --- | --- |
| `JWT_SECRET` | `tavuno-jwt-secret-key-change-in-production` | access tokens are forgeable by anyone reading this repo |
| `PLAYBACK_TOKEN_SECRET` | `tavuno-playback-secret-key` | stream tokens are forgeable |
| `TAVUNO_OPS_TOKEN` | `tavuno-ops-local` | ops endpoints reachable with a published token |
| `OME_API_TOKEN` | `tavuno-m1-local` | OME control plane reachable |
| `AUTH_GUEST_DEVICE_KEY` | `tavuno-tv-guest` | guest identity is guessable |

**Fix (Phase 2):** remove the literal fallback for these five (`${VAR:?...}` or no fallback) so
compose **fails loudly** instead of silently starting insecure, and add a `app/config.py`
startup guard that refuses known-weak or production-default values when `TAVUNO_ENV=production`.
`milestones.md` records "four default secrets"; the count is five.


---

## 7. Roadmap

Each phase ends green on the section 3 baselines, or it is not done.

| Phase | Deliverable | Verified by | Blocker |
| --- | --- | --- | --- |
| 0 | Recovery tag `pre-master-implementation`; this document; `.gitignore` fix | `git tag`, doc review | — |
| 1 | `docs/OWNTV_TAVUNO_GAP_MATRIX.md` | reviewed against both trees | — |
| 2 | F-1..F-17. F-1 ships a **release APK that is installed and exercised**; F-3 narrows cleartext to dev hosts; F-4 binds device identity to the principal; F-8 disables backup; F-2 adds a production secret guard; **F-5 is the bulk** (home rails, favourites, resume UI) | pytest, `assembleRelease`, emulator | emulator must be booted |
| 3 | UX parity per screen (live nav, profiles flow, home, movies, series, sports, EPG, search, player, catch-up, customize), each pinned by a D-pad instrumented test | `connectedAndroidTest` | emulator |
| 4 | Dispatcharr **backup first**, then clean config: accounts, filters, numbering, backup streams, stream profiles, VOD scan, EPG. `docs/DISPATCHARR_SETUP.md`, `docs/DISPATCHARR_PLUGIN_MATRIX.md` | live 0.28.0 instance | **provider credentials** |
| 5 | Tavuno ↔ Dispatcharr sync: channels, groups, VOD, series, EPG, logos, Tavuno-owned IDs | sync run + endpoint checks | provider credentials |
| 6 | Directus upload → Caddy → Android display round-trip | real upload + on-device render | — |
| 7 | Hardening: rate limits, input validation, error surfaces | test suite | — |
| 8 | Dev/prod compose split — no local allowlists, no dev-only ports, no permissive HTTP in prod | config review + local prod-profile boot | — |
| 9 | CI: `.github/workflows/backend.yml` + `android.yml` | CI run | push access |
| 10 | Real content matrix (live/UHD/VOD/series/EPG/catch-up) | live tests | **provider credentials** |
| 11 | Performance: `docker stats --no-stream`, `docker system df`, `docker compose ps` | recorded numbers | — |
| 12 | Docs: `CURRENT_STATE.md`, `PRODUCTION_DEPLOYMENT.md`; update `tavuno-api-contract.md` (F-9) and `Android_Instrumented_Tests_Status.md` (F-10) | review | — |
| 13 | Final full test run + `docs/FINAL_PRODUCT_AUDIT.md` with PASS / PARTIAL / FAIL / NOT VERIFIED | suites | — |

## 8. Risks and blockers

| Risk | Impact | Mitigation |
| --- | --- | --- |
| **F-1 is invisible until release** | A release build that crashes on first API call — the classic "compiles, does not work". | Add a precise `data.model.**` keep rule, then install and exercise a real release APK. Do not close this on a successful compile. |
| **Emulator not running** | Phases 3, 10 and F-1's runtime test cannot be verified. AVD `TavunoTV_API34` exists; `adb devices` is empty. | Boot the AVD before Phase 3. |
| **No provider credentials** | Phases 4 and 10 are hard-blocked: no real Xtream/M3U import, no real content matrix. | Obtain credentials. Until then, Phase 4 can still cover backup, strategy docs and plugin inventory against the live instance. |
| **OwnTV is GPL-3.0** | Tavuno currently has no `LICENSE`. The design system is derived from OwnTV. | Engineer's note only, flagged once: a `LICENSE` + `NOTICE` naming OwnTV-Baseline closes the attribution gap. Not blocking; no further prompts will be raised. |
| **Architecture mismatch** | Porting Room-based code into a server-authoritative client produces dead code. | Every port is re-targeted to `TavunoApiService`/repositories; no Room dependency is added. |
| **Scope creep from 202 reference files** | Effort spent on features that are explicitly out of scope. | Section 1's out-of-scope list and section 5's DROP rows are binding. |
| **Docs drift** | F-9/F-10 already drifted once. | Phase 12 makes doc updates part of the deliverable, not an afterthought. |

## 9. Commit policy

One phase, one focused commit; report the exact test results with it.

```
audit:   product audit F-1..F-17
fix:     release-hardening (F-1 R8, F-3 cleartext, F-8 backup, F-4 device binding)
fix:     production secret guard (F-2)
feat:    home rails, favourites and resume UI (F-5)
feat:    TV navigation
feat:    profiles
test:    Android screen coverage (F-12, F-11)
config:  Dispatcharr clean configuration
feat:    VOD/EPG sync
ci:      pipelines
docs:    current state, deployment, API contract
```

Never push a broken build. Never report an emulator result that was not produced by a real
run.

## 10. Definition of done

1. Release APK builds, installs and runs — not just compiles.
2. Every baseline in section 3 is green and reported with real numbers.
3. F-1..F-17 are closed or explicitly marked PARTIAL/NOT VERIFIED with a reason.
4. No feature is claimed as working that was not exercised on the running system.

