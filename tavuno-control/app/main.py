import logging
from contextlib import contextmanager
from typing import Annotated, Any

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from .config import get_settings
from .services import Services

logging.basicConfig(level=get_settings().log_level, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("tavuno-control")

app = FastAPI(title="Tavuno Control API", version="0.1.0", openapi_url="/openapi.json", docs_url="/docs")
app.state.services = Services(get_settings())


def get_services(request: Request) -> Services:
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
    return {"status": "ok", "services": services.health()}


@app.get("/v1/home", tags=["catalog"])
def home(services: ServicesDependency) -> dict[str, Any]:
    with database(services) as connection:
        categories = connection.execute(
            "SELECT id, name, kind FROM tavuno_categories WHERE is_active = TRUE ORDER BY sort_order, name LIMIT 12"
        ).fetchall()
        channels = connection.execute(
            "SELECT id, name, slug FROM tavuno_channels WHERE is_active = TRUE ORDER BY name LIMIT 12"
        ).fetchall()
    return {"categories": categories, "featured_channels": channels}


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


def list_catalog(collection: str, services: Services, category_id: int | None = None) -> list[dict[str, Any]]:
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
    return list_catalog("tavuno_movies", services, category_id)


@app.get("/v1/series", tags=["catalog"])
def series(services: ServicesDependency, category_id: int | None = None) -> list[dict[str, Any]]:
    return list_catalog("tavuno_series", services, category_id)


@app.get("/v1/sports", tags=["catalog"])
def sports(services: ServicesDependency) -> list[dict[str, Any]]:
    with database(services) as connection:
        return connection.execute(
            "SELECT id, name, kind FROM tavuno_categories WHERE is_active = TRUE AND kind = 'sports' ORDER BY sort_order, name"
        ).fetchall()
