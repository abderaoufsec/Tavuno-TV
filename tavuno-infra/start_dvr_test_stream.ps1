# Starts the local DVR test stream for instrumented DVR tests (android DvrRewindTest).
#
# DvrRewindTest tunes the "M10 Test Channel" (channel 8814), the one catalog channel whose
# playback authorization resolves to OME/DVR mode (dvr_enabled=true, max_rewind_seconds=3600).
# That mode only serves a real LL-HLS stream when something is publishing to OME, which nothing
# does on a dormant dev stack. This script publishes the repo's test clip on a loop:
#
#   ffmpeg -> rtmp://localhost:1935/tavuno/channel_8814 -> OME -> LL-HLS :3333 (direct)
#
# Playback authorization serves direct OME URLs (OME_PLAYBACK_BASE_URL=http://localhost:3333,
# see tavuno-infra/.env): Caddy's :80 listener 301s to https://localhost:8443 with an internal
# CA, which an emulator cannot follow (hardcoded host + untrusted cert). Direct :3333 is plain
# HTTP and reaches the device as http://10.0.2.2:3333 after the loopback rewrite.
#
# Run it once per test session (the push keeps running in the background until ffmpeg or the
# OME container stops), then run `connectedDebugAndroidTest`:
#
#   powershell -File tavuno-infra\start_dvr_test_stream.ps1
#
# Prerequisites: ffmpeg on PATH, tavuno-infra/test_video.mp4, the tavuno stack up.

param(
    # Must match a catalog channel that authorizes into OME/DVR mode (see playback.py).
    [int]$ChannelId = 8814,
    [string]$VideoFile = (Join-Path $PSScriptRoot 'test_video.mp4'),
    [string]$StreamName = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$API = 'http://localhost:8000'

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    Write-Error 'ffmpeg not found on PATH (e.g. choco install ffmpeg).'
}
if (-not (Test-Path $VideoFile)) {
    Write-Error "Test clip not found: $VideoFile"
}

# Where the stream must be published comes from the same authorization the app gets.
$auth = Invoke-RestMethod -Method Post -Uri "$API/v1/playback/live/$ChannelId" `
    -ContentType 'application/json' -Body '{}'
if (-not $auth.playback.dvr_enabled) {
    Write-Error "Channel $ChannelId did not authorize into DVR mode - DvrRewindTest's target is wrong."
}
if (-not $StreamName) { $StreamName = $auth.playback.stream_name }
if (-not $StreamName) { Write-Error 'Authorization returned no stream_name.' }

# Already publishing? OME lists its streams through the API (Basic token, see ome_client.py);
# the response is a plain array of stream-name strings.
$omeToken = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('tavuno-m1-local'))
try {
    $streams = Invoke-RestMethod -Uri 'http://localhost:8081/v1/vhosts/default/apps/tavuno/streams' `
        -Headers @{ Authorization = "Basic $omeToken" }
    $live = @($streams.response) -contains $StreamName
} catch { $live = $false }

if ($live -and -not $Force) {
    Write-Host "Stream '$StreamName' already publishing on OME - nothing to do (use -Force to restart)."
} else {
    $fflog = Join-Path $env:TEMP "tavuno-dvr-stream-$StreamName.log"
    if ($live -and $Force) {
        # Only one publisher can hold the RTMP key; drop it before re-pushing.
        try {
            Invoke-RestMethod -Method Delete `
                -Uri "http://localhost:8081/v1/vhosts/default/apps/tavuno/streams/$StreamName" `
                -Headers @{ Authorization = "Basic $omeToken" } | Out-Null
        } catch { }
    }
    $ffArgs = @(
        '-loglevel', 'warning',
        '-re', '-stream_loop', '-1',
        '-i', $VideoFile,
        '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'zerolatency',
        '-g', '30', '-keyint_min', '30', '-sc_threshold', '0',
        '-pix_fmt', 'yuv420p', '-an',
        '-f', 'flv', "rtmp://localhost:1935/tavuno/$StreamName"
    )
    $proc = Start-Process -FilePath 'ffmpeg' -ArgumentList $ffArgs -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $fflog -RedirectStandardError "$fflog.err"
    Write-Host "ffmpeg started (pid $($proc.Id)) -> rtmp://localhost:1935/tavuno/$StreamName (log: $fflog.err)"
}

# Wait until the LL-HLS playlist is actually served (direct OME; no token needed: OME does not
# validate it - Caddy does not enforce it either, see its Caddyfile note).
$manifest = "http://localhost:3333/tavuno/$StreamName/llhls.m3u8"
$deadline = (Get-Date).AddSeconds(60)
$ok = $false
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-WebRequest -Uri $manifest -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200 -and $r.Content -match '#EXTM3U') { $ok = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}
if (-not $ok) {
    Write-Error "LL-HLS manifest did not come up at $manifest - check the ffmpeg log and OME container."
}

Write-Host "DVR test stream ready: $manifest"
Write-Host "Let it run ~60s before connectedDebugAndroidTest so the DVR window covers a -30s rewind."
