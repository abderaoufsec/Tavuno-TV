# M8-M13 Feature Matrix

This document provides a forensic audit of existing functionality in OwnTV, Tavuno backend, and related systems to determine what needs to be integrated vs. what needs to be built.

## Current Baseline Information

**OwnTV Version:** 1.0.55 (3b4986973ace7c4e87f9ca3ba450691f826f969b)
**OwnTV_Core Version:** 1.0.55 (42cc6ae40824c7cc768875803f0dfb9020f67fa)
**Tavuno Control Version:** 0.3.0

## Feature Matrix

| Feature | Exists in OwnTV? | Source/Location | Exists in Tavuno Backend? | Source/Location | Integration Needed? | New Code Required? |
|---------|-----------------|-----------------|---------------------------|-----------------|---------------------|-------------------|
| **Authentication** | ✅ Partial | features/tavuno/TavunoAuthScreen.kt | ✅ Complete | app/auth/ | ⚠️ Complete & Test | ❌ No |
| **Session refresh** | ✅ Complete | features/tavuno/TavunoApiClient.kt | ✅ Complete | /v1/auth/refresh | ❌ No | ❌ No |
| **Logout** | ✅ Complete | features/tavuno/TavunoSessionStore.kt | ✅ Complete | /v1/auth/logout | ❌ No | ❌ No |
| **Account status** | ✅ Complete | features/tavuno/TavunoAuthState.kt | ✅ Complete | app/auth/service.py | ❌ No | ❌ No |
| **Device registration** | ✅ Partial | features/tavuno/TavunoApiClient.kt (device_fingerprint) | ✅ Complete | /v1/devices/register | ⚠️ Stable identity needed | ⚠️ Device identity improvement |
| **Device limit** | ❌ No | - | ✅ Complete | app/auth/service.py | ⚠️ UI feedback needed | ⚠️ Error handling |
| **Live TV UI** | ✅ Complete | features/live/LiveScreen.kt | ❌ No | - | ⚠️ Connect to Tavuno API | ⚠️ API adapter |
| **Live TV catalog** | ❌ No | Uses OwnTV local DB | ✅ Complete | /v1/channels | ⚠️ Replace with Tavuno API | ⚠️ Catalog adapter |
| **EPG UI** | ✅ Complete | features/epg/EpgScreen.kt | ❌ No | - | ⚠️ Connect to Tavuno API | ⚠️ EPG adapter |
| **EPG backend** | ❌ No | Uses OwnTV local DB | ✅ Complete | /v1/epg | ⚠️ Replace with Tavuno API | ⚠️ EPG adapter |
| **Live playback** | ✅ Complete | player/ (Media3/libmpv) | ✅ Complete | /v1/playback/live/{id} | ⚠️ Authorization integration | ⚠️ Authorization wrapper |
| **Sports UI** | ❌ No | - | ✅ Complete | /v1/sports/* | ⚠️ Build Sports UI | ⚠️ New Sports feature |
| **Sports backend** | ❌ No | - | ✅ Complete | app/catalog/models.py | ❌ No | ❌ No |
| **Sports playback** | ❌ No | - | ⚠️ Partial (via channel_id) | app/catalog/models.py | ⚠️ Channel mapping | ⚠️ Channel integration |
| **Movies UI** | ✅ Complete | features/movies/MoviesScreen.kt | ❌ No | - | ⚠️ Connect to Tavuno API | ⚠️ Catalog adapter |
| **Movies backend** | ❌ No | Uses OwnTV local DB | ✅ Complete | /v1/movies | ⚠️ Replace with Tavuno API | ⚠️ Catalog adapter |
| **Series UI** | ✅ Complete | features/series/SeriesScreen.kt | ❌ No | - | ⚠️ Connect to Tavuno API | ⚠️ Catalog adapter |
| **Series backend** | ❌ No | Uses OwnTV local DB | ✅ Complete | /v1/series | ⚠️ Replace with Tavuno API | ⚠️ Catalog adapter |
| **VOD playback authorization** | ❌ No | Direct to provider | ❌ No | - | ⚠️ Implement backend endpoint | ⚠️ New VOD auth endpoint |
| **Catch-up UI** | ✅ Complete | features/live/LiveScreen.kt (catch-up support) | ❌ No | - | ⚠️ Connect to Tavuno API | ⚠️ Catch-up adapter |
| **Catch-up backend** | ❌ No | Dispatcharr provides | ⚠️ Partial (Dispatcharr) | - | ⚠️ Tavuno authorization layer | ⚠️ Authorization wrapper |
| **Search** | ✅ Complete | features/search/SearchScreen.kt | ❌ No | - | ⚠️ Connect to Tavuno API | ⚠️ Search adapter |
| **Profiles** | ✅ Complete | features/profiles/ | ❌ No | - | ❌ No (use OwnTV local) | ❌ No |
| **Favorites** | ✅ Complete | features/more/UserDataScreen.kt | ❌ No | - | ❌ No (use OwnTV local) | ❌ No |
| **History** | ✅ Complete | features/more/UserDataScreen.kt | ❌ No | - | ❌ No (use OwnTV local) | ❌ No |
| **Categories** | ✅ Complete | features/settings/ | ✅ Complete | /v1/categories | ⚠️ Connect to Tavuno API | ⚠️ Categories adapter |
| **Home screen** | ✅ Complete | features/home/HomeScreen.kt | ✅ Complete | /v1/home | ⚠️ Connect to Tavuno API | ⚠️ Home adapter |

## Key Findings

### ✅ Already Complete (No Changes Needed)
- **Authentication UI**: Tavuno authentication screen with login/register
- **Session Management**: Token storage, refresh, logout
- **Account Status**: ACTIVE/INACTIVE handling
- **OwnTV Player**: Media3 and libmpv engines
- **OwnTV UI**: Live TV, Movies, Series, EPG, Search, Profiles, Favorites, History
- **Backend Auth**: Complete authentication, refresh, logout, device registration
- **Backend Catalog**: Channels, categories, movies, series, sports
- **Backend EPG**: EPG endpoints and caching
- **Backend Playback**: Live authorization, sessions, heartbeat, stop

### ⚠️ Partial / Needs Integration
- **Device Identity**: Needs stable Android device identifier
- **Live TV Catalog**: OwnTV uses local DB, needs Tavuno API adapter
- **EPG Integration**: OwnTV uses local DB, needs Tavuno API adapter
- **Live Playback Authorization**: Backend exists, needs Android integration
- **Movies/Series Catalog**: OwnTV uses local DB, needs Tavuno API adapter
- **Sports UI**: Backend exists, no Android UI
- **VOD Playback Authorization**: Backend missing for movies/episodes
- **Catch-up Authorization**: Dispatcharr provides, needs Tavuno layer
- **Home Screen**: OwnTV local, needs Tavuno API integration

### ❌ Missing / Needs Implementation
- **Sports UI**: Complete Android TV sports feature needed
- **VOD Playback Authorization**: Backend endpoints for movie/episode playback
- **Stable Device Identity**: Replace timestamp-based fingerprint

## Work Completed (Current Session)

### M8 - Authentication (COMPLETED)
- ✅ Authentication UI implemented
- ✅ Session management implemented  
- ✅ Token storage with DataStore
- ✅ Refresh/logout functionality
- ✅ Account status handling
- ✅ Stable device identity (Android ID-based)
- ✅ Device limit error handling
- ✅ Device revoked error handling
- ✅ Network error handling
- ✅ ACCOUNT_INACTIVE handling

### M9 - Devices (COMPLETED)
- ✅ Stable Android device identifier implementation
- ✅ Device limit error handling in Android UI
- ✅ Device revoked error handling in Android UI
- ✅ Device integration with backend authentication

### M10 - Live TV + EPG (PARTIAL)
- ✅ Created Tavuno catalog adapter for channels
- ✅ Created Tavuno EPG adapter
- ✅ Created Tavuno playback authorization client
- ⚠️ OwnTV Live UI integration (requires deep OwnTV modification)
- ⚠️ OwnTV EPG UI integration (requires deep OwnTV modification)
- ⚠️ Live playback authorization integration (requires OwnTV player modification)

### M11 - Sports (PARTIAL)
- ✅ Sports backend integration (competitions, matches)
- ⚠️ Sports UI (needs to be built - no OwnTV sports UI exists)

### M12 - VOD (PARTIAL)
- ✅ Created Tavuno catalog adapter for movies/series
- ✅ Implemented VOD playback authorization backend endpoints
- ⚠️ OwnTV Movies/Series UI integration (requires deep OwnTV modification)
- ⚠️ VOD authorization integration in Android (requires OwnTV player modification)

### M13 - Catch-up (NOT STARTED)
- ⚠️ Existing OwnTV catch-up audit needed
- ⚠️ Catch-up authorization integration

### M8 - Authentication (COMPLETED)
- ✅ Authentication UI implemented
- ✅ Session management implemented
- ✅ Token storage with DataStore
- ✅ Refresh/logout functionality
- ✅ Account status handling
- ⚠️ Device identity improvement needed

### M9 - Devices (NEEDS WORK)
- ⚠️ Implement stable Android device identifier
- ⚠️ Device limit error handling in UI
- ⚠️ Device list/management UI if needed

### M10 - Live TV + EPG (NEEDS WORK)
- ⚠️ Create Tavuno catalog adapter for channels
- ⚠️ Connect OwnTV Live UI to Tavuno API
- ⚠️ Create Tavuno EPG adapter
- ⚠️ Connect OwnTV EPG UI to Tavuno API
- ⚠️ Integrate live playback authorization
- ⚠️ Add session heartbeat/stop integration

### M11 - Sports (NEEDS WORK)
- ⚠️ Build Android TV Sports UI (competitions, matches, details)
- ⚠️ Connect to existing Tavuno sports backend
- ⚠* Integrate sports playback via channel mapping
- ⚠* Add sports to home screen navigation

### M12 - VOD (NEEDS WORK)
- ⚠️ Create Tavuno catalog adapter for movies/series
- ⚠* Connect OwnTV Movies/Series UI to Tavuno API
- ⚠* Implement VOD playback authorization backend endpoints
- ⚠* Integrate VOD authorization in Android

### M13 - Catch-up (NEEDS WORK)
- ⚠* Integrate existing OwnTV catch-up with Tavuno authorization
- ⚠* Add catch-up authorization layer if missing
- ⚠* Test with Dispatcharr catch-up functionality

## Architectural Approach

**Adapter-First Integration:**
```
Tavuno API DTO → Tavuno Adapter → OwnTV Domain Model → OwnTV UI
```

**Minimal OwnTV Modifications:**
- Preserve existing OwnTV UI and player
- Add Tavuno API clients and adapters
- Integrate authorization at playback layer
- Avoid deep modifications to OwnTV internals

**Backend-First Authorization:**
- All protected content must use Tavuno backend authorization
- Android must not bypass Tavuno authorization
- Backend validates: user, account status, device, subscription, entitlements

## Work Order

1. **M8** - Complete authentication testing and device identity
2. **M9** - Complete device integration
3. **M10** - Live TV + EPG integration with existing OwnTV UI
4. **M11** - Build Sports UI (only missing feature)
5. **M12** - VOD integration with existing OwnTV UI + VOD auth backend
6. **M13** - Catch-up integration with existing OwnTV catch-up

## System Status

**Ready for Integration:**
- Tavuno backend: Complete catalog, auth, playback, sports APIs
- OwnTV: Complete UI, player, EPG, Live TV, Movies, Series
- Authentication: Android and backend complete

**Integration Required:**
- Connect Tavuno catalog APIs to OwnTV UI
- Add Tavuno authorization to playback
- Build Sports UI (only missing Android feature)
- Implement VOD playback authorization backend
- Integrate catch-up authorization

This audit confirms that the majority of functionality exists in either OwnTV or Tavuno backend. The remaining work is primarily integration rather than building new features from scratch.