# Tavuno TV

A complete streaming platform with backend infrastructure and Android TV client.

## Overview

Tavuno TV is a production-ready streaming platform consisting of:
- **Backend**: FastAPI-based Tavuno Control API
- **Infrastructure**: Docker-based services (PostgreSQL, Redis, Dispatcharr, OME, etc.)
- **Android Client**: Native Android TV application built with Jetpack Compose

## Architecture

```
Android TV App
    │ HTTPS
    ▼
Tavuno Backend
    ├── Authentication
    ├── Live TV
    ├── Sports
    ├── Movies
    ├── Series
    ├── EPG
    └── Playback Authorization
    ▼
Existing Tavuno infrastructure
```

## Quick Start

### Backend

```bash
cd tavuno-infra
docker-compose up -d
```

Backend API will be available at `http://localhost:8000`

### Android Client

```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

## Documentation

- [Android Client Documentation](docs/android-client.md)
- [API Contract](docs/tavuno-api-contract.md)
- [Milestone Status](milestones.md)
- [Architecture](docs/01_TavunoTV_Architecture.md)

## Completed Features

### Backend (M0-M7)
- ✅ Infrastructure (Docker, PostgreSQL, Redis, Directus, Dispatcharr, OME)
- ✅ Authentication system
- ✅ Device management
- ✅ Catalog (channels, categories, movies, series, seasons, episodes)
- ✅ EPG system
- ✅ Playback authorization (live, movie, episode)
- ✅ Sports data integration

### Android Client (M8-M12)
- ✅ TV-optimized UI with Jetpack Compose
- ✅ Authentication (login, session, logout)
- ✅ Live TV with category filtering
- ✅ Sports events display
- ✅ Movies catalog and details
- ✅ Series catalog with seasons/episodes
- ✅ Media3/ExoPlayer integration
- ✅ D-pad navigation support

## Remaining Work

- EPG now/next display in Live TV screen
- Runtime verification on Android TV device/emulator
- Android UI automation tests
- Catch-up/DVR functionality
- Production deployment
- Advanced OTT features

## License

See project license for details.
