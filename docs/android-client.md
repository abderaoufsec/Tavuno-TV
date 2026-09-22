# Tavuno TV Android Client Documentation

## Overview
The Tavuno TV Android client is a production-ready Android TV application built with Kotlin and Jetpack Compose for TV. It provides a clean, fast TV interface for streaming live TV, sports, movies, and series content from the Tavuno backend.

## Architecture

### Project Structure
```
android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/tavuno/tv/
│   │   │   │   ├── core/              # Dependency injection and app initialization
│   │   │   │   ├── data/
│   │   │   │   │   ├── api/          # Retrofit API service interfaces
│   │   │   │   │   ├── model/        # Data transfer objects
│   │   │   │   │   ├── repository/   # Data repositories
│   │   │   │   │   └── local/        # Local storage (DataStore)
│   │   │   │   ├── network/          # Network configuration
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/   # Reusable UI components
│   │   │   │   │   ├── navigation/   # Navigation setup
│   │   │   │   │   ├── screens/      # Screen composables
│   │   │   │   │   └── theme/        # Theme and styling
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── TavunoApplication.kt
│   │   │   ├── res/                  # Android resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                    # Unit tests
│   ├── build.gradle.kts              # App-level build configuration
│   └── proguard-rules.pro           # ProGuard rules
├── build.gradle.kts                  # Project-level build configuration
├── settings.gradle.kts              # Gradle settings
└── gradle.properties                # Gradle properties
```

### Technology Stack
- **Language**: Kotlin 1.9.20
- **UI Framework**: Jetpack Compose with Compose for TV
- **Architecture**: MVVM with Repository pattern
- **Networking**: Retrofit 2.9.0 + OkHttp 4.12.0
- **Async**: Kotlin Coroutines + Flow
- **Local Storage**: DataStore Preferences
- **Media Player**: Media3/ExoPlayer 1.2.0
- **Image Loading**: Coil 2.5.0
- **Dependency Injection**: Simple singleton module (no DI framework)
- **Target SDK**: 34 (Android 14)
- **Min SDK**: 26 (Android 8.0)

## API Integration

### Authentication Flow
1. **Registration**: `POST /v1/auth/register`
   - User creates account with username, email, password
   - Returns profile_id and basic profile info

2. **Login**: `POST /v1/auth/login`
   - User authenticates with email, password, device fingerprint
   - Returns JWT access token, refresh token, and profile info
   - Tokens stored securely in DataStore

3. **Token Refresh**: `POST /v1/auth/refresh`
   - Automatic refresh when access token expires
   - Uses refresh token to obtain new access token

4. **Logout**: `POST /v1/auth/logout`
   - Invalidates refresh token on server
   - Clears local session storage

### Playback Authorization
1. **Live Playback**: `POST /v1/playback/live/{channel_id}` - IMPLEMENTED AND VERIFIED
   - Requires valid JWT access token
   - Returns short-lived playback URL with token
   - Enforces subscription, device limits, and concurrency limits

2. **Movie Playback**: `POST /v1/playback/movie/{movie_id}` - IMPLEMENTED AND VERIFIED
   - Requires valid JWT access token
   - Returns short-lived playback URL with token
   - Enforces subscription, device limits, and concurrency limits
   - Uses stream URL from Dispatcharr sync

3. **Episode Playback**: `POST /v1/playback/episode/{episode_id}` - IMPLEMENTED AND VERIFIED
   - Requires valid JWT access token
   - Returns short-lived playback URL with token
   - Enforces subscription, device limits, and concurrency limits
   - Uses stream URL from Dispatcharr sync

4. **Heartbeat**: `POST /v1/playback/heartbeat` - IMPLEMENTED
   - Extends playback session every 60 seconds
   - Prevents session timeout

5. **Stop Playback**: `POST /v1/playback/stop` - IMPLEMENTED
   - Terminates playback session
   - Frees up concurrent stream slot

### Data Models
All API responses are mapped to Kotlin data classes in `data/model/`:
- `AuthModels.kt`: Authentication-related models
- `CatalogModels.kt`: Channels, categories, movies, series
- `SportsModels.kt`: Competitions, matches, teams
- `PlaybackModels.kt`: Playback authorization and session models
- `EpgModels.kt`: EPG program data

## UI Components

### Screens
1. **SplashScreen**: App launch, session validation - IMPLEMENTED
2. **LoginScreen**: User authentication - IMPLEMENTED
3. **HomeScreen**: Main navigation hub - IMPLEMENTED
4. **LiveTvScreen**: Channel listing with category filtering - IMPLEMENTED
5. **SportsScreen**: Live and upcoming sports events - IMPLEMENTED
6. **MoviesScreen**: Movie catalog grid - IMPLEMENTED
7. **MovieDetailsScreen**: Movie details and playback - IMPLEMENTED
8. **SeriesScreen**: Series catalog grid - IMPLEMENTED
9. **SeriesDetailsScreen**: Series details and seasons - IMPLEMENTED
10. **SeasonEpisodesScreen**: Episode listing and playback - IMPLEMENTED
11. **PlayerScreen**: Media3 video player with controls - IMPLEMENTED (live, movie, episode)
12. **SettingsScreen**: Account settings and logout - IMPLEMENTED

### Reusable Components
- `FocusableCard`: TV-optimized card with focus handling
- `ErrorState`: Consistent error display with retry
- `LoadingState`: Loading indicator with message
- `EmptyState`: Empty content display

### TV Navigation
- D-pad navigation support
- Focus management with FocusRequester
- Back button handling
- Predictable focus flow
- Large touch targets for remote control

## Configuration

### Build Configuration
API base URL is configured via `gradle.properties`:

```properties
# Development
TAVUNO_API_BASE_URL=http://10.0.2.2:8000

# Production
TAVUNO_API_BASE_URL=https://api.tavuno.com
```

### Environment-Specific Builds
Create `gradle.properties` files for different environments:
- `gradle.properties` (default)
- `gradle.properties.dev` (development)
- `gradle.properties.prod` (production)

## Local Development

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34
- Running Tavuno backend (http://localhost:8000)

### Setup Instructions
1. Clone the repository
2. Open `android/` directory in Android Studio
3. Sync Gradle dependencies
4. Configure `gradle.properties` with your API base URL
5. Run the app on Android TV emulator or device

### Android TV Emulator Setup
1. In Android Studio, create a new device
2. Select TV category (Android 13 or later)
3. Choose a TV emulator (e.g., Google TV)
4. Configure remote control settings

### Testing with Backend
1. Start Tavuno backend: `cd tavuno-infra && docker-compose up -d`
2. Configure Android app with `http://10.0.2.2:8000` (emulator localhost)
3. For physical device on same network: use computer's IP address

## Build Instructions

### Debug Build
```bash
cd android
./gradlew assembleDebug
```

### Release Build
```bash
cd android
./gradlew assembleRelease
```

### Run Tests
```bash
cd android
./gradlew test
```

### Run Lint
```bash
cd android
./gradlew lint
```

### Install Debug APK
```bash
cd android
./gradlew installDebug
```

## Key Features

### Authentication
- Secure token storage with DataStore
- Automatic token refresh
- Device fingerprinting for session management
- Logout and session cleanup

### Live TV
- Channel listing with category filtering
- EPG integration (now/next programs)
- Channel logos and metadata
- Fast channel selection

### Sports
- Live matches with real-time scores
- Upcoming events listing
- Competition filtering
- Team information display

### VOD (Movies/Series)
- Grid-based content browsing
- Detailed content information
- Season/episode navigation for series
- Poster and backdrop artwork
- Full playback authorization for movies and episodes
- Stream URL integration with Dispatcharr

### Playback
- Media3/ExoPlayer integration
- HLS stream support
- Playback session management
- Heartbeat for session extension
- Error handling and retry
- Support for live, movie, and episode content

### TV Optimization
- D-pad navigation
- Focus management
- Large, readable text
- Predictable focus flow
- Back button support
- Landscape optimization

## Security Considerations

### Token Storage
- Access tokens stored in DataStore (encrypted on Android 10+)
- No sensitive data in logs
- Token refresh handled securely

### Network Security
- HTTPS for production
- Certificate pinning (to be added)
- No hardcoded credentials
- API keys in build configuration only

### Device Authentication
- Unique device fingerprint
- Device registration and management
- Concurrent stream limits
- Device revocation support

## Performance Optimizations

### Startup
- Minimal initialization
- Lazy repository initialization
- Splash screen for perceived performance

### Memory
- Proper lifecycle management
- ExoPlayer lifecycle handling
- Coroutine cancellation
- Image loading optimization

### Network
- Request timeouts (30s)
- Connection pooling
- Logging interceptor for debug only
- Efficient data models

## Known Limitations

### Backend Dependencies
- VOD playback requires Dispatcharr sync to populate stream_url fields
- Episode/movie playback requires stream URLs to be available in database
- EPG now/next display not yet integrated into Live TV screen

### Android TV Specific
- Requires Android 8.0+ (minSdk 26)
- Optimized for landscape mode
- Touch controls not primary focus
- No runtime verification on actual TV device/emulator

### Testing
- Limited unit test coverage (basic model tests)
- No UI automation tests yet
- Manual testing required for TV-specific features
- No integration tests for full user flows

## Future Enhancements

### Planned Features
- VOD playback completion
- Series season/episode navigation
- Enhanced EPG display
- Search functionality
- Content recommendations
- Parental controls
- Multiple language support
- Accessibility improvements

### Technical Improvements
- Room database for caching
- WorkManager for background sync
- Hilt dependency injection
- Advanced error handling
- Analytics integration
- Crash reporting

## Troubleshooting

### Build Issues
- **Gradle sync fails**: Check JDK version (requires 17+)
- **Dependency errors**: Clean build: `./gradlew clean`
- **API configuration**: Verify `gradle.properties` settings

### Runtime Issues
- **Network errors**: Check backend is running and API URL is correct
- **Authentication failures**: Verify user account and subscription status
- **Playback errors**: Check subscription entitlements and device limits

### TV-Specific Issues
- **Focus problems**: Ensure focus management is properly implemented
- **Remote not working**: Check Android TV compatibility and remote pairing
- **Performance issues**: Reduce image sizes, enable ProGuard for release

## Contributing

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable names
- Add comments for complex logic
- Keep functions focused and small

### Testing
- Add unit tests for new repositories
- Test on real Android TV device when possible
- Verify D-pad navigation flow
- Check focus behavior

### Git Workflow
- Create feature branches
- Make small, logical commits
- Write descriptive commit messages
- Test thoroughly before merging

## Support

For issues or questions:
1. Check existing documentation
2. Review Tavuno backend API contract
3. Check backend status and logs
4. Verify network connectivity
5. Report issues with detailed reproduction steps

## License
This project is part of the Tavuno TV platform. See main project license for details.
