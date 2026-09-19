# M6 — Streaming Foundation

## Status

COMPLETE — OvenMediaEngine media server integration and routing configured.

## Purpose

Enables reliable, authorized live media streaming from source to player using OvenMediaEngine (OME) for Low-Latency HLS (LL-HLS) and standard HLS.

## Architectural Pipeline

```
Authorized IPTV Source
         ↓
    Dispatcharr
         ↓
  OvenMediaEngine
         ↓ (LL-HLS / HLS)
       Caddy
         ↓ (/media/*)
   Media3 Player
```

## Implementation Details

1. **`app/ome_client.py`**:
   - Probes OME REST API status (`GET /v1/vhosts`).
   - Builds signed playback URLs pointing to Caddy's `/media` proxy (`{base_url}/app/{stream_name}/playlist.m3u8?token={token}`).
2. **Infrastructure**:
   - `tavuno-infra/config/caddy/Caddyfile`: Reverse proxies `/media/*` to `ome:3333`.
   - `docker-compose.yml`: Wires `tavuno-control` to depend on `ome` with configurable `OME_API_URL` and `OME_PLAYBACK_BASE_URL`.
3. **Endpoints**:
   - `GET /v1/ome/health` — Probes OvenMediaEngine health and availability.

## Validation

- [x] OME client generates valid HLS / LL-HLS playlist paths.
- [x] Caddy reverse-proxy configuration handles `/media` routing.
- [x] Stream pass-through principle preserved to prevent unnecessary transcoding overhead.
