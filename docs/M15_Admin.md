# M15 — Admin Platform

**Status: ✅ COMPLETE**

## Scope decision

Directus remains the CRUD admin surface. This is the recorded strategy, not a
shortcut:

- `docs/00_TavunoTV_Master_Strategy.md`: *"Directus Studio initially, custom
  Tavuno Admin UI later where needed"*
- `docs/03_TavunoComponent_Integration_Map.md`: *"Admin UI — Directus Studio
  first — Avoid building CRUD UI"*

Building channels/EPG/VOD/profiles editors from scratch would duplicate a
platform that is already running, funded by the same effort budget as the
features users actually see. **M15 was therefore incomplete for want of
*visibility*, not editing.**

## What was added — a read-only ops dashboard

Two endpoints on `tavuno-control`, no build step, no frontend framework:

| Route | Auth | Contents |
| --- | --- | --- |
| `GET /v1/ops` | none | HTML shell (contains **no** data and **no** secret) |
| `GET /v1/ops/summary` | `X-Ops-Token` | JSON snapshot |

`app/ops.py` assembles the snapshot from six independent sections:

- **health** — reuses the existing `services.health()` probe
- **sync** — last Dispatcharr sync status, timestamp, version
- **counts** — channels, categories, movies, series, episodes, EPG, profiles
- **playback** — active session count
- **migrations** — ledger state (`applied`, `latest`)
- **scope** — the live-channel test scope

Each section is individually guarded: one missing table or dead dependency
yields a field, never a 500. That matters because the dashboard is most
valuable *during* an outage.

### Why a token and not `require_admin`

Free-launch mode (`AUTH_OPEN_ACCESS=true`, the compose default) resolves the
guest principal with role `user`. Every `require_admin` route therefore answers
403 while the app ships without login — so gating the dashboard on an admin
login would make it unreachable by construction, and rebuilding the login
system is explicitly out of scope for this launch.

`X-Ops-Token` is one shared secret compared with `hmac.compare_digest`
(constant time, so the header cannot be probed byte by byte).

### Why the HTML page is served openly

The page carries no data and no token: it reads the operator's token from
`sessionStorage` and sends it as a header. Serving the shell without auth is
what keeps the secret out of the document, the URL and the access log — the
same reason the page is *not* handed `?token=...`.

### Read-only by construction

`tests/test_ops.py` asserts no `POST`/`PUT`/`PATCH`/`DELETE` route exists
under `/v1/ops`. Editing stays in Directus.

## Configuration

```bash
# tavuno-infra/.env  (or tavuno-control/.env)
TAVUNO_OPS_TOKEN=<change-me>
```

Ships with a working default (`tavuno-ops-local`) so a fresh clone has a usable
dashboard. **Override it in production** — it is a known constant otherwise.
It is listed as a secret to rotate in `docs/M17_Security_Hardening.md`.

## Usage

```bash
# open the page
start http://localhost:8000/v1/ops

# or read the data directly
curl -H "X-Ops-Token: tavuno-ops-local" http://localhost:8000/v1/ops/summary
```

The port binds to `127.0.0.1`, and Caddy does not proxy `/v1/ops` (it routes
only `/api`, `/directus`, `/media`).

## Verification (live stack)

| Check | Result |
| --- | --- |
| `GET /v1/ops` | 200, `text/html`, no token embedded |
| `GET /v1/ops/summary` without token | 401 |
| with a wrong token | 401 |
| with the correct token | 200 |
| Host suite | **407 passed, 13 skipped** (20 new ops tests) |
| `docker compose config -q` | exit 0 |

Live snapshot from the running stack:

```json
{
  "health":   {"database": "ok", "cache": "ok", "dispatcharr": "ok (0.28.0)", "ome": "ok"},
  "sync":     {"status": "success", "synced_at": "2026-10-02T18:52:07Z"},
  "counts":   {"channels": 2036, "active_channels": 2036, "categories": 105,
               "epg_programmes": 9828, "profiles": 13},
  "migrations": {"applied": 8, "latest": "008_m14_customization_profiles.sql"},
  "scope":    {"restricted": true, "limit": 10}
}
```

Counts are raw database totals; the `scope` block explains why the app
currently serves only the scoped subset.
