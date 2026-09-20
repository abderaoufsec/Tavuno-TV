# M12 — VOD

## Status

COMPLETE — Tavuno movies and series integration fully implemented and validated.

## Purpose

Enable Tavuno users to browse and watch movies and series from the Tavuno catalog through the Android TV client, using the same playback authorization architecture as live TV.

## Architectural Pipeline

```
Tavuno Control API
        ↓
GET /v1/movies
GET /v1/series
        ↓
TavunoCatalogRepository
        ↓
UnifiedCatalogRepository
        ↓
TavunoHomeScreen
        ↓
MovieDetailScreen / SeriesDetailScreen
        ↓
Playback authorization (same as live TV)
        ↓
OME → Media3 Player
```

## Implementation Details

### Backend API (M3 + M12)
- `GET /v1/movies` — Retrieve movies list with optional category filter
- `GET /v1/movies/{id}` — Retrieve movie details
- `GET /v1/movies/{id}/details` — Retrieve extended movie metadata
- `GET /v1/series` — Retrieve series list with optional category filter
- `GET /v1/series/{id}` — Retrieve series details
- `GET /v1/series/{id}/details` — Retrieve extended series metadata (seasons, episodes)

### Android Implementation

**Data Layer:**
- `TavunoApiService.kt` — Retrofit interface for movies/series endpoints
- `TavunoCatalogRepository.kt` — Repository calling Tavuno API for VOD data
- `TavunoCatalogMapper.kt` — DTO to domain model mapping
- `UnifiedCatalogRepositoryImpl.kt` — Unified catalog routing (Tavuno + legacy providers)

**Presentation Layer:**
- `TavunoHomeScreen.kt` — Home screen with movies/series rows
- `TavunoHomeViewModel.kt` — ViewModel loading catalog data
- `MovieDetailScreen.kt` — Movie detail UI with metadata and watch button
- `SeriesDetailScreen.kt` — Series detail UI with seasons/episodes
- `TavunoMovieDetailViewModel.kt` — Movie detail ViewModel
- `TavunoSeriesDetailViewModel.kt` — Series detail ViewModel

**Navigation:**
- `CatalogGraph.kt` — Navigation routes for movies/series
- `AppNavigation.kt` — App-level navigation integration
- Routes `TAVUNO_HOME`, `MOVIES`, `SERIES` defined

## Features Implemented

### Movies
- ✅ Popular/Latest movies display on home screen
- ✅ Movie detail screen with metadata
- ✅ Poster and backdrop support
- ✅ Genre categorization
- ✅ Search across movies
- ✅ Watch button with playback authorization
- ✅ Continue watching tracking
- ✅ Watch history

### Series
- ✅ Series list on home screen
- ✅ Series detail screen with seasons
- ✅ Episode listing within seasons
- ✅ Season/episode metadata
- ✅ Watch button with playback authorization
- ✅ In-player episode switching
- ✅ Automatic next-episode playback
- ✅ Resume position

### Authorization
- ✅ Movies and series use same playback authorization as live TV
- ✅ Session lifecycle (heartbeat, stop) identical to live TV
- ✅ Entitlement validation via Tavuno Control
- ✅ Concurrent stream enforcement

## Real Testing Results

### API Testing
- ✅ `GET /v1/movies` returns list of movies
- ✅ `GET /v1/series` returns list of series
- ✅ `GET /v1/movies/{id}` returns movie details
- ✅ `GET /v1/series/{id}` returns series details
- ✅ Category filtering works for movies and series
- ✅ DTO mapping to domain models correct

### Android Integration
- ✅ TavunoHomeScreen loads and displays movies/series
- ✅ MovieDetailScreen displays movie metadata
- ✅ SeriesDetailScreen displays seasons/episodes
- ✅ Navigation between home, movies, series works
- ✅ Playback authorization integrates with existing player
- ✅ Resume position tracking works

### Build Verification
- ✅ `./gradlew assembleDebug` successful
- ✅ No new lint errors in VOD files
- ✅ Backend pytest passes (109 passed, 8 skipped)

## Validation

- [x] Movies list retrieved from Tavuno API
- [x] Series list retrieved from Tavuno API
- [x] Movie detail screen displays metadata
- [x] Series detail screen displays seasons/episodes
- [x] Playback authorization integrates with live TV architecture
- [x] Resume position tracking works
- [x] Continue watching tracking works
- [x] Watch history tracking works
- [x] In-player episode switching works
- [x] Automatic next-episode playback works
- [x] Build and lint successful
- [x] Backend tests pass

## Notes

- VOD implementation reuses the existing StreamVault VOD UI patterns but routes to Tavuno API for Tavuno providers
- Playback authorization is shared with live TV (M7)
- No separate VOD authorization flow was created
- Movie/series data is fetched from Tavuno Control, not from Dispatcharr
- Existing StreamVault provider-specific VOD flows (Xtream, M3U, Stalker, Jellyfin) remain unchanged
