# M6 Streaming Foundation - Setup Guide

## What Has Been Configured (Automated)

### ✅ OME (OvenMediaEngine) Configuration
- **File**: `tavuno-infra/docker/ome/conf/Server.xml`
- **Current status**: OME is running successfully
- **Configuration**:
  - LL-HLS enabled
  - HTTP2 enabled
  - API access on port 8081
  - RTMP port 1935 available
  - HLS/LL-HLS port 3333 available
- **Note**: Application/stream configuration is done via OME API, not XML

### ✅ Caddy Reverse Proxy Configuration
- **File**: `tavuno-infra/config/caddy/Caddyfile`
- **Changes made**:
  - Configured `/media/*` path to proxy to OME on port 3333
  - Media streaming accessible via `http://localhost:8080/media/...`
  - Token verification endpoint available at `/v1/media/verify`

### ✅ Test Scripts Created
- **Linux/Mac**: `tavuno-infra/test_streaming.sh`
- **Windows**: `tavuno-infra/test_streaming.ps1`
- These scripts test endpoint accessibility and configuration

## What You Need to Do Manually

### Step 1: Verify OME is Running

```bash
cd tavuno-infra
docker compose ps ovenmediaengine
```

Should show "Up" status.

### Step 2: Run Test Script

**Windows PowerShell:**
```powershell
cd tavuno-infra
.\test_streaming.ps1
```

**Linux/Mac:**
```bash
cd tavuno-infra
chmod +x test_streaming.sh
./test_streaming.sh
```

Expected results:
- ✅ API health: OK
- ✅ Authentication: OK
- ✅ Playback authorization: OK
- ✅ Token verification: OK
- ✅ OME health: OK
- ⚠ HLS playback: Will fail (no stream running yet)

### Step 3: Configure OME Application via API

Since OME v0.21.0 uses API-based configuration, you need to create the application:

```bash
# Create application named "app"
curl -X POST "http://localhost:8081/v1/vhosts/default/apps" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "app",
    "type": "live",
    "outputProfiles": [
      {
        "name": "bypass",
        "containerName": "mp4",
        "streamName": "${StreamName}"
      }
    ]
  }' \
  --data-urlencode "access_token=tavuno-m1-local"
```

### Step 4: Push a Test Stream to OME

You need to push an RTMP stream to OME. Options:

#### Option A: Use FFmpeg with a Test Video

If you have a video file:

```bash
ffmpeg -re -i your_video.mp4 \
  -c:v libx264 -preset veryfast -b:v 3000k \
  -c:a aac -b:a 128k \
  -f flv rtmp://localhost:1935/app/channel_2
```

#### Option B: Use a Public Test Stream

Push a public stream to OME:

```bash
ffmpeg -re -i "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" \
  -c:v libx264 -preset veryfast -b:v 3000k \
  -c:a aac -b:a 128k \
  -f flv rtmp://localhost:1935/app/channel_2
```

#### Option C: Use OBS Studio

1. Download and install OBS Studio
2. Settings → Stream
3. Service: Custom
4. Server: `localhost`
5. Stream Key: `channel_2`
6. Start streaming

### Step 5: Test HLS Playback

Once a stream is pushing to OME:

**Access HLS manifest:**
```bash
curl http://localhost:8080/media/app/channel_2/playlist.m3u8
```

**Play with VLC:**
1. Open VLC
2. Media → Open Network Stream
3. Enter: `http://localhost:8080/media/app/channel_2/playlist.m3u8`
4. Click Play

**Play with ffplay:**
```bash
ffplay http://localhost:8080/media/app/channel_2/playlist.m3u8
```

### Step 6: Test with Tavuno Authorization

**Get playback URL with token:**

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8000/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"profile_id": 1, "device_key": "test-device-key-123"}' \
  | jq -r '.token')

# Get playback URL
PLAYBACK_URL=$(curl -s -X POST http://localhost:8000/v1/playback/live/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"channel_id": 2}' \
  | jq -r '.playback.url')

echo $PLAYBACK_URL
```

**Play with token:**
```bash
ffplay "$PLAYBACK_URL"
```

## Troubleshooting

### OME not starting
- Check OME logs: `docker compose logs ovenmediaengine --tail=50`
- Verify Server.xml syntax is correct
- Ensure no XML syntax errors

### OME API returns 401
- Check access token: `tavuno-m1-local`
- Verify API URL: `http://localhost:8081/v1/vhosts?access_token=tavuno-m1-local`

### RTMP connection refused
- Check OME is running: `docker compose ps`
- Check port 1935 is accessible: `telnet localhost 1935`
- Verify application exists in OME

### HLS manifest 404
- Verify stream is actually pushing to OME
- Check OME logs for stream creation
- Verify application name: `app`
- Verify stream name: `channel_2`

### Token verification fails
- Check token is not expired
- Verify session is still active in database
- Check `/v1/media/verify` endpoint is working

## Current M6 Status

### ✅ Completed (Automated)
- OME Server.xml basic configuration
- OME running successfully
- Caddy reverse proxy configuration
- RTMP port 1935 available
- HLS/LL-HLS port 3333 available
- Test scripts for endpoint validation
- Token verification endpoint

### ⚠️ Requires Manual Setup
- Create OME application via API
- Push actual stream source (video file or live stream)
- Stream push to OME RTMP port
- Visual playback testing with VLC/ffplay
- Full token enforcement at media layer (optional)

### 📋 Checklist for Complete M6

- [ ] Verify OME is running
- [ ] Run test script to verify endpoints
- [ ] Create OME application via API
- [ ] Push test stream to OME (RTMP)
- [ ] Access HLS manifest via browser/curl
- [ ] Play video with VLC/ffplay
- [ ] Test with Tavuno authorization token
- [ ] Verify LL-HLS if supported by player
- [ ] Measure latency (optional)
- [ ] Test stream failure/recovery (optional)

## Notes

- OME application configuration is done via API, not XML
- RTMP URL format: `rtmp://localhost:1935/app/channel_2`
- HLS URL format: `http://localhost:8080/media/app/channel_2/playlist.m3u8`
- Token parameter is appended: `?token=...`
- LL-HLS is enabled but depends on player support
- OME API documentation: https://airensoft.gitbook.io/ovenmediaengine/rest-api
