import asyncio
import logging
from contextlib import asynccontextmanager, contextmanager
from typing import Annotated, Any
import time

from fastapi import Depends, FastAPI, HTTPException, Request, status, Header
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .config import get_settings
from .playback import authorize_live_playback, authorize_movie_playback, authorize_episode_playback, heartbeat_session, stop_session, generate_auth_token, verify_playback_token
from .services import Services
from .auth.router import router as auth_router
from .devices.router import router as devices_router
from .auth.service import AuthService
from .auth.deps import current_principal, require_admin
from .catalog.service import CatalogService

logging.basicConfig(level=get_settings().log_level, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("tavuno-control")

JWT_EXPIRATION_HOURS = 24


@asynccontextmanager
async def lifespan(app: FastAPI):
    if getattr(app.state, "services", None) is None:
        app.state.services = Services(get_settings())
    services: Services = app.state.services
    interval = services.settings.dispatcharr_sync_interval_seconds
    stop = asyncio.Event()

    async def periodic_dispatcharr_sync() -> None:
        if interval <= 0:
            logger.info("Dispatcharr periodic sync disabled")
            return
        if not services.settings.dispatcharr_api_key:
            logger.warning("Dispatcharr periodic sync skipped: DISPATCHARR_API_KEY is not set")
            return
        logger.info("Dispatcharr periodic sync enabled every %s seconds", interval)
        while not stop.is_set():
            try:
                with services.connection() as connection:
                    summary = services.sync.sync_all(connection)
                logger.info("Dispatcharr periodic sync complete: %s", summary)
            except Exception:
                logger.exception("Dispatcharr periodic sync failed")
            try:
                await asyncio.wait_for(stop.wait(), timeout=interval)
            except TimeoutError:
                continue

    task = asyncio.create_task(periodic_dispatcharr_sync())
    yield
    stop.set()
    task.cancel()
    try:
        await task
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

# Include routers
app.include_router(auth_router)
app.include_router(devices_router)


def get_services(request: Request) -> Services:
    if getattr(request.app.state, "services", None) is None:
        request.app.state.services = Services(get_settings())
    return request.app.state.services


ServicesDependency = Annotated[Services, Depends(get_services)]


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


@app.get("/v1/home", tags=["catalog"])
def home(services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    catalog = CatalogService(services)
    return catalog.get_home()


@app.get("/v1/channels", tags=["catalog"])
def list_channels(services: ServicesDependency, category_id: int | None = None, principal: dict = Depends(current_principal)) -> list[dict[str, Any]]:
    catalog = CatalogService(services)
    channels = catalog.get_channels(category_id=category_id)
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
    categories = catalog.get_categories(kind=kind)
    return [category.model_dump() for category in categories]


@app.get("/v1/categories/{category_id}", tags=["catalog"])
def get_category(category_id: int, services: ServicesDependency, principal: dict = Depends(current_principal)) -> dict[str, Any]:
    catalog = CatalogService(services)
    category = catalog.get_category(category_id)
    if category is None:
        raise HTTPException(status_code=404, detail="Category not found")
    return category.model_dump()


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
    with database(services) as connection:
        programmes = connection.execute(
            """
            SELECT p.id, p.title, p.starts_at, p.ends_at, p.description
            FROM tavuno_epg_programmes p
            JOIN tavuno_epg_channels e ON e.id = p.epg_channel
            WHERE e.channel = %s AND p.ends_at >= NOW()
            ORDER BY p.starts_at ASC
            LIMIT 3
            """,
            (channel_id,),
        ).fetchall()

    return {
        "channel_id": channel_id,
        "now": programmes[0] if len(programmes) > 0 else None,
        "next": programmes[1] if len(programmes) > 1 else None,
        "later": programmes[2] if len(programmes) > 2 else None,
    }


class SessionRequest(BaseModel):
    session_id: int


@app.post("/v1/playback/live/{channel_id}", tags=["playback"])
def playback_live(
    channel_id: int,
    services: ServicesDependency,
    authorization: str | None = Header(None),
) -> dict[str, Any]:
    """Authorize a live playback stream with JWT authentication (M7)."""
    if not authorization:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authorization header required")
    
    # Extract Bearer token
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authorization header")
    token = authorization.replace("Bearer ", "")
    
    try:
        # Use AuthService to verify token and get profile
        auth_service = AuthService(services)
        auth_data = auth_service.get_profile_from_token(token)
        profile_id = auth_data["profile_id"]
        device_id = auth_data["device_id"]

        # Get device_key from database
        with database(services) as connection:
            device = connection.execute(
                "SELECT device_key FROM tavuno_devices WHERE id = %s AND profile = %s",
                (device_id, profile_id),
            ).fetchone()
            if not device:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")

            device_key = device["device_key"]

            return authorize_live_playback(
                profile_id=profile_id,
                device_key=device_key,
                channel_id=channel_id,
                connection=connection,
                settings=services.settings,
            )
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
    """Authorize a movie playback stream with JWT authentication (M12)."""
    if not authorization:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authorization header required")
    
    # Extract Bearer token
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authorization header")
    token = authorization.replace("Bearer ", "")
    
    try:
        # Use AuthService to verify token and get profile
        auth_service = AuthService(services)
        auth_data = auth_service.get_profile_from_token(token)
        profile_id = auth_data["profile_id"]
        device_id = auth_data["device_id"]

        # Get device_key from database
        with database(services) as connection:
            device = connection.execute(
                "SELECT device_key FROM tavuno_devices WHERE id = %s AND profile = %s",
                (device_id, profile_id),
            ).fetchone()
            if not device:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")

            device_key = device["device_key"]

            return authorize_movie_playback(
                profile_id=profile_id,
                device_key=device_key,
                movie_id=movie_id,
                connection=connection,
                settings=services.settings,
            )
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
    """Authorize an episode playback stream with JWT authentication (M12)."""
    if not authorization:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authorization header required")
    
    # Extract Bearer token
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid authorization header")
    token = authorization.replace("Bearer ", "")
    
    try:
        # Use AuthService to verify token and get profile
        auth_service = AuthService(services)
        auth_data = auth_service.get_profile_from_token(token)
        profile_id = auth_data["profile_id"]
        device_id = auth_data["device_id"]

        # Get device_key from database
        with database(services) as connection:
            device = connection.execute(
                "SELECT device_key FROM tavuno_devices WHERE id = %s AND profile = %s",
                (device_id, profile_id),
            ).fetchone()
            if not device:
                raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")

            device_key = device["device_key"]

            return authorize_episode_playback(
                profile_id=profile_id,
                device_key=device_key,
                episode_id=episode_id,
                connection=connection,
                settings=services.settings,
            )
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
