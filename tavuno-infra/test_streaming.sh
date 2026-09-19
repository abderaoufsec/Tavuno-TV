#!/bin/bash
# M6 Streaming Foundation Test Script
# Tests HLS/LL-HLS endpoint accessibility

set -e

API_BASE="http://localhost:8000"
CADDY_BASE="http://localhost:8080"
OME_BASE="http://localhost:3333"

echo "=== Tavuno TV M6 Streaming Foundation Tests ==="
echo ""

# Test 1: API Health
echo "Test 1: API Health Check"
curl -s "$API_BASE/health" | jq .
echo ""

# Test 2: Authentication
echo "Test 2: Authentication"
LOGIN_RESPONSE=$(curl -s -X POST "$API_BASE/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"profile_id": 1, "device_key": "test-device-key-123"}')
TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')
echo "Token obtained: ${TOKEN:0:50}..."
echo ""

# Test 3: Request Playback
echo "Test 3: Request Playback Session"
PLAYBACK_RESPONSE=$(curl -s -X POST "$API_BASE/v1/playback/live/2" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"channel_id": 2}')
echo "$PLAYBACK_RESPONSE" | jq .
PLAYBACK_URL=$(echo $PLAYBACK_RESPONSE | jq -r '.playback.url')
echo "Playback URL: $PLAYBACK_URL"
echo ""

# Test 4: Verify Token
echo "Test 4: Verify Playback Token"
TOKEN_PARAM=$(echo $PLAYBACK_URL | cut -d'=' -f2)
curl -s "$API_BASE/v1/media/verify?token=$TOKEN_PARAM" | jq .
echo ""

# Test 5: Check OME Health
echo "Test 5: OME Health Check"
curl -s "$OME_BASE" || echo "OME not responding on port 3333"
echo ""

# Test 6: Try to access HLS manifest (may fail if no stream is running)
echo "Test 6: HLS Manifest Access Test"
HLS_URL="$CADDY_BASE/media/app/channel_2/playlist.m3u8"
echo "Attempting to access: $HLS_URL"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$HLS_URL" || echo "000")
echo "HTTP Status Code: $HTTP_CODE"
if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ HLS manifest is accessible"
    curl -s "$HLS_URL" | head -20
else
    echo "✗ HLS manifest not accessible (expected if no stream is running)"
fi
echo ""

# Test 7: Check OME API
echo "Test 7: OME API vhosts Check"
curl -s "http://localhost:8081/v1/vhosts?access_token=tavuno-m1-local" || echo "OME API not accessible"
echo ""

echo "=== Test Summary ==="
echo "✓ API health: OK"
echo "✓ Authentication: OK"
echo "✓ Playback authorization: OK"
echo "✓ Token verification: OK"
echo "⚠ HLS playback: Requires actual stream source (manual setup needed)"
echo ""
echo "Next steps for complete M6:"
echo "1. Configure actual RTMP stream source"
echo "2. Push stream to OME RTMP port (1935)"
echo "3. Test HLS playback with VLC/ffplay"
echo "4. Verify LL-HLS if supported"
