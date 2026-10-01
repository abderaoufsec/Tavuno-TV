# OwnTV → Tavuno TV Feature Port Plan

Source of truth: `C:\Users\benab\CyberLab\OwnTV-Baseline\OwnTV` (upstream `ahXN00/OwnTV`).
Target: `C:\Users\benab\CyberLab\Tavuno-TV\android` (`com.tavuno.tv`).

## Where the two apps already agree

The Tavuno TV design system was already ported from OwnTV-Baseline (theme, components,
persistent shell — see the "Design-System Port" milestone entry):

| OwnTV-Baseline | Tavuno TV | Status |
| --- | --- | --- |
| `ui/theme/*` (colors, dimens, type, animations) | `ui/theme/*` | ported |
| `ui/components/*` (FocusableSurface, PosterCard, buttons, state views) | `ui/components/*` | ported |
| `features/shell/*` (sidebar, top bar, category rail, preview, content pane) | `ui/shell/*` | ported |
| `features/home`, `movies`, `series`, `settings` | `ui/screens/*` | ported onto the system |

## What OwnTV-Baseline has that Tavuno TV does not

Ranked by value-to-effort for a Tavuno TV VOD/IPTV product:

### Tier 1 — player quality (highest value, self-contained)
- ✅ `features/subtitles/*` + `player/Subtitle*` — ported as Media3 text-track selection (Off →
  track 1 → … → Off). OwnTV's app-drawn `SubtitleOverlay` was *not* needed: Media3's `PlayerView`
  already renders cues, so only the selection layer had to be built. See
  `android/.../ui/screens/player/PlayerTracks.kt`.
- ✅ `player/RemoteShortcutBindings` — ported as `ui/screens/player/PlayerRemote.kt`
  (`resolvePlayerKey`), re-targeted from libmpv to Media3 (play/pause rocker, channel rocker,
  SUBTITLE/CAPTIONS).
- ✅ `features/live/LiveZapList` — ported as `playback/LiveZapNavigator.kt` +
  `core/LiveChannelQueue.kt` (the in-player list is `ui/screens/player/PlayerChannelOverlay.kt`).
- ⏭ `player/DirectTuneResolver` — not ported: OwnTV needs it because its catalog is large and local
  (a Room query + rebuilt windows). Tavuno already receives the exact browsed channel list, so
  `LiveZapNavigator` covers the decision half without a second resolver.
- ⏭ `CatchupContinue`, `CatchupJumpDialog` — catch-up resume; needs backend catch-up support
  (M13 is still partial).

### Tier 2 — browsing that users expect on day one
- `features/search/*` — global search across channels/movies/series.
- `features/epg/EpgScreen` + `GuideCore` — full EPG timeline grid (Tavuno has now/next only).
- `features/customize/*` — reorder/hide channels and categories, bulk rename.
- `features/movies` + `series` cinematic layout variant (commit `d0e115b`).

### Tier 3 — platform features (larger, needs Tavuno API support)
- `features/profiles/*` — multi-profile switching (backend already has `tavuno_profiles`).
- `features/recordings`, `downloads`, `multiview` — DVR/download/multi-stream (needs backend).
- `features/recovery`, `update`, `setup` wizard, `more/*`, i18n (`core/i18n`).

## Recommended approach

Port in vertical slices, keeping Tavuno TV buildable and tested after each slice:

1. ✅ **Slice A — player parity**: zap list + remote shortcut bindings. Landed in
   `ui/screens/player` + `playback/LiveZapNavigator` + `core/LiveChannelQueue`; no backend work.
   (commit `807a699`)
2. ✅ **Slice A2 — subtitles**: Media3 text-track selection, walked as `Off → track 1 → … → Off`.
   Decision layer in `ui/screens/player/PlayerTracks.kt`; no backend work.
3. ✅ **Slice B — search**: a grouped `GET /v1/search` on the backend (channels + movies + series)
   plus a Search tab in the shell. (commit `8320ae1`)
4. ⏭ **Slice C — full EPG guide**: needs a windowed EPG endpoint (already a tracked backend task).
5. ⏭ **Slice D — customize + profiles**: needs backend write endpoints.

Each slice: port the Kotlin, rewire to Tavuno's `TavunoApiService`/repositories, then
`gradlew test assembleDebug` + emulator D-pad pass.

## Status

| Slice | State | Pinned by |
| --- | --- | --- |
| A — player parity (zap, remote map, HUD, channel overlay) | ✅ done | `LiveZapNavigatorTest`, `PlayerRemoteTest` |
| A2 — subtitles | ✅ done | `PlayerTracksTest` |
| B — search (backend endpoint + Android tab) | ✅ done | `test_catalog_service.py` |
| C — full EPG guide | ⏭ not started (blocked on the windowed EPG endpoint) | — |
| D — customize + profiles | ⏭ not started (blocked on backend write endpoints) | — |

Android unit suite after Slice A2: **65 passed / 0 failed**; backend suite: **235 passed / 8 skipped**.

## Not a straight copy

OwnTV-Baseline's player is **libmpv/FFmpeg** (`libmpv` dep) — Tavuno TV uses
**Media3/ExoPlayer**. Player-adjacent code (tracks, subtitles, shortcuts) must be re-targeted
to Media3 APIs rather than copied verbatim.