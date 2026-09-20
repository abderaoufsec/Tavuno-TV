# M11 — Sports

## Status

PARTIAL — Backend complete with full sports support, Android implementation with basic UI but missing match details and navigation integration.

## Purpose

Create Tavuno's sports experience with sports-specific data models (competitions, matches, teams) and a dedicated sports UI that connects sports events to authorized live broadcasts.

## Current Implementation

### Backend API (Complete)
**Database Schema:**
- `migrations/003_m11_sports_schema.sql` — Creates sports tables
  - `tavuno_competitions` (id, name, slug, sport, category_id, external_id, is_active)
  - `tavuno_teams` (id, name, slug, competition_id, logo, external_id, is_active)
  - `tavuno_matches` (id, competition_id, home_team_id, away_team_id, channel_id, kickoff, status, home_score, away_score, external_id, is_active)

**Models:**
- `app/catalog/models.py` — Competition, Team, Match models
- Includes field validation and serialization

**API Endpoints:**
- `GET /v1/sports/competitions` — List competitions (filterable by sport)
- `GET /v1/sports/competitions/{id}` — Get competition details
- `GET /v1/sports/competitions/{id}/matches` — Get matches for competition (filterable by status, limit)
- `GET /v1/sports/matches` — List matches (filterable by status, limit)
- `GET /v1/sports/matches/{id}` — Get match details
- `GET /v1/sports/matches/{id}/details` — Get match details with team names and channel info

**Tests:**
- `tests/test_catalog_service.py` — Sports mapping and query tests
- All sports tests passing (126 passed, 8 skipped)

### Android Implementation (Partial)
**Data Layer:**
- `TavunoApiService.kt` — Sports API endpoints
- `AuthDtos.kt` — Sports DTOs (CompetitionDto, TeamDto, MatchDto, MatchDetailsDto)
- `TavunoSportsMapper.kt` — DTO to domain model mapping
- `TavunoSportsRepository.kt` — Sports data repository
- `AuthDataModule.kt` — DI wiring for sports components

**Domain Models:**
- `domain/model/SportsModels.kt` — Competition, Team, Match, MatchDetails, MatchStatus

**Presentation Layer:**
- `TavunoSportsViewModel.kt` — Sports ViewModel with loading/error states
- `TavunoSportsScreen.kt` — Basic sports homepage UI
  - Competitions row
  - Live matches column
  - Upcoming matches column
  - Loading and error states

**Missing Components:**
- ❌ No dedicated competition matches screen
- ❌ No match detail screen with team logos and channel info
- ❌ No navigation integration into main app flow
- ❌ No watch button connecting match to live channel
- ❌ No EPG connection from match to program
- ❌ No in-app navigation from sports to player

## Completed Milestone Specs

**Data Models:**
- ✅ Competition (id, name, slug, sport, category_id, external_id, is_active)
- ✅ Team (id, name, slug, competition_id, logo, external_id, is_active)
- ✅ Match (id, competition_id, home_team_id, away_team_id, channel_id, kickoff, status, home_score, away_score, external_id, is_active)
- ✅ MatchStatus enum (UPCOMING, LIVE, FINISHED, POSTPONED)
- ✅ MatchDetails (with team names, logos, channel mapping)

**Features Completed:**
- ✅ Sports homepage (competitions, live matches, upcoming matches)
- ✅ Competitions display
- ✅ Match list (live and upcoming)
- ✅ Backend API for all sports queries
- ✅ Android data layer (repository, mapper, DTOs)
- ✅ Android ViewModel with state management
- ✅ Basic Android UI (TV Compose components)

**Features Missing:**
- ❌ Competition-specific match screen
- ❌ Match detail screen
- ❌ Watch button from sports event
- ❌ EPG connection
- ❌ Navigation integration

## Validation

### Backend
- [x] Sports database schema created
- [x] Sports models implemented
- [x] Sports API endpoints implemented
- [x] Sports tests passing (126 passed, 8 skipped)
- [x] Competition mapping and queries working
- [x] Team mapping working
- [x] Match mapping with scores working
- [x] Match details with team names and channel info working

### Android
- [x] Sports domain models implemented
- [x] Sports DTOs implemented
- [x] Sports mapper implemented
- [x] Sports repository implemented
- [x] Sports ViewModel implemented
- [x] Sports homepage UI implemented
- [x] DI wiring complete
- [x] Build successful
- [ ] Competition matches screen not implemented
- [ ] Match detail screen not implemented
- [ ] Navigation not integrated
- [ ] Watch button not implemented
- [ ] EPG connection not implemented

## Notes

- Backend sports support is complete and tested
- Android has basic sports UI but lacks navigation and match detail screens
- The current sports homepage shows competitions, live matches, and upcoming matches
- Match-to-channel mapping exists in backend but not used in Android UI
- This milestone is PARTIAL — backend complete, Android basic UI complete, but missing navigation and match details

## Files Changed

**Backend:**
- `tavuno-control/migrations/003_m11_sports_schema.sql` (new)
- `tavuno-control/app/catalog/models.py` (modified)
- `tavuno-control/app/catalog/service.py` (modified)
- `tavuno-control/app/main.py` (modified)
- `tavuno-control/tests/test_catalog_service.py` (modified)

**Android:**
- `tavuno-tv-android/domain/src/main/java/com/streamvault/domain/model/SportsModels.kt` (new)
- `tavuno-tv-android/data/src/main/java/com/streamvault/data/remote/tavuno/AuthDtos.kt` (modified)
- `tavuno-tv-android/data/src/main/java/com/streamvault/data/remote/tavuno/TavunoApiService.kt` (modified)
- `tavuno-tv-android/data/src/main/java/com/streamvault/data/remote/tavuno/TavunoCatalogRepository.kt` (modified)
- `tavuno-tv-android/data/src/main/java/com/streamvault/data/remote/tavuno/TavunoSportsMapper.kt` (new)
- `tavuno-tv-android/data/src/main/java/com/streamvault/data/remote/tavuno/TavunoSportsRepository.kt` (new)
- `tavuno-tv-android/data/src/main/java/com/streamvault/data/di/AuthDataModule.kt` (modified)
- `tavuno-tv-android/feature/catalog/src/main/java/com/streamvault/feature/catalog/presentation/tavuno/TavunoSportsViewModel.kt` (new)
- `tavuno-tv-android/feature/catalog/src/main/java/com/streamvault/feature/catalog/presentation/tavuno/TavunoSportsScreen.kt` (new)
