# M3 — Tavuno Control Backend

## Status

COMPLETE — FastAPI service is containerized, covers the M3 product APIs, and can run independently of the Android client.

## Implementation

`tavuno-control` is a FastAPI service on Python 3.12. It talks to Tavuno PostgreSQL and Redis by Docker service names. Catalog responses can be cached in Redis. Dispatcharr synchronization belongs to M4 and is invoked from this service; OvenMediaEngine playback remains out of M3 scope.

## API

- `GET /health` — PostgreSQL and Redis connectivity, plus optional last Dispatcharr sync status.
- `GET /v1/home` — active categories and featured channels (Redis-cached).
- `GET /v1/channels` and `GET /v1/channels/{id}` — active channel catalog; channel detail includes stored source mappings without stream URLs.
- `POST /v1/devices/register` — idempotent registration for an active Tavuno profile.
- `GET /v1/epg` — normalized EPG programmes.
- `GET /v1/movies`, `GET /v1/series`, `GET /v1/sports` — catalog surfaces.
- `GET /openapi.json` and `GET /docs` — generated API definition and interactive local documentation.

The API is accessible at `http://localhost:8000` and through Caddy at `http://localhost:8080/api`.

## Run and Test

```powershell
cd C:\Users\benab\CyberLab\Projects\tavuno-tv
docker compose -f tavuno-infra\docker-compose.yml up -d --build tavuno-control
```

Run unit tests:

```powershell
cd tavuno-control
$env:POSTGRES_PASSWORD='test'
$env:REDIS_PASSWORD='test'
python -m unittest discover -s tests -v
```

Or with the service image:

```powershell
docker run --rm -e POSTGRES_PASSWORD=test -e REDIS_PASSWORD=test tavuno-infra-tavuno-control:latest python -m unittest discover -s tests -v
```

## Boundaries

- Client authentication/authorization policy is still scheduled with account and playback milestones.
- Raw provider stream URLs are never returned by catalog endpoints.
- SQL uses parameterized values for request-supplied filters and identifiers. Catalog table names are allow-listed.
