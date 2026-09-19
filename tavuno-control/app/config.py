from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    environment: str = Field(default="development", validation_alias="TAVUNO_ENV")
    log_level: str = Field(default="INFO", validation_alias="TAVUNO_LOG_LEVEL")
    postgres_host: str = Field(default="postgres", validation_alias="POSTGRES_HOST")
    postgres_port: int = Field(default=5432, validation_alias="POSTGRES_PORT")
    postgres_db: str = Field(default="tavuno", validation_alias="POSTGRES_DB")
    postgres_user: str = Field(default="tavuno", validation_alias="POSTGRES_USER")
    postgres_password: str = Field(validation_alias="POSTGRES_PASSWORD")
    redis_host: str = Field(default="redis", validation_alias="REDIS_HOST")
    redis_port: int = Field(default=6379, validation_alias="REDIS_PORT")
    redis_password: str = Field(validation_alias="REDIS_PASSWORD")
    catalog_cache_seconds: int = Field(default=30, validation_alias="TAVUNO_CATALOG_CACHE_SECONDS")

    dispatcharr_url: str = Field(default="http://dispatcharr:9191", validation_alias="DISPATCHARR_URL")
    dispatcharr_api_key: str | None = Field(default=None, validation_alias="DISPATCHARR_API_KEY")
    dispatcharr_expected_version: str = Field(default="0.28.0", validation_alias="DISPATCHARR_EXPECTED_VERSION")
    dispatcharr_sync_interval_seconds: int = Field(default=3600, validation_alias="DISPATCHARR_SYNC_INTERVAL_SECONDS")
    dispatcharr_timeout_seconds: float = Field(default=20.0, validation_alias="DISPATCHARR_TIMEOUT_SECONDS")

    ome_api_url: str = Field(default="http://tavuno-ovenmediaengine:8081", validation_alias="OME_API_URL")
    ome_api_token: str | None = Field(default=None, validation_alias="OME_API_TOKEN")
    ome_playback_base_url: str = Field(default="http://localhost:8080/media", validation_alias="OME_PLAYBACK_BASE_URL")

    playback_token_secret: str = Field(default="tavuno-playback-secret-key", validation_alias="PLAYBACK_TOKEN_SECRET")
    playback_token_ttl_seconds: int = Field(default=120, validation_alias="PLAYBACK_TOKEN_TTL_SECONDS")

    @property
    def postgres_dsn(self) -> str:
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
