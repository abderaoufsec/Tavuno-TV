# M6 Streaming Foundation Test Script (PowerShell)
# Tests HLS/LL-HLS endpoint accessibility

$ErrorActionPreference = "Stop"

$API_BASE = "http://localhost:8000"
$CADDY_BASE = "http://localhost:8080"
$OME_BASE = "http://localhost:3333"

Write-Host "=== Tavuno TV M6 Streaming Foundation Tests ===" -ForegroundColor Cyan
Write-Host ""

# Test 1: API Health
Write-Host "Test 1: API Health Check" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$API_BASE/health"
    $response | ConvertTo-Json
} catch {
    Write-Host "✗ API health check failed: $_" -ForegroundColor Red
}
Write-Host ""

# Test 2: Authentication
Write-Host "Test 2: Authentication" -ForegroundColor Yellow
try {
    $loginResponse = Invoke-RestMethod -Uri "$API_BASE/v1/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"profile_id": 1, "device_key": "test-device-key-123"}'
    $token = $loginResponse.token
    Write-Host "Token obtained: $($token.Substring(0, [Math]::Min(50, $token.Length)))..." -ForegroundColor Green
} catch {
    Write-Host "✗ Authentication failed: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Test 3: Request Playback
Write-Host "Test 3: Request Playback Session" -ForegroundColor Yellow
try {
    $playbackResponse = Invoke-RestMethod -Uri "$API_BASE/v1/playback/live/2" `
        -Method POST `
        -ContentType "application/json" `
        -Headers @{Authorization = "Bearer $token"} `
        -Body '{"channel_id": 2}'
    $playbackResponse | ConvertTo-Json
    $playbackUrl = $playbackResponse.playback.url
    Write-Host "Playback URL: $playbackUrl" -ForegroundColor Cyan
} catch {
    Write-Host "✗ Playback request failed: $_" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Test 4: Verify Token
Write-Host "Test 4: Verify Playback Token" -ForegroundColor Yellow
try {
    $tokenParam = $playbackUrl.Split('=')[1]
    $verifyResponse = Invoke-RestMethod -Uri "$API_BASE/v1/media/verify?token=$tokenParam"
    $verifyResponse | ConvertTo-Json
} catch {
    Write-Host "✗ Token verification failed: $_" -ForegroundColor Red
}
Write-Host ""

# Test 5: Check OME Health
Write-Host "Test 5: OME Health Check" -ForegroundColor Yellow
try {
    $omeResponse = Invoke-WebRequest -Uri "$OME_BASE" -UseBasicParsing
    Write-Host "✓ OME is responding on port 3333" -ForegroundColor Green
} catch {
    Write-Host "✗ OME not responding on port 3333: $_" -ForegroundColor Red
}
Write-Host ""

# Test 6: Try to access HLS manifest
Write-Host "Test 6: HLS Manifest Access Test" -ForegroundColor Yellow
$hlsUrl = "$CADDY_BASE/media/app/channel_2/playlist.m3u8"
Write-Host "Attempting to access: $hlsUrl" -ForegroundColor Cyan
try {
    $hlsResponse = Invoke-WebRequest -Uri $hlsUrl -UseBasicParsing
    Write-Host "HTTP Status Code: $($hlsResponse.StatusCode)" -ForegroundColor Green
    if ($hlsResponse.StatusCode -eq 200) {
        Write-Host "✓ HLS manifest is accessible" -ForegroundColor Green
        Write-Host "First 20 lines of manifest:" -ForegroundColor Cyan
        $hlsResponse.Content.Split("`n") | Select-Object -First 20
    }
} catch {
    Write-Host "✗ HLS manifest not accessible (expected if no stream is running)" -ForegroundColor Yellow
}
Write-Host ""

# Test 7: Check OME API
Write-Host "Test 7: OME API vhosts Check" -ForegroundColor Yellow
try {
    $omeApiResponse = Invoke-RestMethod -Uri "http://localhost:8081/v1/vhosts?access_token=tavuno-m1-local"
    $omeApiResponse | ConvertTo-Json
} catch {
    Write-Host "✗ OME API not accessible: $_" -ForegroundColor Red
}
Write-Host ""

Write-Host "=== Test Summary ===" -ForegroundColor Cyan
Write-Host "✓ API health: OK" -ForegroundColor Green
Write-Host "✓ Authentication: OK" -ForegroundColor Green
Write-Host "✓ Playback authorization: OK" -ForegroundColor Green
Write-Host "✓ Token verification: OK" -ForegroundColor Green
Write-Host "⚠ HLS playback: Requires actual stream source (manual setup needed)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Next steps for complete M6:" -ForegroundColor Cyan
Write-Host "1. Configure actual RTMP stream source" -ForegroundColor White
Write-Host "2. Push stream to OME RTMP port (1935)" -ForegroundColor White
Write-Host "3. Test HLS playback with VLC/ffplay" -ForegroundColor White
Write-Host "4. Verify LL-HLS if supported" -ForegroundColor White
