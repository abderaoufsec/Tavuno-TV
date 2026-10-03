import asyncio
import logging
from contextlib import asynccontextmanager, contextmanager
from typing import Annotated, Any
import time

from fastapi import Depends, FastAPI, HTTPException, Query, Request, status, Header
from fastapi.responses import HTMLResponse, JSONResponse, Response
from pydantic import BaseModel, Field

from .config import get_settings
from .playback import authorize_live_playback, authorize_movie_playback, authorize_episode_playback, heartbeat_session, stop_session, generate_auth_token, verify_playback_token
from .playback_identity import resolve_playback_identity
from .services import Services
from .db_migrations import run_migrations
from .auth.router import router as auth_router
from .devices.router import router as devices_router
from .auth.deps import current_principal, require_admin
from .catalog.service import CatalogService
from .customize.models import CustomizationPayload
from .customize.service import CustomizeService
from .favourites.models import FavouriteList, FavouriteToggle
from .favourites.service import FavouritesService
from .resume.models import ProgressList, ProgressUpdate
from .resume.service import ResumeService
from .epg.service import EpgService, parse_timestamp
from .profiles.models import CreateProfileRequest, UpdateProfileRequest
from .profiles.service import ProfilesService
from .session_reaper import reap_expired_sessions
from .ops import OPS_HTML, OpsService, token_matches
from . import metrics as prometheus_metrics

logging.basicConfig(level=get_settings().log_level, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("tavuno-control")

JWT_EXPIRATION_HOURS = 24


@asynccontextmanager
async def lifespan(app: FastAPI):
    if getattr(app.state, "services", None) is None:
        app.state.services = Services(get_settings())
    services: Services = app.state.services

    if services.settings.auto_migrate:
        # Schema first, background loops second. A failure here is fatal on
        # purpose: answering requests against a schema we could not bring up to
        # date is worse than refusing to start. run_migrations goes through
        # services.connection's context manager, so a migration that fails rolls
        # back together with its ledger row.
        try:
            report = run_migrations(services.connection)
        except Exception:
            logger.exception("Automatic database migration failed")
            raise
        logger.info("Automatic database migration: %s", report.summary())
    else:
        logger.info(
            "Automatic database migration disabled (set TAVUNO_AUTO_MIGRATE=true to enable)"
        )

    dispatcharr_interval = services.settings.dispatcharr_sync_interval_seconds
    session_reaper_interval = services.settings.session_reaper_interval_seconds
    stop = asyncio.Event()

    async def periodic_dispatcharr_sync() -> None:
        if dispatcharr_interval <= 0:
            logger.info("Dispatcharr periodic sync disabled")
            return
        if not services.settings.dispatcharr_api_key:
            logger.warning("Dispatcharr periodic sync skipped: DISPATCHARR_API_KEY is not set")
            return
        logger.info("Dispatcharr periodic sync enabled every %s seconds", dispatcharr_interval)
        while not stop.is_set():
            try:
                with services.connection() as connection:
                    summary = services.sync.sync_all(connection)
                logger.info("Dispatcharr periodic sync complete: %s", summary)
            except Exception:
                logger.exception("Dispatcharr periodic sync failed")
            try:
                await asyncio.wait_for(stop.wait(), timeout=dispatcharr_interval)
            except TimeoutError:
                continue

    async def periodic_session_reaper() -> None:
        if session_reaper_interval <= 0:
            logger.info("Session reaper disabled")
            return
        logger.info("Session reaper enabled every %s seconds", session_reaper_interval)
        while not stop.is_set():
            try:
                with services.connection() as connection:
                    reaped_count = reap_expired_sessions(connection)
                if reaped_count > 0:
                    logger.info("Session reaper: reaped %d expired sessions", reaped_count)
            except Exception:
                logger.exception("Session reaper failed")
            try:
                await asyncio.wait_for(stop.wait(), timeout=session_reaper_interval)
            except TimeoutError:
                continue

    dispatcharr_task = asyncio.create_task(periodic_dispatcharr_sync())
    reaper_task = asyncio.create_task(periodic_session_reaper())
    yield
    stop.set()
    dispatcharr_task.cancel()
    reaper_task.cancel()
    try:
        await dispatcharr_task
        await reaper_task
    except asyncio.CancelledError:
        pass


app = FastAPI(
    title="Tavuno Control API",
    version="0.3.0",
    description="Tavuno product API for catalog, devices, EPG, and Dispatcharr synchronization.",
    openapi_url="/openapi.json",
    docs_url="/docs",
    lifespan=lifespan,
)
app.state.services = None
prometheus_metrics.set_build_info("0.3.0")

# Include routers
app.include_router(auth_router)
app.include_router(devices_router)


@app.middleware("http")
async def observe_requests(request: Request, call_next):
    """Count and time every request (M16).

    Reading the route template *after* ``call_next`` matters: routing has run
    by then, so ``scope["route"]`` holds the pattern rather than the concrete
    path, and a 404 (which never matched a route) falls back to a single
    ``unmatched`` bucket instead of minting a label per probed URL.

    Wrapped so instrumentation can never fail a request — a broken metric
    must not become a 500.
    """
    started = time.perf_counter()
    status_code = 500
    try:
        response = await call_next(request)
        status_code = response.status_code
        return response
    finally:
        try:
            prometheus_metrics.observe_request(
                method=request.method,
                route=prometheus_metrics.route_template(request),
                status=status_code,
                started_at=started,
            )
        except Exception:
            logger.debug("request observation failed", exc_info=True)


def get_services(request: Request) -> Services:
    if getattr(request.app.state, "services", None) is None:
        request.app.state.services = Services(get_settings())
    return request.app.state.services


ServicesDependency = Annotated[Services, Depends(get_services)]


def profile_id_of(principal: Any) -> int | None:
    """The signed-in profile id, or ``None`` when there isn't one.

    ``tests/test_api.py`` invokes route handlers directly, so ``principal`` is the
    unresolved ``Depends`` marker rather than a dict. Treating anything that is not
    a dict as "no profile" keeps those calls working and, more importantly, makes a
    missing principal degrade to the plain un-customized catalog instead of a 500.
    """
    if not isinstance(principal, dict):
        return None
    value = principal.get("profile_id")
    return int(value) if value is not None else None


def require_profile_id(principal: Any) -> int:
    """The signed-in profile id, or a 401 when the caller has none.

    Customization and profiles are meaningless without an owner, so a caller that
    reaches them unauthenticated gets a 401 rather than a 500 from a KeyError.
    """
    profile_id = profile_id_of(principal)
    if profile_id is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")
    return profile_id


@contextmanager
def database(services: Services):
    with services.connection() as connection:
        yield connection


@app.exception_handler(Exception)
async def unhandled_exception_handler(_: Request, exc: Exception) -> JSONResponse:
    logger.exception("Unhandled API exception", exc_info=exc)
    return JSONResponse(status_code=500, content={"detail": "Internal server error"})


@app.get("/health", tags=["operations"])
def health(services: ServicesDependency) -> dict[str, Any]:
    payload = {"status": "ok", "services": services.health()}
    last_sync = services.sync.last_sync()
    if last_sync:
        payload["last_dispatcharr_sync"] = last_sync
    return payload


@app.get("/metrics", tags=["operations"], summary="Prometheus exposition (M16)")
def metrics_endpoint(services: ServicesDependency) -> Response:
    """Prometheus scrape target.

    Unauthenticated on purpose — Prometheus has no credentials to send, and
    the port is bound to loopback by compose — and excluded from the
    request-observation middleware's own route label only by virtue of being
    a route like any other, so its traffic is visible too.

    The gauges are re-read here rather than on a timer: scraping *is* the
    trigger, so the numbers are at most one scrape stale and there is no
    background loop to leak or desynchronise.
    """
    body, content_type = prometheus_metrics.render(services)
    return Response(content=body, media_type=content_type)


@app.get("/v1/ops", tags=["operations"], response_class=HTMLResponse, summary="Ops dashboard (M15)")
def ops_page() -> HTMLResponse:
    """Read-only operations dashboard (M15).

    Served without authentication because the document carries no data — it is
    an empty shell that fetches ``/v1/ops/summary`` and holds the caller's
    token in sessionStorage. Serving the HTML openly is what keeps the secret
    out of the URL, the document, and the access log; the data endpoint is the
    one that is guarded.

    Write operations stay in Directus. See ``docs/00_TavunoTV_Master_Strategy.md``
    ("Directus Studio initially, custom Tavuno Admin UI later where needed").
    """
    return HTMLResponse(content=OPS_HTML)


@app.get("/v1/ops/summary", tags=["operations"], summary="Ops dashboard data (M15)")
def ops_summary(
    services: ServicesDependency,
    x_ops_token: str | None = Header(None),
) -> dict[str, Any]:
    """Snapshot of catalog, health, sync, playback and schema state.

    Guarded by a shared secret rather than ``require_admin``: under
    ``AUTH_OPEN_ACCESS`` the guest principal has role ``user``, so an
    admin-gated route would be unreachable for as long as the app ships
    without login. Compared in constant time (``hmac.compare_digest``).
    """
    expected = getattr(services.settings, "ops_token", "")
    if not token_matches(expected, x_ops_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid ops token")
    return OpsService(services).snapshot()


@app.get("/v1/home", tags=["catalog"])
def home(services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    catalog = CatalogService(services)
    return catalog.get_home(profile_id=profile_id_of(principal))


@app.get("/v1/channels", tags=["catalog"])
def list_channels(services: ServicesDependency, category_id: int | None = None, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    catalog = CatalogService(services)
    channels = catalog.get_channels(category_id=category_id, profile_id=profile_id_of(principal))
    return [channel.model_dump() for channel in channels]


@app.get("/v1/channels/{channel_id}", tags=["catalog"])
def get_channel(channel_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    catalog = CatalogService(services)
    channel = catalog.get_channel(channel_id)
    if channel is None:
        raise HTTPException(status_code=404, detail="Channel not found")

    # Get sources separately (not in catalog model)
    with database(services) as connection:
        sources = connection.execute(
            """
            SELECT provider, external_id, priority, is_active
            FROM tavuno_channel_sources
            WHERE channel = %s
            ORDER BY priority, id
            """,
            (channel_id,),
        ).fetchall()

    channel_data = channel.model_dump()
    channel_data["sources"] = sources
    return channel_data


@app.get("/v1/channels/{channel_id}/details", tags=["catalog"])
def get_channel_details(channel_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Get detailed channel information for content detail screens (M12)."""
    catalog = CatalogService(services)
    channel_details = catalog.get_channel_details(channel_id)
    if channel_details is None:
        raise HTTPException(status_code=404, detail="Channel not found")
    return channel_details.model_dump()


@app.get("/v1/epg", tags=["epg"])
def epg(services: ServicesDependency, channel_id: int | None = None, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    # Try cache first for M5 EPG caching requirement
    if channel_id is not None:
        cached = services.get_cached_epg(channel_id)
        if cached:
            return cached

    query = """
        SELECT p.id, p.title, p.starts_at, p.ends_at, p.description, e.channel AS channel_id
        FROM tavuno_epg_programmes p JOIN tavuno_epg_channels e ON e.id = p.epg_channel
    """
    parameters: tuple[Any, ...] = ()
    if channel_id is not None:
        query += " WHERE e.channel = %s"
        parameters = (channel_id,)
    query += " ORDER BY p.starts_at"
    with database(services) as connection:
        programmes = connection.execute(query, parameters).fetchall()

    # Cache the result for channel-specific queries
    if channel_id is not None:
        services.cache_epg(channel_id, programmes)

    return programmes


@app.get("/v1/categories", tags=["catalog"])
def list_categories(services: ServicesDependency, kind: str | None = None, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    catalog = CatalogService(services)
    categories = catalog.get_categories(kind=kind, profile_id=profile_id_of(principal))
    return [category.model_dump() for category in categories]


@app.get("/v1/categories/{category_id}", tags=["catalog"])
def get_category(category_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    catalog = CatalogService(services)
    category = catalog.get_category(category_id)
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    return category.model_dump()


@app.get("/v1/search", tags=["catalog"])
def search_catalog(
    services: ServicesDependency,
    q: str,
    limit: int = 20,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Case-insensitive search across channels, movies and series (Slice B)."""
    catalog = CatalogService(services)
    results = catalog.search(q, limit=limit)
    return {
        "query": results["query"],
        "channels": [item.model_dump() for item in results["channels"]],
        "movies": [item.model_dump() for item in results["movies"]],
        "series": [item.model_dump() for item in results["series"]],
    }


@app.get("/v1/movies", tags=["catalog"])
def movies(services: ServicesDependency, category_id: int | None = None, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    catalog = CatalogService(services)
    movies = catalog.get_movies(category_id=category_id)
    return [movie.model_dump() for movie in movies]


@app.get("/v1/movies/{movie_id}", tags=["catalog"])
def get_movie(movie_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    catalog = CatalogService(services)
    movie = catalog.get_movie(movie_id)
    if movie is None:
        raise HTTPException(status_code=404, detail="Movie not found")
    return movie.model_dump()


@app.get("/v1/movies/{movie_id}/details", tags=["catalog"])
def get_movie_details(movie_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Get detailed movie information for content detail screens (M12)."""
    catalog = CatalogService(services)
    movie_details = catalog.get_movie_details(movie_id)
    if movie_details is None:
        raise HTTPException(status_code=404, detail="Movie not found")
    return movie_details.model_dump()


@app.get("/v1/series", tags=["catalog"])
def series(services: ServicesDependency, category_id: int | None = None, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    catalog = CatalogService(services)
    series_list = catalog.get_series(category_id=category_id)
    return [series_item.model_dump() for series_item in series_list]


@app.get("/v1/series/{series_id}", tags=["catalog"])
def get_series(series_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    catalog = CatalogService(services)
    series_item = catalog.get_series_by_id(series_id)
    if series_item is None:
        raise HTTPException(status_code=404, detail="Series not found")
    return series_item.model_dump()


@app.get("/v1/series/{series_id}/details", tags=["catalog"])
def get_series_details(series_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Get detailed series information for content detail screens (M12)."""
    catalog = CatalogService(services)
    series_details = catalog.get_series_details(series_id)
    if series_details is None:
        raise HTTPException(status_code=404, detail="Series not found")
    return series_details.model_dump()


@app.get("/v1/series/{series_id}/seasons", tags=["catalog"])
def get_series_seasons(series_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    """Get seasons for a specific series (M12)."""
    catalog = CatalogService(services)
    series_details = catalog.get_series_details(series_id)
    if series_details is None:
        raise HTTPException(status_code=404, detail="Series not found")
    return series_details.seasons if series_details.seasons else []


@app.get("/v1/seasons/{season_id}/episodes", tags=["catalog"])
def get_season_episodes(season_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    """Get episodes for a specific season (M12)."""
    catalog = CatalogService(services)
    episodes = catalog.get_season_episodes(season_id)
    return episodes


@app.get("/v1/sports/competitions", tags=["catalog"])
def list_competitions(services: ServicesDependency, sport: str | None = None, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    """Get all active competitions, optionally filtered by sport (M11)."""
    catalog = CatalogService(services)
    competitions = catalog.get_competitions(sport=sport)
    return [competition.model_dump() for competition in competitions]


@app.get("/v1/sports/competitions/{competition_id}", tags=["catalog"])
def get_competition(competition_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Get a specific competition by ID (M11)."""
    catalog = CatalogService(services)
    competition = catalog.get_competition(competition_id)
    if competition is None:
        raise HTTPException(status_code=404, detail="Competition not found")
    return competition.model_dump()


@app.get("/v1/sports/competitions/{competition_id}/matches", tags=["catalog"])
def list_competition_matches(
    competition_id: int,
    services: ServicesDependency,
    status: str | None = None,
    limit: int = 100,
    principal: dict = Depends(current_principal)
) -> list[dict[str, Any]]:
    """Get matches for a specific competition, optionally filtered by status (M11)."""
    catalog = CatalogService(services)
    matches = catalog.get_matches(competition_id=competition_id, status=status, limit=limit)
    return [match.model_dump() for match in matches]


@app.get("/v1/sports/matches", tags=["catalog"])
def list_matches(
    services: ServicesDependency,
    status: str | None = None,
    limit: int = 100,
    principal: dict = Depends(current_principal)
) -> list[dict[str, Any]]:
    """Get all matches, optionally filtered by status (M11)."""
    catalog = CatalogService(services)
    matches = catalog.get_matches(status=status, limit=limit)
    return [match.model_dump() for match in matches]


@app.get("/v1/sports/matches/{match_id}", tags=["catalog"])
def get_match(match_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Get a specific match by ID (M11)."""
    catalog = CatalogService(services)
    match = catalog.get_match(match_id)
    if match is None:
        raise HTTPException(status_code=404, detail="Match not found")
    return match.model_dump()


@app.get("/v1/sports/matches/{match_id}/details", tags=["catalog"])
def get_match_details(match_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Get detailed match information with team names and channel (M11)."""
    catalog = CatalogService(services)
    match_details = catalog.get_match_details(match_id)
    if match_details is None:
        raise HTTPException(status_code=404, detail="Match not found")
    return match_details.model_dump()


@app.get("/v1/epg/channel/{channel_id}/now-next", tags=["epg"])
def channel_epg_now_next(channel_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Return NOW, NEXT, and LATER programmes for a specific channel (M5)."""
    cached = services.get_cached_now_next(channel_id)
    if cached is not None:
        return cached

    with database(services) as connection:
        programmes = connection.execute(
            """
            SELECT p.id, p.title, p.starts_at, p.ends_at, p.description, e.channel AS channel_id
            FROM tavuno_epg_programmes p
            JOIN tavuno_epg_channels e ON e.id = p.epg_channel
            WHERE e.channel = %s AND p.ends_at >= NOW()
            ORDER BY p.starts_at ASC
            LIMIT 3
            """,
            (channel_id,),
        ).fetchall()

    payload = {
        "channel_id": channel_id,
        "now": programmes[0] if len(programmes) > 0 else None,
        "next": programmes[1] if len(programmes) > 1 else None,
        "later": programmes[2] if len(programmes) > 2 else None,
    }
    services.cache_now_next(channel_id, payload)
    return payload


@app.get("/v1/epg/window", tags=["epg"])
def epg_window(
    services: ServicesDependency,
    start: str,
    end: str,
    channel_ids: list[int] | None = Query(default=None),
    category_id: int | None = None,
    limit: int | None = None,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Windowed guide (Slice C): every channel's programmes overlapping [start, end).

    One query for the whole grid, channels name-ordered and capped, and filtered
    through the same live-channel test scope the channel list uses — so the guide
    can never advertise a channel `/v1/channels` refuses to show.
    """
    epg = EpgService(services)
    try:
        window = epg.window(
            window_start=parse_timestamp(start),
            window_end=parse_timestamp(end),
            channel_ids=channel_ids,
            category_id=category_id,
            limit=limit,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return window.model_dump()


# --- Customization (Slice D) -------------------------------------------------
#
# Per-profile rail order and visibility for channels and categories. A PUT is a
# full replace for one kind: the client sends the complete list it just rendered,
# which is what makes "un-pin" expressible. Reads are always scoped to the
# caller's profile, so one account cannot read or write another's overrides.


@app.get("/v1/customize/{kind}", tags=["customize"])
def get_customizations(kind: str, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """The caller's overrides for one kind."""
    try:
        result = CustomizeService(services).list(require_profile_id(principal), kind)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return result.model_dump()


@app.put("/v1/customize/{kind}", tags=["customize"])
def put_customizations(
    kind: str,
    payload: CustomizationPayload,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Replace the caller's overrides for one kind."""
    try:
        result = CustomizeService(services).save(require_profile_id(principal), kind, payload.items)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return result.model_dump()


@app.delete("/v1/customize/{kind}", tags=["customize"])
def delete_customizations(kind: str, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Drop the caller's overrides for one kind, restoring the natural catalog order."""
    try:
        removed = CustomizeService(services).reset(require_profile_id(principal), kind)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"kind": kind, "removed": removed}


# --- Favourites (A6) --------------------------------------------------------
#
# A favourite is a per-profile *flag* on one catalog item, deliberately kept
# separate from `/v1/customize` (which owns rail order and visibility): "pinned
# to the top" and "I love this" are different intents, and conflating them makes
# un-pinning unable to express "still a favourite, just not first".
#
# Every route requires a profile for the same reason the customize routes do —
# there is no such thing as a favourite without an owner.


def _favorite_row_to_item(catalog: CatalogService, kind: str, row: dict[str, Any]) -> dict[str, Any] | None:
    """Turn a raw ``tavuno_*`` row into the public shape of that kind.

    Goes through the catalog's own mappers so a favourite rail shows exactly
    what the grid it was favourited from shows — same fields, same artwork
    normalization — rather than a second, drifting serialization.
    """
    try:
        if kind == "channel":
            item = catalog._map_channel(row)
        elif kind == "movie":
            item = catalog._map_movie(row)
        elif kind == "series":
            item = catalog._map_series(row)
        else:
            return None
    except Exception:
        logger.warning("Could not map favourite row for kind=%s", kind)
        return None
    return item.model_dump()


@app.get("/v1/favourites/{kind}", tags=["favourites"])
def list_favourites(
    kind: str,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
    limit: int = 50,
) -> dict[str, Any]:
    """The caller's favourites for one kind, newest first."""
    profile_id = require_profile_id(principal)
    try:
        rows = FavouritesService(services).list_rows(profile_id, kind, limit=max(1, min(limit, 200)))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    catalog = CatalogService(services)
    items = [
        mapped
        for mapped in (_favorite_row_to_item(catalog, kind, row) for row in rows)
        if mapped is not None
    ]
    return FavouriteList(kind=kind, items=items).model_dump()


@app.post("/v1/favourites", tags=["favourites"])
def set_favourite(
    payload: FavouriteToggle,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Set the favourite flag for one item.

    Omitting ``is_favourite`` flips the current value, which is what a heart
    button wants: the client does not have to read the row to know what pressing
    it means. Supplying it makes the call idempotent, which is what a "retry"
    wants.
    """
    profile_id = require_profile_id(principal)
    service = FavouritesService(services)
    try:
        value = (
            payload.is_favourite
            if payload.is_favourite is not None
            else service.toggle(profile_id, payload.kind, payload.item_id)
        )
        service.set(profile_id, payload.kind, payload.item_id, bool(value))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    return {
        "kind": payload.kind,
        "item_id": payload.item_id,
        "is_favourite": bool(value),
        "item": _favorite_item(services, payload.kind, payload.item_id) if value else None,
    }


def _favorite_item(services: Services, kind: str, item_id: int) -> dict[str, Any] | None:
    """Resolve one item through the public catalog reads, or ``None``.

    Goes via the catalog service (not a private query) so the item echoed back
    on a toggle is byte-identical to what the grid would have rendered for it.
    """
    catalog = CatalogService(services)
    try:
        if kind == "channel":
            item = catalog.get_channel(item_id)
        elif kind == "movie":
            item = catalog.get_movie(item_id)
        elif kind == "series":
            item = catalog.get_series_by_id(item_id)
        else:
            return None
    except Exception:
        logger.warning("Could not resolve favourite item kind=%s id=%s", kind, item_id)
        return None
    return item.model_dump() if item is not None else None


@app.delete("/v1/favourites/{kind}/{item_id}", tags=["favourites"])
def delete_favourite(
    kind: str,
    item_id: int,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Drop one favourite.

    Idempotent rather than 404 on a missing row: un-favouriting something that
    was never favourited already satisfies the caller.
    """
    profile_id = require_profile_id(principal)
    try:
        FavouritesService(services).set(profile_id, kind, item_id, False)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"kind": kind, "item_id": item_id, "is_favourite": False}


# --- Resume / progress (A6) -------------------------------------------------
#
# Durable "where was I" per profile. Deliberately separate from
# `/v1/playback/*`, which mints an authorization lease that the session reaper
# deletes — progress recorded there would evaporate minutes later.


@app.get("/v1/resume", tags=["resume"])
def list_resume(
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
    limit: int = 20,
) -> dict[str, Any]:
    """Continue-watching entries for the caller, most recently watched first."""
    profile_id = require_profile_id(principal)
    rows = ResumeService(services).recent(profile_id, limit=max(1, min(limit, 50)))
    catalog = CatalogService(services)
    items = [
        mapped
        for mapped in (_resume_row_to_item(catalog, row) for row in rows)
        if mapped is not None
    ]
    return ProgressList(items=items).model_dump()


def _resume_row_to_item(catalog: CatalogService, row: dict[str, Any]) -> dict[str, Any] | None:
    """Publicize one resume row (a ``kind`` tag plus a raw catalog row).

    :meth:`ResumeService.recent` returns raw ``SELECT *`` rows; this maps them
    through the catalog's own mappers so the rail renders identically to a grid,
    while adding the three resume-specific keys (``kind``, ``progress``,
    ``position_ms``) the client needs to draw a bar and pick the item back up.
    """
    kind = str(row.get("kind") or "")
    try:
        if kind == "channel":
            item = catalog._map_channel(row)
        elif kind == "movie":
            item = catalog._map_movie(row)
        elif kind == "series":
            item = catalog._map_series(row)
        else:
            return None
    except Exception:
        logger.warning("Could not map resume row for kind=%s", kind)
        return None

    mapped = item.model_dump()
    mapped.pop("viewer", None)
    mapped.update(
        {
            "kind": kind,
            "progress": row.get("progress"),
            "position_ms": row.get("position_ms"),
            "duration_ms": row.get("duration_ms"),
        }
    )
    return mapped


@app.get("/v1/resume/{kind}/{item_id}", tags=["resume"])
def get_resume(
    kind: str,
    item_id: int,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Progress for one item; 404 when there is nothing to resume."""
    profile_id = require_profile_id(principal)
    try:
        payload = ResumeService(services).get(profile_id, kind, item_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    if payload is None:
        raise HTTPException(status_code=404, detail="No resume position for that item")
    return payload.model_dump()


@app.put("/v1/resume", tags=["resume"])
def put_resume(
    payload: ProgressUpdate,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
) -> Any:
    """Report a playback position.

    Answers **204** when the write cleared the row instead of storing one —
    finished, or rewound to the start. That is a success, not a failure: the
    client's correct next action is to stop drawing a progress bar, and a 200
    with an empty body would leave it guessing whether the write happened.
    """
    profile_id = require_profile_id(principal)
    try:
        stored = ResumeService(services).record(
            profile_id,
            payload.kind,
            payload.item_id,
            payload.position_ms,
            payload.duration_ms,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    if stored is None:
        return Response(status_code=status.HTTP_204_NO_CONTENT)
    return stored.model_dump()


# --- Profiles (Slice D) ------------------------------------------------------
#
# One account (the caller's own profile) may own several child viewing profiles.
# The account row is editable here but never deletable — that would orphan a
# subscription — so DELETE only ever removes an extra viewer.


@app.get("/v1/profiles", tags=["profiles"])
def list_profiles(services: ServicesDependency, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    """The account plus its child profiles, account first."""
    require_profile_id(principal)
    return [profile.model_dump() for profile in ProfilesService(services).list_for(principal)]


@app.post("/v1/profiles", tags=["profiles"], status_code=status.HTTP_201_CREATED)
def create_profile(
    request: CreateProfileRequest,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Add a viewing profile under the caller's account."""
    require_profile_id(principal)
    try:
        created = ProfilesService(services).create(
            principal, request.display_name, request.avatar, request.is_kids
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return created.model_dump()


@app.patch("/v1/profiles/{profile_id}", tags=["profiles"])
def update_profile(
    profile_id: int,
    request: UpdateProfileRequest,
    services: ServicesDependency,
    principal: dict = Depends(current_principal),
) -> dict[str, Any]:
    """Rename/re-flag the account or one of its children."""
    require_profile_id(principal)
    try:
        updated = ProfilesService(services).update(
            principal, profile_id, request.display_name, request.avatar, request.is_kids
        )
    except ValueError as exc:
        if str(exc) == "profile_not_found":
            raise HTTPException(status_code=404, detail="Profile not found") from exc
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return updated.model_dump()


@app.delete("/v1/profiles/{profile_id}", tags=["profiles"])
def delete_profile(profile_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    """Remove a child profile. The account row itself is not deletable."""
    require_profile_id(principal)
    try:
        ProfilesService(services).delete(principal, profile_id)
    except ValueError as exc:
        if str(exc) == "profile_not_found":
            raise HTTPException(status_code=404, detail="Profile not found") from exc
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    return {"deleted": True, "profile_id": profile_id}


class SessionRequest(BaseModel):
    session_id: int


@app.post("/v1/playback/live/{channel_id}", tags=["playback"])
def playback_live(
    channel_id: int,
    services: ServicesDependency,
    authorization: str | None = Header(None),
) -> dict[str, Any]:
    """Authorize a live playback stream with JWT authentication (M7).

    With AUTH_OPEN_ACCESS enabled the seeded guest identity is used when no
    valid Bearer token is supplied (free launch, no login required).
    """
    try:
        profile_id, device_key = resolve_playback_identity(None, services, authorization)
        with database(services) as connection:
            result = authorize_live_playback(
                profile_id=profile_id,
                device_key=device_key,
                channel_id=channel_id,
                connection=connection,
                settings=services.settings,
            )
        prometheus_metrics.observe_playback_session("live")
        return result
    except HTTPException:
        raise
    except ValueError as exc:
        logger.exception("Playback authorization failed - invalid token")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token") from exc
    except Exception as exc:
        logger.exception("Playback authorization failed")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Authorization failed") from exc


@app.post("/v1/playback/heartbeat", tags=["playback"])
def playback_heartbeat(
    payload: SessionRequest,
    services: ServicesDependency,
) -> dict[str, Any]:
    """Extend active session lifetime via client heartbeat (M7)."""
    with database(services) as connection:
        return heartbeat_session(
            session_id=payload.session_id,
            connection=connection,
            ttl_seconds=services.settings.playback_token_ttl_seconds,
        )


@app.post("/v1/playback/stop", tags=["playback"])
def playback_stop(
    payload: SessionRequest,
    services: ServicesDependency,
) -> dict[str, Any]:
    """Stop an active playback session (M7)."""
    with database(services) as connection:
        return stop_session(session_id=payload.session_id, connection=connection)


@app.post("/v1/admin/sync/dispatcharr", tags=["admin"])
def sync_from_dispatcharr(services: ServicesDependency, principal: dict = Depends(require_admin)) -> dict[str, Any]:
    """Synchronize channels, VOD, stream mappings, and EPG from Dispatcharr (M4)."""
    try:
        with database(services) as connection:
            result = services.sync.sync_all(connection)
            # Invalidate EPG cache after sync
            services.invalidate_epg_cache()
            return result
    except Exception as exc:
        logger.exception("Dispatcharr sync failed")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Dispatcharr sync failed: {exc}",
        ) from exc


@app.get("/v1/admin/sync/dispatcharr", tags=["admin"])
def last_dispatcharr_sync(services: ServicesDependency, principal: dict = Depends(require_admin)) -> dict[str, Any]:
    summary = services.sync.last_sync()
    if summary is None:
        return {"status": "never", "detail": "No Dispatcharr sync has been recorded yet"}
    return summary


@app.post("/v1/playback/movie/{movie_id}", tags=["playback"])
def playback_movie(
    movie_id: int,
    services: ServicesDependency,
    authorization: str | None = Header(None),
) -> dict[str, Any]:
    """Authorize a movie playback stream with JWT authentication (M12).

    With AUTH_OPEN_ACCESS enabled the seeded guest identity is used when no
    valid Bearer token is supplied (free launch, no login required).
    """
    try:
        profile_id, device_key = resolve_playback_identity(None, services, authorization)
        with database(services) as connection:
            result = authorize_movie_playback(
                profile_id=profile_id,
                device_key=device_key,
                movie_id=movie_id,
                connection=connection,
                settings=services.settings,
            )
        prometheus_metrics.observe_playback_session("movie")
        return result
    except HTTPException:
        raise
    except ValueError as exc:
        logger.exception("Movie playback authorization failed - invalid token")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token") from exc
    except Exception as exc:
        logger.exception("Movie playback authorization failed")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Authorization failed") from exc


@app.post("/v1/playback/episode/{episode_id}", tags=["playback"])
def playback_episode(
    episode_id: int,
    services: ServicesDependency,
    authorization: str | None = Header(None),
) -> dict[str, Any]:
    """Authorize an episode playback stream with JWT authentication (M12).

    With AUTH_OPEN_ACCESS enabled the seeded guest identity is used when no
    valid Bearer token is supplied (free launch, no login required).
    """
    try:
        profile_id, device_key = resolve_playback_identity(None, services, authorization)
        with database(services) as connection:
            result = authorize_episode_playback(
                profile_id=profile_id,
                device_key=device_key,
                episode_id=episode_id,
                connection=connection,
                settings=services.settings,
            )
        prometheus_metrics.observe_playback_session("episode")
        return result
    except HTTPException:
        raise
    except ValueError as exc:
        logger.exception("Episode playback authorization failed - invalid token")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token") from exc
    except Exception as exc:
        logger.exception("Episode playback authorization failed")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Authorization failed") from exc


@app.get("/v1/media/verify", tags=["media"])
def verify_media_token(token: str, services: ServicesDependency) -> dict[str, Any]:
    """Verify a playback token for media-layer authorization (M7)."""
    with database(services) as connection:
        session_info = verify_playback_token(token, connection, services.settings.playback_token_secret)
        return {
            "valid": True,
            "session_id": session_info["session_id"],
            "profile_id": session_info["profile_id"],
            "device_id": session_info["device_id"],
        }


@app.get("/v1/dispatcharr/health", tags=["operations"])
def dispatcharr_health(services: ServicesDependency) -> dict[str, Any]:
    """Check Dispatcharr version probe (M4)."""
    try:
        data = services.dispatcharr.get_version()
        version = data.get("version")
        expected = services.settings.dispatcharr_expected_version
        return {
            "status": "ok" if version == expected else "version_mismatch",
            "version": version,
            "expected_version": expected,
            "authenticated": bool(services.settings.dispatcharr_api_key),
        }
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Dispatcharr unreachable: {exc}",
        ) from exc


@app.get("/v1/ome/health", tags=["operations"])
def ome_health(services: ServicesDependency) -> dict[str, Any]:
    """Check OvenMediaEngine health probe (M6)."""
    return services.ome.get_health()
