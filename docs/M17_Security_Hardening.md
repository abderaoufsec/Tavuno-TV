# M17 — Security Hardening

**Status: ✅ COMPLETE, with named deferrals**

This records what was already in place, what this milestone added, and — most
usefully — what is deliberately **not** done and why. An audit that lists only
strengths is marketing; the deferrals section is the part worth reading.

## What this milestone added

### 1. Transport security (was: plain HTTP only)

`tavuno-infra/config/caddy/Caddyfile` now terminates TLS:

| Before | Now |
| --- | --- |
| `:80` serving `/api`, `/directus`, `/media` in cleartext | `:80` **only** redirects (301) to HTTPS |
| no TLS anywhere | `https://localhost` with `tls internal` |

Verified live:

```
$ curl -skI http://localhost:8080/api/health
HTTP/1.1 301 Moved Permanently
Location: https://localhost:8443/api/health

$ curl -skL -o /dev/null -w '%{http_code} %{num_redirects}\n' http://localhost:8080/api/health
200 1

$ openssl x509 -in localhost.crt -noout -issuer -dates
issuer=CN=Caddy Local Authority - ECC Intermediate
notBefore=Oct  2 19:02:28 2026 GMT
notAfter=Oct  3 07:02:28 2026 GMT
```

`tls internal` issues from Caddy's own CA — no domain and no internet access
needed, which is what makes it usable for a local launch. It auto-renews
(certificates are deliberately short-lived).

**Switching to Let's Encrypt** is one edit: replace the site address with the
domain and delete the `tls internal` line. Caddy then provisions and renews
automatically and performs the HTTP→HTTPS redirect itself, so the manual `:80`
block is deleted at the same time. At that point also add HSTS:

```caddy
header Strict-Transport-Security "max-age=31536000; includeSubDomains"
```

**HSTS is deliberately absent today.** Browsers ignore it over an untrusted
certificate, and emitting it against the internal CA risks pinning `localhost`
to HTTPS before the deployment is ready for that.

### 2. Response hardening headers

```caddy
header {
    X-Content-Type-Options "nosniff"
    Referrer-Policy "strict-origin-when-cross-origin"
    -Server
}
```

Verified present on the HTTPS site, and `Server` is suppressed (removing a
version fingerprint).

### 3. Unmatched paths no longer answer 200

Before this change, **any** unproxied path returned `200` with an empty body —
`/nonexistent`, `/v1/ops`, `/metrics` alike. That is bad on two counts: it reads
as success to an uptime monitor, and it tells a scanner nothing about what
exists. An explicit catch-all now refuses them:

| Path | Result |
| --- | --- |
| `/` | 200 (proxy banner) |
| `/api/*`, `/directus/*`, `/media/*` | proxied |
| `/v1/ops`, `/metrics`, `/nonexistent` | **404** |

The practical effect is that the ops dashboard and metrics endpoint are
genuinely unreachable from the public entry point rather than accidentally
exposed.

### 4. Startup warning on published default secrets

`app/config.py` carries four secrets with working defaults so a fresh clone
runs without setup. Each is a published constant, so its value outside
development is a real exposure. `Settings` now warns at construction:

```
SECURITY: JWT_SECRET is still the built-in default in a non-development
environment (production). Set it from the environment before exposing this service.
```

Checked: `JWT_SECRET`, `PLAYBACK_TOKEN_SECRET`, `OPS_TOKEN`, `OME_API_TOKEN`.
Silent in `development`/`dev`/`local`/`test`; **4 warnings** in `production`.

It warns rather than raises on purpose: refusing to start would convert a
configuration smell into an outage and hide the very log that explains it.
Covered by `tests/test_config_secrets.py` (7 cases).

### 5. Secrets inventory (for rotation)

| Setting | Ships as | Action for production |
| --- | --- | --- |
| `JWT_SECRET` | `tavuno-jwt-secret-key-change-in-production` | **rotate** |
| `PLAYBACK_TOKEN_SECRET` | `tavuno-playback-secret-key` | **rotate** |
| `TAVUNO_OPS_TOKEN` | `tavuno-ops-local` | **rotate** |
| `OME_API_TOKEN` | `tavuno-m1-local` | **rotate** |
| `POSTGRES_PASSWORD` / `REDIS_PASSWORD` | set in `.env` (gitignored) | already unique |
| `DISPATCHARR_POSTGRES_PASSWORD` | set in `.env` | already unique |
| `GRAFANA_ADMIN_PASSWORD` | set in `.env` | already unique |

No secret is committed: `.env` is gitignored and only `.env.example`
templates are tracked. The four defaults above are the only committed
constants, and each is now flagged at startup.

## What was already in place (verified, not assumed)

| Control | Where | Detail |
| --- | --- | --- |
| Password hashing | `app/auth/` | bcrypt (`PASSWORD_HASH_SCHEME`) |
| Access tokens | `app/auth/tokens.py` | JWT HS256, TTL 900s |
| Refresh tokens | `app/auth/tokens.py` | JWT HS256 with `jti`, TTL 30d |
| Refresh rotation + revocation | `TokenStorageService.denylist_refresh_jti` / `is_refresh_jti_denylisted` | Redis key `auth:refresh_denylist`, replay detection (S2-01) |
| Playback tokens | `app/playback.py` | HS256, TTL 120s, signature compared with `hmac.compare_digest` (line 127) — constant time |
| Login rate limiting | `app/auth/service.py::_check_rate_limit` | key `auth:login_rate:{sha256(email:ip)}`, 10 attempts/minute. The email is hashed so raw addresses are not stored in Redis |
| Device limits | `app/auth/service.py` | enforced against the plan; hardcoded 2-device default removed (S2-03) |
| Device revocation | `app/devices/` | revoked devices rejected at login and playback |
| No auto-registration at playback | `app/playback.py` | unregistered device → 403 (S1-06) |
| No spoofable device header | `app/auth/deps.py` | device identity comes from the JWT, not `X-Device-Fingerprint` (S2-06) |
| Catalog requires auth | all `/v1/*` catalog routes | via `current_principal` (S1-05) |
| Loopback binding | `tavuno-infra/docker-compose.yml` | `tavuno-control:8000`, `caddy:8080/8443`, `prometheus:9090`, `grafana:3000`, `alertmanager:9093` all bind `127.0.0.1` |
| No credentials in VCS | `.gitignore` | `.env` untracked; only templates committed |

## Deferred, with reasons

These are known and accepted, not overlooked. Each names what would have to
change and what the exposure is today.

### 1. The shipped launch has no login (`AUTH_OPEN_ACCESS=true`)

**Decision, not a defect** — this is the free-launch requirement. Consequences
worth stating plainly:

- Catalog and playback authorise the seeded `guest@tavuno.local` identity when
  no token is supplied, so anyone who can reach the API can browse and play.
- All anonymous callers **share one guest profile and device**, so
  `max_concurrent_streams` cannot isolate them — noted in `playback.py`.
- `require_admin` routes (the Dispatcharr sync) are unreachable, since the
  guest role is `user`. The superset check in `tests/test_open_access.py`
  still proves admin routes 403, i.e. they fail closed.

**Today's only boundary is the loopback bind.** Before any public exposure,
`AUTH_OPEN_ACCESS` must be `false` and the rate limits below widened.

### 2. `app/auth/service.py:309` — password reset does not denylist refresh tokens

```
# TODO(Fix 2): denylist all refresh tokens for profile
```

Password reset deactivates the profile's devices, but outstanding **refresh
tokens stay valid until expiry (up to 30 days)**. Access tokens are
short (15 min), so the window is the refresh lifetime, and it requires a stolen
token to exploit.

Fixing it needs an index of issued refresh JTIs per profile, which does not
exist today. Tracked as part of the M9 hardening line rather than done here.

### 3. `app/auth/email_service.py:22,34` — SMTP not implemented

Both send paths are `TODO` stubs that return `True` to simulate success. There
is no security impact in itself, but two consequences matter:

- Password reset cannot actually reach a user, so flows depending on it are
  non-functional rather than merely insecure.
- It is why Alertmanager has **no email receiver** (see `M16_Monitoring.md`).

### 4. Rate limiting is narrow, and fails open

Only login is limited. There is no general per-IP limit on catalog or playback
endpoints, and `_check_rate_limit` **allows the request when Redis errors** —
deliberate (a cache outage should not lock everyone out) but it means the limit
disappears exactly when Redis is down or under attack.

### 5. Playback tokens are not enforced at the proxy

`/media/*` is passed through by Caddy to OME; token verification happens in the
app/OME layer (`GET /v1/media/verify`). Enforcing it at the proxy needs extra
Caddy modules, as the Caddyfile comment records.

### 6. Metrics and ops endpoints have no authentication of their own

`/metrics` is tokenless by necessity (Prometheus sends no credentials) and
`/v1/ops/summary` uses a shared secret that ships with a default. Both are
loopback-bound and explicitly **not proxied** (they answer 404 through Caddy) —
verified. Treat their port binding as the control.

### 7. No independent security review

This document is a **self-assessment** assembled from the code. It is not a
penetration test, and no third party has reviewed the authentication
implementation. Treat M17 as "documented and hardened", not "audited".

## Verification (live)

| Check | Result |
| --- | --- |
| HTTP → HTTPS redirect | 301 to `https://localhost:8443/...` |
| Full chain follow | 200 after 1 redirect |
| Certificate issuer | `CN=Caddy Local Authority - ECC Intermediate` |
| Security headers | `X-Content-Type-Options`, `Referrer-Policy` present; `Server` removed |
| `/v1/ops` and `/metrics` via Caddy | **404** (not exposed) |
| `/api/health` via Caddy | 200, correct JSON |
| `/directus/server/health` via Caddy | 403 (expected without Directus auth — proves proxy connectivity) |
| Default-secret warning | 4 warnings in `production`, silent in `development` |
| Host suite | **414 passed, 13 skipped** |

## Related documents

- `docs/M15_Admin.md` — the ops dashboard and its token
- `docs/M16_Monitoring.md` — why alerting has no delivery target yet
- `milestones.md` — the post-M9 remediation list (S1-03 … S2-06)