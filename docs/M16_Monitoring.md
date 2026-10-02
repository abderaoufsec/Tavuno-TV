# M16 — Monitoring & Operations

**Status: ✅ COMPLETE**

Prometheus, Grafana and Alertmanager already ran in the stack (M1), but M16 was
tracked as partial because nothing had been wired to them: Prometheus scraped
only itself, Grafana had a datasource and no dashboards, and no alert rules
existed. This milestone closes that.

## What was added

### 1. The API now exposes metrics — `GET /metrics`

`app/metrics.py` plus a request-observation middleware in `app/main.py`.

| Metric | Type | Labels |
| --- | --- | --- |
| `tavuno_http_requests_total` | counter | `method`, `route`, `status` |
| `tavuno_http_request_duration_seconds` | histogram | `method`, `route` |
| `tavuno_playback_sessions_started_total` | counter | `content_type` |
| `tavuno_active_playback_sessions` | gauge | — |
| `tavuno_dependency_up` | gauge | `dependency` (postgres, redis) |
| `tavuno_dispatcharr_sync_last_success_timestamp_seconds` | gauge | — |
| `tavuno_build_info` | gauge | `version` |

Three decisions worth knowing:

- **Labels are route *templates*, not paths.** `/v1/channels/123456` records as
  `/v1/channels/{channel_id}`, and an unmatched 404 collapses into one
  `unmatched` bucket. Without this a client walking the catalog would mint a
  time series per channel id and a scanner probing random URLs would mint one
  per probe. Guarded by `tests/test_metrics.py`.
- **`tavuno_active_playback_sessions` uses the same predicate as
  `playback.py`** (`status='active' AND last_seen_at >= NOW() - 90s`), so the
  dashboard cannot disagree with what `max_concurrent_streams` enforces.
- **Dispatcharr and OME are *not* probed from inside `/metrics`.** Both wrap
  their HTTP calls in a retrying circuit breaker (`app/resilience.py` —
  3 attempts, 20s timeout for Dispatcharr), so a hung dependency would stall
  the scrape that reports it and drop the whole target. That is the inversion
  monitoring exists to prevent, so they are probed from outside instead.

### 2. blackbox-exporter probes the dependencies that have no metrics

`config/blackbox/blackbox.yml`. The probe module requires **HTTP 200 *and*
`"status": "ok"` in the body** — a status code alone is not enough, because
`GET /v1/ome/health` deliberately answers `200` with
`{"status": "unreachable"}` when OME is down. A code-only probe would report a
dead media server as healthy and silence the most important alert.

Targets: `/v1/dispatcharr/health`, `/v1/ome/health`.

### 3. Exporters for the engines

- `postgres-exporter:v0.15.0` → `pg_up`, connections, transactions
- `redis_exporter:v1.62.0` → `redis_up`, memory, clients, cache hit ratio

Two deliberate quirks, both commented in `docker-compose.yml`:

- **`DATA_SOURCE_URI`/`USER`/`PASS` instead of `DATA_SOURCE_NAME`.** The
  database password contains `#`, a fragment delimiter in a URL, so a single
  URL-form DSN authenticates with a silently truncated password. v0.15.0
  passes these as libpq keyword parameters; v0.20.1 rebuilds a URL and fails
  auth — verified, not assumed.
- **`--no-collector.stat_bgwriter`.** PostgreSQL 17 split `pg_stat_bgwriter`
  into separate bgwriter/checkpointer views and this exporter still reads the
  old one. The dashboard uses `pg_stat_database`, which is unaffected.

### 4. Alert rules — `config/prometheus/alerts.yml`

8 rules, `promtool`-validated:

| Group | Rules |
| --- | --- |
| `tavuno-availability` | API down, dependency down, pg down, redis down, probe failed |
| `tavuno-freshness` | Dispatcharr sync stale (> 2h) |
| `tavuno-performance` | 5xx ratio > 5%, p95 zap authorization > 2.5s |

Each fires only on something actionable. The staleness rule in particular is
guarded by `> 0`: a stack with `DISPATCHARR_API_KEY` unset runs no sync by
design, and alerting on "never synced" would fire forever until someone muted
the whole ruleset.

### 5. Grafana dashboards — provisioned from files

Three dashboards in `config/grafana/dashboards/`, loaded by the provider in
`config/grafana/provisioning/dashboards/` (rescans every 30s, so editing JSON
needs no container restart):

- **Tavuno API** — request rate, 5xx rate, p95, latency and status breakdown
- **Tavuno Playback** — active sessions, starts by content type, zap
  authorization percentiles
- **Tavuno Infrastructure** — scrape targets, dependency probes, blackbox
  probes, catalog freshness, PostgreSQL, Redis

The datasource `uid` is pinned to `prometheus` in the provisioning file;
without that the dashboards would have to be rebound by hand on every fresh
Grafana volume.

## Alert delivery — currently off, by design

The Alertmanager route points at a **receiver with no integrations**: alerts
are recorded, visible in the UI, and exposed as `ALERTS`, but nothing is pushed
anywhere.

This is deliberate. This repository has no SMTP implementation
(`app/auth/email_service.py` is a `TODO` stub) so an email receiver could not
send; and pointing a webhook at an invented URL would either spam delivery
errors or silently discard pages *while appearing configured*. A null receiver
is the honest state.

**To start receiving alerts**, add one block to
`config/alertmanager/alertmanager.yml` and restart:

```yaml
receivers:
  - name: default
    webhook_configs:
      - url: https://hooks.slack.com/services/<...>
        send_resolved: true
```

Grouping and inhibition are independent of that change and need no edits.

## Verification (live stack)

| Check | Result |
| --- | --- |
| `promtool check config` / `check rules` | valid, 8 rules |
| `amtool check-config` | valid, 1 inhibit rule, 1 receiver |
| blackbox `--config.check` | valid |
| Prometheus targets | **6/6 up** |
| Health gauges | `pg_up=1`, `redis_up=1`, `tavuno_dependency_up=1` ×2, `probe_success=1` ×2 |
| Firing alerts | **0** |
| Grafana dashboards | 3 provisioned; proxy query returns data (p95 = 71ms) |
| Host suite | 387 passed, 13 skipped |
| `docker compose config -q` | exit 0 |

## Endpoints

- `http://localhost:9090` — Prometheus
- `http://localhost:3000` — Grafana (credentials in `tavuno-infra/.env`)
- `http://localhost:9093` — Alertmanager (bound to loopback)
- `http://localhost:8000/metrics` — API exposition (bound to loopback)

`/metrics` is unauthenticated because Prometheus sends no credentials; it is
not routed through Caddy (which proxies only `/api`, `/directus`, `/media`),
and the container port binds to `127.0.0.1`.

