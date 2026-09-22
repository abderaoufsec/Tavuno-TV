# Tavuno TV Android Client

Official Android TV client for the Tavuno TV streaming platform.

## Features

- **Live TV**: Channel browsing with category filtering and EPG support
- **Sports**: Live and upcoming sports events with real-time scores
- **Movies**: On-demand movie catalog with detailed information
- **Series**: TV series with season and episode navigation
- **Playback**: HLS streaming with Media3/ExoPlayer
- **Authentication**: Secure JWT-based authentication with auto-refresh
- **TV Optimized**: D-pad navigation, focus management, and remote control support

## Requirements

- Android 5.0 (API 21) or higher
- Android TV or Google TV device
- Active Tavuno subscription
- Internet connection

## Installation

### From Source
```bash
git clone https://github.com/abderaoufsec/Tavuno-TV.git
cd Tavuno-TV/android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### From Google Play (Coming Soon)
The app will be available on Google Play for Android TV devices.

## Configuration

The app requires configuration of the Tavuno API base URL. This is set in `gradle.properties`:

```properties
TAVUNO_API_BASE_URL=http://your-api-url.com
```

For development with the local backend:
```properties
TAVUNO_API_BASE_URL=http://10.0.2.2:8000
```

## Development

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34
- Running Tavuno backend

### Build
```bash
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build
```

### Test
```bash
./gradlew test            # Unit tests
./gradlew connectedAndroidTest  # UI tests
```

### Run
```bash
./gradlew installDebug    # Install on connected device
```

## Architecture

The app follows a clean architecture with:
- **MVVM pattern** with Repository pattern
- **Jetpack Compose** for TV UI
- **Retrofit** for API communication
- **DataStore** for local storage
- **Media3/ExoPlayer** for video playback

See [docs/android-client.md](../docs/android-client.md) for detailed documentation.

## Screens

1. **Splash**: App launch and session validation
2. **Login**: User authentication
3. **Home**: Main navigation hub
4. **Live TV**: Channel listing
5. **Sports**: Sports events
6. **Movies**: Movie catalog
7. **Series**: Series catalog
8. **Player**: Video playback
9. **Settings**: Account management

## User Flow

```
Launch → Splash → (Authenticated?) → Home → Content → Player
                              ↓ (No)
                            Login → Home → Content → Player
```

## Remote Control

The app is optimized for TV remote control:
- **D-pad**: Navigate between items
- **OK/Enter**: Select focused item
- **Back**: Go back or exit
- **Menu**: Open settings (where available)

## Authentication

The app uses Tavuno's authentication system:
- Email/password login
- JWT token-based authentication
- Automatic token refresh
- Device registration and management
- Subscription validation

## Playback

The app supports:
- HLS live streaming
- VOD playback (movies/series)
- Playback session management
- Concurrent stream limits
- Subscription-based access control

## Performance

- Fast startup with splash screen
- Lazy loading of content
- Efficient image loading with Coil
- Proper lifecycle management
- Memory-optimized video playback

## Security

- Secure token storage with DataStore
- HTTPS for production API calls
- No hardcoded credentials
- Device fingerprinting
- Session timeout and refresh

## Troubleshooting

### Connection Issues
- Verify backend is running
- Check API URL configuration
- Ensure network connectivity
- Check subscription status

### Playback Issues
- Verify subscription entitlements
- Check device limits
- Ensure adequate internet bandwidth
- Try restarting the app

### Remote Control Issues
- Ensure proper TV device pairing
- Check focus management
- Verify D-pad navigation
- Test with different remote

## Contributing

See [docs/android-client.md](../docs/android-client.md) for contribution guidelines.

## License

This project is part of the Tavuno TV platform. See main project license for details.

## Support

For support, visit the main Tavuno TV project repository or contact the development team.
