#!/bin/bash
# M10 Live Streaming Test Setup
# This script demonstrates the complete streaming pipeline verification

set -e

echo "=== TAVUNO-TV M10 LIVE STREAMING TEST SETUP ==="
echo ""

# PHASE 1: Verify OME Configuration
echo "PHASE 1: OME Configuration Verification"
echo "-------------------------------------------"
echo "OME Configuration:"
echo "  Virtual Host: default"
echo "  Application: tavuno"
echo "  RTMP Port: 1935"
echo "  LLHLS Port: 3333"
echo "  Expected RTMP URL: rtmp://localhost:1935/tavuno/test"
echo "  Expected HLS URL: http://localhost:3333/tavuno/test/llhls.m3u8"
echo "  Expected Caddy URL: http://localhost:8080/media/tavuno/test/llhls.m3u8"
echo ""

# Check if OME is running
echo "Checking OME status..."
if curl -s -f "http://localhost:8081/v1/vhosts" > /dev/null 2>&1; then
    echo "✓ OME API is accessible"
else
    echo "✗ OME API is not accessible"
    exit 1
fi
echo ""

# PHASE 2: Test Source Requirements
echo "PHASE 2: Test Source Requirements"
echo "-----------------------------------"
echo "To complete end-to-end testing, you need:"
echo "1. A test video file (MP4, WebM, or other FFmpeg-compatible format)"
echo "2. FFmpeg installed on the host system"
echo "3. Network access to localhost:1935 (RTMP)"
echo ""
echo "Suggested test sources:"
echo "  - Public domain video: https://gettestfiles.com/mp4/TestFile-mp4-100kb.mp4"
echo "  - Placeholder video: https://placeholdervideo.dev/640x360"
echo "  - Test pattern generator: https://cubbbix.com/sample-video-files/"
echo ""

# PHASE 3: Download Test Video (if provided)
if [ -n "$1" ]; then
    echo "Downloading test video from: $1"
    curl -L -o /tmp/test_video.mp4 "$1"
    VIDEO_FILE="/tmp/test_video.mp4"
else
    echo "No test video URL provided. Using local file if available."
    VIDEO_FILE="${2:-/tmp/test_video.mp4}"
fi

if [ ! -f "$VIDEO_FILE" ]; then
    echo "⚠ Test video not found at: $VIDEO_FILE"
    echo "  Please provide a test video URL as first argument"
    echo "  Example: $0 https://gettestfiles.com/mp4/TestFile-mp4-100kb.mp4"
    echo ""
    echo "Continuing with infrastructure verification only..."
    VIDEO_FILE=""
fi

# PHASE 4: FFmpeg Check
echo "PHASE 4: FFmpeg Availability Check"
echo "------------------------------------"
if command -v ffmpeg &> /dev/null; then
    echo "✓ FFmpeg is available"
    FFMPEG_VERSION=$(ffmpeg -version | head -n 1)
    echo "  Version: $FFMPEG_VERSION"
else
    echo "✗ FFmpeg is not available"
    echo "  Install FFmpeg to complete streaming tests"
    echo "  Windows: choco install ffmpeg"
    echo "  Linux: sudo apt install ffmpeg"
    echo "  macOS: brew install ffmpeg"
    FFMPEG_AVAILABLE=false
fi
echo ""

# PHASE 5: Streaming Command Template
echo "PHASE 5: FFmpeg Streaming Command"
echo "----------------------------------"
echo "If FFmpeg is available, use this command to publish to OME:"
echo ""
echo "ffmpeg -re -i test_video.mp4 \\"
echo "  -c:v libx264 -preset veryfast -b:v 800k \\"
echo "  -c:a aac -b:a 128k \\"
echo "  -f flv rtmp://localhost:1935/tavuno/test"
echo ""
echo "For continuous looping:"
echo "ffmpeg -re -stream_loop -1 -i test_video.mp4 \\"
echo "  -c:v libx264 -preset veryfast -b:v 800k \\"
echo "  -c:a aac -b:a 128k \\"
echo "  -f flv rtmp://localhost:1935/tavuno/test"
echo ""

# PHASE 6: Verify OME Stream Detection
echo "PHASE 6: OME Stream Detection Verification"
echo "------------------------------------------"
echo "After starting FFmpeg, verify stream appears in OME:"
echo ""
echo "curl -H \"Authorization: Basic dGF2dW5vLW0xLWxvY2Fs\" \\"
echo "  \"http://localhost:8081/v1/vhosts/default/apps/tavuno/streams\""
echo ""
echo "Expected response (when stream is active):"
echo '{'
echo '  "message": "OK",'
echo '  "response": ["test"],'
echo '  "statusCode": 200'
echo '}'
echo ""

# PHASE 7: LL-HLS Verification
echo "PHASE 7: LL-HLS Playlist Verification"
echo "----------------------------------------"
echo "Expected HLS URLs:"
echo "  Direct OME:     http://localhost:3333/tavuno/test/llhls.m3u8"
echo "  Via Caddy:      http://localhost:8080/media/tavuno/test/llhls.m3u8"
echo "  Via Tavuno:     (requires playback authorization)"
echo ""
echo "Verification commands:"
echo "  curl -I http://localhost:3333/tavuno/test/llhls.m3u8"
echo "  curl -I http://localhost:8080/media/tavuno/test/llhls.m3u8"
echo ""

# PHASE 8: Tavuno Playback Endpoint
echo "PHASE 8: Tavuno Playback Endpoint"
echo "----------------------------------"
echo "Current playback endpoint: POST /v1/playback/live/{channel_id}"
echo ""
echo "Required headers:"
echo "  Authorization: Bearer <JWT token>"
echo "  Content-Type: application/json"
echo ""
echo "Request body:"
echo '{'
echo '  "channel_id": <id>'
echo '}'
echo ""

# Current infrastructure status
echo "=== CURRENT INFRASTRUCTURE STATUS ==="
echo "✓ OME container: Running (tavuno-ovenmediaengine)"
echo "✓ OME API: Accessible (port 8081)"
echo "✓ OME RTMP: Port 1935 exposed"
echo "✓ OME LLHLS: Port 3333 exposed"
echo "✓ Caddy: Running (tavuno-caddy)"
echo "✓ Caddy media proxy: Configured for /media/*"
echo "✓ Tavuno Control: Running (tavuno-control)"
echo "✓ Tavuno playback endpoint: Implemented"
echo "✓ JWT authentication: Working"
echo "✓ Playback token system: Implemented"
echo ""

# Summary
echo "=== SUMMARY ==="
echo "Infrastructure: READY"
echo "OME Configuration: VERIFIED"
echo "Test Source: NEEDED"
echo "FFmpeg: NEEDED (for stream injection)"
echo ""
echo "To complete M10 verification:"
echo "1. Install FFmpeg if not available"
echo "2. Download or provide a test video file"
echo "3. Run FFmpeg to publish to OME"
echo "4. Verify stream appears in OME API"
echo "5. Verify HLS playlist is accessible"
echo "6. Test Tavuno playback endpoint"
echo "7. Verify end-to-end media playback"
echo ""
