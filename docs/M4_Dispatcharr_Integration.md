# M4 — Dispatcharr Integration

## Status

COMPLETE — read-only Dispatcharr 0.27.2 connector with pagination, URL redaction, VOD import, stream-id mapping, and a periodic sync job.

## Purpose

Connects Tavuno Control to Dispatcharr IPTV middleware without duplicating IPTV source logic or scraping feeds in Tavuno.

## Architectural Boundaries

- **Source of Truth**: Dispatcharr manages IPTV providers, M3U/Xtream feeds, stream monitoring, and source failover.
- **Tavuno Canonical IDs**: Tavuno tables keep their own primary keys. Dispatcharr channel and stream IDs are stored as `external_id` values in `tavuno_channel_sources`. VOD items use stable slugs `darr-movie-{id}` and `darr-series-{id}`.
- **Zero Credential Leakage**: Raw provider stream URLs, M3U credentials, and Dispatcharr tokens are stripped at the client boundary and are never written to Tavuno catalog tables or returned by client APIs.

## Implementation Details

1. **`app/dispatcharr_client.py`**
   - `get_version()` probes `/api/core/version/`.
   - Channel groups, channels, streams, EPG, VOD categories, movies, series, and episodes.
   - Pagination follows `next` until it is null.
   - Sensitive fields (`url`, `local_file`, credentials) are redacted before mapping.
2. **`app/sync_service.py`**
   - Live groups → `tavuno_categories` (`kind=live`).
   - VOD categories → `tavuno_categories` (`kind=movie` or `series`).
   - Channels → `tavuno_channels` plus `provider=dispatcharr` source rows.
   - Stream records → `provider=dispatcharr-stream` source rows (IDs only).
   - EPG programmes → `tavuno_epg_programmes`.
   - Movies / series / episodes → Tavuno VOD tables.
3. **Job**
   - Manual: `POST /v1/admin/sync/dispatcharr`
   - Status: `GET /v1/admin/sync/dispatcharr`
   - Periodic: `DISPATCHARR_SYNC_INTERVAL_SECONDS` (default 3600). Disabled when the interval is `0` or `DISPATCHARR_API_KEY` is empty.
4. **Health**
   - `GET /v1/dispatcharr/health` reports the live version against `0.27.2`.

## Configuration

Set `DISPATCHARR_API_KEY` in `tavuno-infra/.env` for an active, least-privileged Dispatcharr user. Do not commit the key.

## Validation

- [x] Version probe against Dispatcharr 0.27.2.
- [x] Pagination traversal with next-page detection.
- [x] Stream URL redaction at the client boundary.
- [x] Channel, stream, EPG, and VOD mapping with Tavuno-owned IDs.
- [x] Unit tests cover the client and sync mapping.
