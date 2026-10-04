from functools import lru_cache
import logging
from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger("tavuno-control.config")

# Secrets that ship with working defaults so a fresh clone runs without setup.
# Each is a published constant, so its value outside development is a real
# exposure rather than a cosmetic one. Checked at startup so an operator finds
# out from the log rather than from an incident.
#
# These five are exactly the compose fallbacks listed in
# docs/MASTER_IMPLEMENTATION_PLAN.md §F-2; milestones.md says "four", it is five.
_INSECURE_DEFAULT_SECRETS: dict[str, str] = {
    "jwt_secret": "tavuno-jwt-secret-key-change-in-production",
    "playback_token_secret": "tavuno-playback-secret-key",
    "ops_token": "tavuno-ops-local",
    "ome_api_token": "tavuno-m1-local",
    "auth_guest_device_key": "tavuno-tv-guest",
}

# Environments where the defaults are expected and correct.
_DEV_ENVIRONMENTS = {"development", "dev", "local", "test"}

# Environments where a published default is an incident, not a smell.
_PRODUCTION_ENVIRONMENTS = {"production", "prod"}


def _is_published_default(value: object, published: str) -> bool:
    """True when `value` is the published constant, including a decorated copy.

    The deployed .env carries JWT_SECRET as the default with a reassuring suffix
    appended ("...change-in-production-use-random-64-hex-in-production", 74 chars
    against the 42-char constant). An equality check misses that entirely while
    the value is still readable out of the repository, so match the prefix too.
    """
    if not isinstance(value, str):
        return False
    return value == published or value.startswith(published)


def _is_placeholder(value: object) -> bool:
    """True for the CHANGE_ME placeholders .env.example ships.

    An operator who copies the example and forgets to fill one in would
    otherwise deploy a secret that anyone who has read the repository can
    guess, which is the same failure the published constants cause.
    """
    return isinstance(value, str) and value.strip().upper().startswith("CHANGE_ME")


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

    # Schema management. ``app/db_migrations.py`` applies the SQL files in
    # ``tavuno-control/migrations`` in filename order and records each one in the
    # ``tavuno_schema_migrations`` ledger, so a file is executed exactly once.
    #   TAVUNO_AUTO_MIGRATE = true  -> apply pending migrations during API startup
    #   false (default)             -> start without touching the schema
    # Every migration guards itself with IF NOT EXISTS / ON CONFLICT, so turning
    # this on against a database that was migrated by hand replays the set as
    # no-ops instead of failing.
    #
    # The set patches an existing schema rather than bootstrapping one: the
    # tables it alters and references (tavuno_profiles, tavuno_devices,
    # tavuno_plans, tavuno_categories, tavuno_channels, tavuno_channel_sources)
    # come from the Directus base schema applied by
    # tavuno-infra/scripts/apply-m2-schema.ps1. Against a database where that has
    # never run, 001_m9_auth_schema.sql fails with
    # relation "tavuno_profiles" does not exist. tests/test_migrations_live.py
    # pins both halves of that: the failure without the base schema, and a full
    # apply once it is in place.
    auto_migrate: bool = Field(default=False, validation_alias="TAVUNO_AUTO_MIGRATE")

    dispatcharr_url: str = Field(default="http://dispatcharr:9191", validation_alias="DISPATCHARR_URL")
    dispatcharr_api_key: str | None = Field(default=None, validation_alias="DISPATCHARR_API_KEY")
    dispatcharr_expected_version: str = Field(default="0.28.0", validation_alias="DISPATCHARR_EXPECTED_VERSION")
    dispatcharr_sync_interval_seconds: int = Field(default=3600, validation_alias="DISPATCHARR_SYNC_INTERVAL_SECONDS")
    dispatcharr_timeout_seconds: float = Field(default=20.0, validation_alias="DISPATCHARR_TIMEOUT_SECONDS")

    # Public Directus origin used to turn stored file UUIDs into fetchable asset
    # URLs (see app/assets.py). This is the address *clients* can reach, not the
    # compose-network one: on the emulator that is 10.0.2.2, in production it is
    # whatever Caddy serves /directus/ on. Left blank, artwork references are
    # returned untouched (placeholders) rather than rewritten to a path the
    # device cannot resolve.
    directus_url: str = Field(default="", validation_alias="DIRECTUS_PUBLIC_URL")

    session_reaper_interval_seconds: int = Field(default=60, validation_alias="SESSION_REAPER_INTERVAL_SECONDS")

    ome_api_url: str = Field(default="http://tavuno-ovenmediaengine:8081", validation_alias="OME_API_URL")
    ome_api_token: str | None = Field(default=None, validation_alias="OME_API_TOKEN")
    ome_playback_base_url: str = Field(default="http://localhost:8080/media", validation_alias="OME_PLAYBACK_BASE_URL")
    ome_dvr_max_duration_seconds: int = Field(default=3600, validation_alias="OME_DVR_MAX_DURATION_SECONDS")
    # Host used to replace localhost/127.0.0.1 in playback URLs for Android emulators/devices.
    playback_public_host: str | None = Field(default=None, validation_alias="PLAYBACK_PUBLIC_HOST")

    playback_token_secret: str = Field(default="tavuno-playback-secret-key", validation_alias="PLAYBACK_TOKEN_SECRET")
    playback_token_ttl_seconds: int = Field(default=120, validation_alias="PLAYBACK_TOKEN_TTL_SECONDS")

    jwt_secret: str = Field(default="tavuno-jwt-secret-key-change-in-production", validation_alias="JWT_SECRET")
    jwt_access_ttl_seconds: int = Field(default=900, validation_alias="JWT_ACCESS_TTL_SECONDS")
    jwt_refresh_ttl_seconds: int = Field(default=2592000, validation_alias="JWT_REFRESH_TTL_SECONDS")
    password_hash_scheme: str = Field(default="bcrypt", validation_alias="PASSWORD_HASH_SCHEME")
    free_launch: bool = Field(default=True, validation_alias="FREE_LAUNCH")
    default_plan_id: int | None = Field(default=None, validation_alias="DEFAULT_PLAN_ID")

    auth_open_access: bool = Field(default=False, validation_alias="AUTH_OPEN_ACCESS")
    auth_guest_email: str = Field(default="guest@tavuno.local", validation_alias="AUTH_GUEST_EMAIL")
    auth_guest_device_key: str = Field(default="tavuno-tv-guest", validation_alias="AUTH_GUEST_DEVICE_KEY")

    # Live-channel test scoping. Both knobs are additive filters applied when
    # the catalog is listed; the synced data is never touched, so lifting the
    # test scope is a config change rather than a re-sync.
    #   TAVUNO_LIVE_CHANNEL_ALLOWLIST = "6853,6854,8845"  -> only those channels
    #   TAVUNO_LIVE_CHANNEL_LIMIT     = 10                 -> first 10 by name
    # Empty allowlist + limit 0 exposes the whole synced catalog (production).
    live_channel_allowlist: str = Field(default="", validation_alias="TAVUNO_LIVE_CHANNEL_ALLOWLIST")
    live_channel_limit: int = Field(default=0, validation_alias="TAVUNO_LIVE_CHANNEL_LIMIT")

    # Email service (M8.5)
    email_enabled: bool = Field(default=False, validation_alias="EMAIL_ENABLED")
    email_from_address: str = Field(default="noreply@tavuno.com", validation_alias="EMAIL_FROM_ADDRESS")
    email_from_name: str = Field(default="Tavuno", validation_alias="EMAIL_FROM_NAME")
    email_smtp_host: str = Field(default="smtp.gmail.com", validation_alias="EMAIL_SMTP_HOST")
    email_smtp_port: int = Field(default=587, validation_alias="EMAIL_SMTP_PORT")
    email_smtp_username: str | None = Field(default=None, validation_alias="EMAIL_SMTP_USERNAME")
    email_smtp_password: str | None = Field(default=None, validation_alias="EMAIL_SMTP_PASSWORD")
    email_smtp_use_tls: bool = Field(default=True, validation_alias="EMAIL_SMTP_USE_TLS")
    email_base_url: str = Field(default="https://tavuno.com", validation_alias="EMAIL_BASE_URL")
    password_reset_ttl_seconds: int = Field(default=3600, validation_alias="PASSWORD_RESET_TTL_SECONDS")

    # Ops dashboard (M15). Read-only status endpoint gated by a shared secret
    # rather than by `require_admin`: in free-launch mode (AUTH_OPEN_ACCESS) the
    # seeded guest identity has role `user`, so every require_admin route
    # answers 403 while the app ships without login — gating a dashboard on a
    # login system that is deliberately off would make it unreachable.
    #
    # **Change this in production.** It ships as a working default so a fresh
    # clone has a usable dashboard, and the header is compared in constant time
    # (hmac.compare_digest) so it cannot be probed byte by byte.
    ops_token: str = Field(default="tavuno-ops-local", validation_alias="TAVUNO_OPS_TOKEN")

    @model_validator(mode="after")
    def _check_default_secrets(self) -> "Settings":
        """Handle published default secrets, tiered by environment.

        - ``_DEV_ENVIRONMENTS``: silent. A fresh clone has to run with no setup,
          and warning there would train people to ignore the message that matters.
        - any other non-production environment: warn, but still construct. An
          operator must be able to boot and read the log that explains what to
          fix; turning a configuration smell into an outage hides exactly that.
        - ``_PRODUCTION_ENVIRONMENTS``: refuse. This is the point of the check.
          With a published default live, anyone who has read the repository can
          mint valid JWTs, playback tokens, guest identity and ops access on the
          deployed instance, so starting would be the worse failure mode.
        """
        environment = (self.environment or "").strip().lower()
        if environment in _DEV_ENVIRONMENTS:
            return self

        offenders = [
            field_name
            for field_name, published in _INSECURE_DEFAULT_SECRETS.items()
            if _is_published_default(getattr(self, field_name, None), published)
            or _is_placeholder(getattr(self, field_name, None))
        ]
        if not offenders:
            return self

        names = ", ".join(name.upper() for name in offenders)
        if environment in _PRODUCTION_ENVIRONMENTS:
            raise ValueError(
                f"Refusing to start: {names} are still the published defaults "
                f"while TAVUNO_ENV={self.environment}. Generate one random value "
                "per secret before deploying, e.g. "
                "`python -c \"import secrets;print(secrets.token_hex(32))\"`."
            )

        for field_name in offenders:
            logger.warning(
                "SECURITY: %s is still the built-in default in a non-development "
                "environment (%s). Set it from the environment before exposing this "
                "service.",
                field_name.upper(),
                self.environment,
            )
        return self

    @property
    def postgres_dsn(self) -> str:
        return (
            f"postgresql://{self.postgres_user}:{self.postgres_password}"
            f"@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()
