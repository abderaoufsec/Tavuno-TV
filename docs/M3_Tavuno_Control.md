# M3 — Tavuno Control Backend

## Status

COMPLETE — validated locally on 2026-09-18.

## Implementation

`tavuno-control` is a FastAPI service running on Python 3.12. It connects directly to Tavuno PostgreSQL and Redis using Docker service names. It does not yet call Dispatcharr or OvenMediaEngine; that integration is intentionally deferred.

## API

- `GET /health` — verifies PostgreSQL and Redis connectivity.
- `GET /v1/home` — active categories and featured channels.
- `GET /v1/channels` and `GET /v1/channels/{id}` — active channel catalog.
- `POST /v1/devices/register` — idempotent registration for an active Tavuno profile.
- `GET /v1/epg` — normalized EPG programmes.
- `GET /v1/movies`, `GET /v1/series`, `GET /v1/sports` — initial catalog surfaces.
- `GET /openapi.json` and `GET /docs` — generated API definition and interactive local documentation.

The API is accessible directly at `http://localhost:8000` and through Caddy at `http://localhost:8080/api`. Caddy removes the `/api` prefix before forwarding.

## Run and Test

```powershell
cd C:\Users\benab\CyberLab\Tavuno-TV
docker compose -f tavuno-infra\docker-compose.yml up -d --build tavuno-control
```

Run unit tests with the built service image:

```powershell
docker run --rm -v "${PWD}\tavuno-control:/src" -e POSTGRES_PASSWORD=test -e REDIS_PASSWORD=test tavuno-infra-tavuno-control:latest sh -lc "cd /src && PYTHONPATH=/src python -m unittest discover -s tests -v"
```

## Boundaries

- No authentication/authorization policy is exposed to clients yet; it is scheduled with account and playback milestones.
- No provider credentials, stream URLs, Dispatcharr control calls, or OME control calls exist in this service.
- SQL uses parameterized values for all request-supplied filters and identifiers.
