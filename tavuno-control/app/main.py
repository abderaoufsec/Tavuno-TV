import asyncio
import logging
from contextlib import asynccontextmanager, contextmanager
from typing import Annotated, Any

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .config import get_settings
from .playback import authorize_live_playback, heartbeat_session, stop_session
from .services import Services

logging.basicConfig(level=get_settings().log_level, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("tavuno-control")

CATALOG_COLLECTIONS = {
    "movies": "tavuno_movies",
    "series": "tavuno_series",
}


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
def home(services: ServicesDependency) -> dict[str, Any]:
    def load() -> dict[str, Any]:
        with database(services) as connection:
            categories = connection.execute(
                "SELECT id, name, kind FROM tavuno_categories WHERE is_active = TRUE ORDER BY sort_order, name LIMIT 12"
            ).fetchall()
            channels = connection.execute(
                "SELECT id, name, slug FROM tavuno_channels WHERE is_active = TRUE ORDER BY name LIMIT 12"
            ).fetchall()
        return {"categories": categories, "featured_channels": channels}

    return services.cached_json("home", load)


@app.get("/v1/channels", tags=["catalog"])
def list_channels(services: ServicesDependency, category_id: int | None = None) -> list[dict[str, Any]]:
    query = "SELECT id, name, slug, category, is_active FROM tavuno_channels WHERE is_active = TRUE"
    parameters: tuple[Any, ...] = ()
    if category_id is not None:
        query += " AND category = %s"
        parameters = (category_id,)
    query += " ORDER BY name"
    with database(services) as connection:
        return connection.execute(query, parameters).fetchall()


@app.get("/v1/channels/{channel_id}", tags=["catalog"])
def get_channel(channel_id: int, services: ServicesDependency) -> dict[str, Any]:
    with database(services) as connection:
        channel = connection.execute(
            "SELECT id, name, slug, category, is_active FROM tavuno_channels WHERE id = %s AND is_active = TRUE",
            (channel_id,),
        ).fetchone()
        if channel is None:
            raise HTTPException(status_code=404, detail="Channel not found")
        sources = connection.execute(
            """
            SELECT provider, external_id, priority, is_active
            FROM tavuno_channel_sources
            WHERE channel = %s
            ORDER BY priority, id
            """,
            (channel_id,),
        ).fetchall()
    channel["sources"] = sources
    return channel


class DeviceRegistration(BaseModel):
    profile_id: int
    name: str = Field(min_length=1, max_length=120)
    device_key: str = Field(min_length=8, max_length=255)
    platform: str = Field(min_length=2, max_length=48)


@app.post("/v1/devices/register", status_code=status.HTTP_201_CREATED, tags=["devices"])
def register_device(payload: DeviceRegistration, services: ServicesDependency) -> dict[str, Any]:
    with database(services) as connection:
        profile = connection.execute("SELECT id FROM tavuno_profiles WHERE id = %s AND status = 'active'", (payload.profile_id,)).fetchone()
        if profile is None:
            raise HTTPException(status_code=404, detail="Active profile not found")
        device = connection.execute(
            """
            INSERT INTO tavuno_devices (profile, name, device_key, platform, is_active, last_seen_at)
            VALUES (%s, %s, %s, %s, TRUE, NOW())
            ON CONFLICT (device_key) DO UPDATE SET name = EXCLUDED.name, platform = EXCLUDED.platform,
              last_seen_at = NOW(), is_active = TRUE
            RETURNING id, profile, name, device_key, platform, is_active, last_seen_at
            """,
            (payload.profile_id, payload.name, payload.device_key, payload.platform),
        ).fetchone()
        connection.commit()
    return device


@app.get("/v1/epg", tags=["epg"])
def epg(services: ServicesDependency, channel_id: int | None = None) -> list[dict[str, Any]]:
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
        return connection.execute(query, parameters).fetchall()


def list_catalog(collection_key: str, services: Services, category_id: int | None = None) -> list[dict[str, Any]]:
    collection = CATALOG_COLLECTIONS.get(collection_key)
    if collection is None:
        raise HTTPException(status_code=400, detail="Unknown catalog collection")
    query = f"SELECT id, title, slug, category FROM {collection} WHERE is_active = TRUE"
    parameters: tuple[Any, ...] = ()
    if category_id is not None:
        query += " AND category = %s"
        parameters = (category_id,)
    query += " ORDER BY title"
    with database(services) as connection:
        return connection.execute(query, parameters).fetchall()


@app.get("/v1/movies", tags=["catalog"])
def movies(services: ServicesDependency, category_id: int | None = None) -> list[dict[str, Any]]:
    return list_catalog("movies", services, category_id)


@app.get("/v1/series", tags=["catalog"])
def series(services: ServicesDependency, category_id: int | None = None) -> list[dict[str, Any]]:
    return list_catalog("series", services, category_id)


@app.get("/v1/sports", tags=["catalog"])
def sports(services: ServicesDependency) -> list[dict[str, Any]]:
    with database(services) as connection:
        return connection.execute(
            "SELECT id, name, kind FROM tavuno_categories WHERE is_active = TRUE AND kind = 'sports' ORDER BY sort_order, name"
        ).fetchall()


@app.get("/v1/epg/channel/{channel_id}/now-next", tags=["epg"])
def channel_epg_now_next(channel_id: int, services: ServicesDependency) -> dict[str, Any]:
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


class LivePlaybackRequest(BaseModel):
    profile_id: int
    device_key: str = Field(min_length=8, max_length=255)


class SessionRequest(BaseModel):
    session_id: int


@app.post("/v1/playback/live/{channel_id}", tags=["playback"])
def playback_live(
    channel_id: int,
    payload: LivePlaybackRequest,
    services: ServicesDependency,
) -> dict[str, Any]:
    """Authorize a live playback stream, enforce limits, and mint temporary token (M7)."""
    with database(services) as connection:
        return authorize_live_playback(
            profile_id=payload.profile_id,
            device_key=payload.device_key,
            channel_id=channel_id,
            connection=connection,
            settings=services.settings,
        )


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
def sync_from_dispatcharr(services: ServicesDependency) -> dict[str, Any]:
    """Synchronize channels, VOD, stream mappings, and EPG from Dispatcharr (M4)."""
    try:
        with database(services) as connection:
            return services.sync.sync_all(connection)
    except Exception as exc:
        logger.exception("Dispatcharr sync failed")
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Dispatcharr sync failed: {exc}",
        ) from exc


@app.get("/v1/admin/sync/dispatcharr", tags=["admin"])
def last_dispatcharr_sync(services: ServicesDependency) -> dict[str, Any]:
    summary = services.sync.last_sync()
    if summary is None:
        return {"status": "never", "detail": "No Dispatcharr sync has been recorded yet"}
    return summary


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
