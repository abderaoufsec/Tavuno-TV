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

### Pointing the emulator at the backend

The scope is enforced **server-side**, so the Android client needs no change to
benefit from it — but the emulator cannot reach the host as `localhost`. Tavuno TV
already defaults to `http://10.0.2.2:8000` (`TAVUNO_API_BASE_URL` in
`android/gradle.properties`, surfaced as `BuildConfig.TAVUNO_API_BASE_URL`), and
`10.0.2.2` is the emulator’s alias for the host loopback. Playback URLs get the
same rewrite (`PlaybackUrls`), so a stream URL the backend built with `localhost`
still plays on the emulator.

If `/v1/channels` looks empty on the emulator while `curl
http://localhost:8000/v1/channels` answers, check the app’s base URL *before* the
scope: an app pointed at the device’s own loopback never reaches `tavuno-control`.

### The current working scope

`find_working_channels.py` writes into `tavuno-infra/.env`, which is gitignored — so the
*working* set (as opposed to the template above) is not in the repository. As of 2026-10-02 the
running dev stack is scoped to these 10 verified channel ids, with `TAVUNO_LIVE_CHANNEL_LIMIT=10`:

```
8845,6860,6861,6862,6863,8844,6869,6870,6871,6874
```

Reproduce it either by re-running the script (it re-probes upstreams, so the set may differ if
streams have since gone away) or by pasting the ids above into `tavuno-infra/.env` and
restarting `tavuno-control`. Clearing the scope restores the full 2036-channel catalog.

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