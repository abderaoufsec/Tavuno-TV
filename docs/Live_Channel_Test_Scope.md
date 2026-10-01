# Live Channel Test Scope

While the app is being tested, the catalog is restricted to a small set of
channels whose upstream streams were **verified as actually playing**. Once the
app is confirmed working, the scope is lifted and every synced channel returns.

The scope is a **read-time filter** — it never deletes or deactivates synced
data, so lifting it is a config change, not a re-sync.

## How it works

Two settings on `tavuno-control` (see `app/config.py`):

| Setting | Meaning | Production value |
| --- | --- | --- |
| `TAVUNO_LIVE_CHANNEL_ALLOWLIST` | Comma-separated channel **ids** and/or **slugs** to expose. Empty = no allowlist. | *(empty)* |
| `TAVUNO_LIVE_CHANNEL_LIMIT` | Maximum live channels listed. `0` = unlimited. Also caps the Home rail. | `0` |

They are applied to `CatalogService.get_channels()` and `CatalogService.get_home()`,
i.e. `GET /v1/channels` and `GET /v1/home`.

Playback (`POST /v1/playback/live/{id}`) is intentionally **not** filtered: a
directly-addressed channel id still plays, which keeps EPG/deep links working.

## Restrict to working channels

```bash
cd tavuno-control
python scripts/find_working_channels.py            # find 10 and write ../tavuno-infra/.env
python scripts/find_working_channels.py --limit 25
python scripts/find_working_channels.py --no-write # just report
docker compose -f ../tavuno-infra/docker-compose.yml up -d tavuno-control
```

### Why "working" is trustworthy

The playback endpoint probes each channel's Dispatcharr stream URL before
answering. A playable upstream is returned as `protocol: "http_hls"`; anything
unreachable silently falls back to the OME relay (`protocol: "hls"`). The script
therefore treats **`http_hls` as proof the stream plays right now**.

## Restore the full catalog

```bash
python scripts/find_working_channels.py --clear
docker compose -f ../tavuno-infra/docker-compose.yml up -d tavuno-control
```

Or set `TAVUNO_LIVE_CHANNEL_ALLOWLIST=` (empty) and `TAVUNO_LIVE_CHANNEL_LIMIT=0`
in `tavuno-infra/.env` and restart `tavuno-control`.

## Tests

`tests/test_catalog_service.py::TestLiveChannelTestScope` covers the allowlist
parsing, the limit, the combined category+scope query, and the "no scope"
fall-through.

## Free-launch (guest) playback

`AUTH_OPEN_ACCESS=true` keeps `tavuno-control` answering catalog and playback
without a login; the backend resolves the seeded `guest@tavuno.local` identity.
The Android client sends **no** `Authorization` header in that mode (see
`PlaybackRepository.authorizationHeader()`), which is what fixed the
`No access token available` crash when opening a stream.