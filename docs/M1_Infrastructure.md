# M1 — Infrastructure Foundation

## Status

COMPLETE — validated locally on 2026-09-18.

## Services

- PostgreSQL 17.11
- Redis 8.0 with AOF persistence and password authentication
- Directus 12.3.1
- Dispatcharr 0.27.2 with a Celery worker
- OvenMediaEngine v0.21.0
- Prometheus v3.8.1
- Grafana 13.2.2, provisioned with Prometheus as its default datasource
- Caddy 2.10.2

## Networks

- `tavuno-internal`: all services; databases and monitoring remain internal.
- `tavuno-public`: Caddy only. Caddy bridges the public and internal networks to proxy approved local routes.

## Persistent Volumes

- `tavuno_postgres_data`
- `tavuno_redis_data`
- `tavuno_directus_uploads`
- `tavuno_directus_extensions`
- `tavuno_dispatcharr_data`
- `tavuno_prometheus_data`
- `tavuno_grafana_data`
- `tavuno_caddy_data`
- `tavuno_caddy_config`

## Local Endpoints

- Directus: `http://localhost:8055`
- Dispatcharr: `http://localhost:9191`
- OvenMediaEngine API: `http://localhost:8081`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Caddy reverse-proxy proof: `http://localhost:8080/`
- Caddy → Directus: `http://localhost:8080/directus/server/health` (the endpoint correctly returns `403` without Directus authentication, which proves proxy connectivity).

## Database Initialization

On a new PostgreSQL volume, `docker/postgres/init/10-dispatcharr-database.sh` creates the separate `dispatcharr` role and database. Existing PostgreSQL volumes are left untouched by PostgreSQL's standard initialization behavior.

## Validation

- [x] Compose configuration resolves successfully.
- [x] PostgreSQL and Redis report healthy.
- [x] Directus, Dispatcharr, Celery, OME, Prometheus, Grafana, and Caddy start.
- [x] Directus connects to PostgreSQL and Redis.
- [x] Dispatcharr connects to its PostgreSQL database and Redis.
- [x] OME control API requires authentication.
- [x] Prometheus is ready and Grafana has a provisioned Prometheus datasource.
- [x] Caddy returns a local response and proxies Directus across `tavuno-internal`.
- [x] `tavuno-public` and `tavuno-internal` are present with the intended membership.
- [x] Restart and down/up recovery preserve named-volume data.

## Operational Notes

- Copy the root `.env.example` to `tavuno-infra/.env` and replace every `CHANGE_ME` value before first startup.
- `.env` is ignored by Git. Rotate any development secrets that were exposed before this milestone was completed.
- OME's current API token is development-only and must be rotated and externalized before a shared or production deployment.
- M1 establishes infrastructure only. No business schema, IPTV source, media experiment, or StreamVault change is included.
