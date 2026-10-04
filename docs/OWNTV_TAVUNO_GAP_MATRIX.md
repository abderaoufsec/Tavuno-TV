# OwnTV-Baseline → Tavuno TV Gap Matrix

Phase 1 deliverable. Companion to `docs/MASTER_IMPLEMENTATION_PLAN.md` (section 5 summarises
this file; this file is the detailed, per-surface view).

Reference tree: `C:\Users\benab\CyberLab\OwnTV-Baseline\OwnTV` (upstream `ahXN00/OwnTV`, GPL-3.0).
It is a **read-only reference** and is not part of this repository.

Target tree: `C:\Users\benab\CyberLab\Tavuno-TV\android` (`com.tavuno.tv`).

## How to read this

| Column | Meaning |
| --- | --- |
| Surface | Tavuno screen or subsystem |
| Tavuno now | What exists today |
| OwnTV ref | The reference file(s) that inform the work |
| Missing | Behaviour Tavuno does not yet have |
| Action | USE / PORT / BUILD / DROP / DONE |
| Pinned by | The test that must exist when the row is closed |

## Size comparison

| Metric | OwnTV app | Tavuno client |
| --- | --- | --- |
| Kotlin files | 202 | 71 |
| Feature packages | 20 (`features/*`) | 27 screens (`ui/screens/*`) |
| UI components | 46 | 6 (5 ported + 1 renamed) |
| Theme files | 11 | 8 |
| Player files | 19 (libmpv) | 11 (Media3) |
| Screens | ~180 surfaces | 27 |
| Persistence | Room (local-first) | HTTP + Postgres (server-authoritative) |

The persistence difference is the single most important architectural fact: OwnTV answers
questions from a local Room database, Tavuno asks `tavuno-control`. Nothing ported from OwnTV
may carry a Room query with it.

## Per-surface matrix

### Shell and navigation

| Surface | Tavuno now | OwnTV ref | Missing | Action | Pinned by |
| --- | --- | --- | --- | --- | --- |
| Shell | `ui/shell/*` (8 files) | `features/shell/OwnTVShell` (19) | — | DONE | `DPadNavigationTest` |
| Sidebar / tabs | `Sidebar.kt`, `TavunoTab.kt` | `OwnTVShell`, `ui/components/NavLadder` | ladder focus order | PORT | `DPadNavigationTest` |
| Top bar | `TopBar.kt` | `OwnTVShell` | — | DONE | `DPadNavigationTest` |
| Category rail | `CategoryRail.kt` | `ui/components/ChNavPaging` | paged rail navigation | PORT | new |
| Preview pane | `PreviewPane.kt` | `features/shell/QuickPreview` (in `more/`) | — | DONE | `DPadNavigationTest` |
| Content pane | `ContentPane.kt` | `OwnTVShell` | — | DONE | `DPadNavigationTest` |
| Dialogs | `DialogPanel.kt` | `ui/components/OwnTVPopup`, `PopupTheme` | themed popup | PORT | new |

### Screens

| Tavuno screen | OwnTV ref | Gap | Action |
| --- | --- | --- | --- |
| `splash/SplashScreen` | `features/setup/SetupWizard` | Tavuno onboarding is its own | BUILD |
| `auth/LoginScreen` | — | Tavuno-specific (device register + login) | BUILD |
| `home/HomeScreen` | `features/home/HomeScreen`, `HomeGuideSlice` | **rails API-only — no UI (F-5)** | PORT/BUILD |
| `live/LiveTvScreen` | `features/live/LiveScreen`, `LiveRailText` | catch-up runtime unverified (F-11) | PORT |
| `guide/GuideScreen` | `features/epg/EpgScreen`, `GuideCore` | DONE | DONE |
| `movies/MoviesScreen` | `features/movies/MoviesScreen` | cinematic layout variant | PORT |
| `movies/MovieDetailsScreen` | `features/movies/MovieViewModel` | **favourite + resume controls (F-5)** | PORT |
| `series/SeriesScreen` | `features/series/SeriesScreen` | cinematic layout variant | PORT |
| `series/SeriesDetailsScreen` | `features/series/SeriesViewModel` | **favourite + resume controls (F-5)** | PORT |
| `series/SeasonEpisodesScreen` | `features/series/SeriesViewModel` | episode-level resume progress | PORT |
| `sports/SportsScreen` | — (OwnTV has no sports screen) | Tavuno-specific; no tests (F-12) | BUILD |
| `search/SearchScreen` | `features/search/SearchScreen`, `SearchBar` | DONE | DONE |
| `profiles/ProfilesScreen` | `features/profiles/ProfileGate`, `ProfileComponents` | **profile gate not enforced; no tests (F-12, F-15)** | PORT |
| `customize/CustomizeScreen` | `features/customize/CustomizeItemsScreen`, `MoveToCategoryDialog`, `BulkRenameDialogs` | bulk rename + move-to-category missing; no tests (F-12) | PORT |
| `settings/SettingsScreen` | `features/settings/*` (36) | most OwnTV settings are libmpv/Room-specific | DROP/selective |
| `player/PlayerScreen` | `player/*` (19) | see player matrix | PORT |

### Player

| Surface | Tavuno now | OwnTV ref | Missing | Action | Pinned by |
| --- | --- | --- | --- | --- | --- |
| HUD | `PlayerHud.kt` | `PlayerHud`, `PlayerHudChrome`, `PlayerHudControls`, `PlayerHudDialogs` | chrome/control layering | USE as reference | `PlayerRemoteTest` |
| Remote keys | `PlayerRemote.kt` | `player/RemoteShortcutBindings`, `components/RemoteShortcutInput` | — | DONE | `PlayerRemoteTest` |
| Channel overlay / zapping | `PlayerChannelOverlay.kt`, `playback/LiveZapNavigator`, `core/LiveChannelQueue` | `features/live/LiveZapList` | — | DONE | `LiveZapNavigatorTest` |
| Subtitle tracks | `PlayerTracks.kt` | `player/SubtitleOverlay`, `components/TrackOptionText` | — (Media3 renders cues) | DONE | `PlayerTracksTest` |
| Catch-up continue | `CatchupContinue.kt` | `features/live/CatchupContinue` | live-source runtime proof | PORT | `CatchupContinueTest` + Phase 10 |
| Catch-up jump | `CatchupJumps.kt`, `CatchupJumpDialog.kt` | `features/live/CatchupJumpDialog`, `CatchupManualTimeDialog` | manual time entry | PORT | `CatchupJumpsTest` |
| Stream info | — | `player/StreamInfoOverlay` | diagnostics overlay | PORT (optional) | new |
| Player clock | — | `player/PlayerClock` | — | DROP | — |
| Mini player | — | `player/MiniPlayer`, `MiniPlayerLayout` | — | DROP | — |
| Audio-only badge / now playing | — | `player/AudioOnlyBadge`, `player/AudioNowPlayingBar` | audio track selection | PORT (optional) | — |
| Recording badge / conflict | — | `player/RecordingBadge`, `RecordingConflict` | — | DROP | — |
| Frame rate control | — | `player/FrameRateController`, `AutoFrameRatePrompt` | — | DROP | — |
| Video zoom | — | `player/VideoZoom` | — | DROP | — |
| mpv surface | — | `player/MpvVideoSurface` | Tavuno uses Media3 | DROP | — |

### Cross-cutting concerns

| Concern | Tavuno now | Gap | Action | Issue |
| --- | --- | --- | --- | --- |
| Auth | `data/repository/AuthRepository`, bearer headers | token not bound to device on refresh | BUILD | F-16 |
| Device identity | `app/devices/router.py` | caller-supplied identity not bound to principal | BUILD | F-4 |
| Favourites | `/v1/favourites` (API only) | **no UI** | PORT/BUILD | F-5 |
| Resume | `/v1/resume` (API only) | **no UI**, no episode progress | PORT/BUILD | F-5 |
| Home rails | `/v1/home` (API only) | **no UI** | PORT/BUILD | F-5 |
| Playback state | `playback/*` | guest singleton shared across profiles | BUILD | F-15 |
| Artwork | `app/assets.py` UUID → URL, Caddy front | — | DONE | — |
| Time/number formatting | `ui/screens/live/EpgTimeFormat.kt` | scattered; OwnTV centralises in `ui/format` | PORT | — |
| Profile gate | `ui/screens/profiles` | gate not enforced at entry | PORT | F-12, F-15 |
| Backup/restore of credentials | — | `allowBackup="true"` exposes token cache | BUILD | F-8 |

### Phase mapping

| Gap cluster | Lands in |
| --- | --- |
| F-1, F-3, F-4, F-8, F-2, F-17 | Phase 2 |
| F-5 (home rails, favourites, resume) + `ResumeDialog` | Phase 2 |
| F-12, F-11 + screen parity (live, profiles, home, movies, series, sports, EPG, search, player, catch-up, customize) | Phase 3 |
| Cinematic movies/series layout, `ChNavPaging`, `NavLadder` | Phase 3 |
| F-15, F-16 | Phase 7 |
| F-6 | Phase 9 |
| F-7, F-13 | Phase 8 |
| F-9, F-10, F-14 | Phase 12 |

## Bottom line

Of 202 reference Kotlin files, the ported-plus-needed set is roughly **25-35 files**
(components, formatting, a few feature behaviours, catch-up manual time). The rest is
libmpv/Room platform code that is explicitly out of scope. Tavuno's real gap is not missing
OwnTV code — it is the **missing client UI for APIs that already exist** (F-5), plus release
hardening (F-1, F-3, F-8).

