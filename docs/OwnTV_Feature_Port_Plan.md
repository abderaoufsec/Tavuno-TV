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
- `features/subtitles/*` + `player/Subtitle*` — subtitle discovery, styling, renderer agreement.
- `player/RemoteShortcutBindings` — remote/media-key bindings (play, pause, seek, track).
- `player/DirectTuneResolver` — fast channel zap without a full re-prepare.
- `features/live/LiveZapList`, `CatchupContinue`, `CatchupJumpDialog` — channel-zap list and
  catch-up resume (complements Tavuno's existing M13 DVR work).

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

1. **Slice A — player parity**: subtitles + remote shortcut bindings + zap list.
   Touches only `ui/screens/player` and adding `player/` helpers; no backend work.
2. **Slice B — search**: `features/search` against existing `/v1/channels|movies|series`.
3. **Slice C — full EPG guide**: needs a windowed EPG endpoint (already a tracked backend task).
4. **Slice D — customize + profiles**: needs backend write endpoints.

Each slice: port the Kotlin, rewire to Tavuno's `TavunoApiService`/repositories, then
`gradlew test assembleDebug` + emulator D-pad pass.

## Not a straight copy

OwnTV-Baseline's player is **libmpv/FFmpeg** (`libmpv` dep) — Tavuno TV uses
**Media3/ExoPlayer**. Player-adjacent code (tracks, subtitles, shortcuts) must be re-targeted
to Media3 APIs rather than copied verbatim.