"""Normalize provider asset references into fetchable URLs.

Directus stores an uploaded image as a **relation to a row in
``directus_files``**, so every artwork column that the schema documents as
"UUID as string" (``tavuno_channels.logo``, ``tavuno_movies.poster`` /
``backdrop``, ``tavuno_seasons.poster``, ``tavuno_episodes.thumbnail``,
``tavuno_profiles.avatar``) actually holds a bare file UUID. A UUID is not a
URL: handing one to an image loader produces a request for a relative path and a
silent empty tile, which is exactly the placeholder state the Android app has
been rendering since the columns landed.

The mapping is Directus' own convention — a file UUID is served from
``/assets/<uuid>`` — so this module only has to recognise the shape and prefix
it. It deliberately does **not** do a lookup per id: a catalog read of 12
posters must not become 12 round trips to Directus, and a UUID that no longer
resolves is the same UX as artwork that was never uploaded (a placeholder),
so a stale id costs nothing to leave as a URL.

Everything here is a pure function over strings, which is what makes the
"is this already a URL?" rules testable without a database or an HTTP client.
"""

from __future__ import annotations

import re
from urllib.parse import urlsplit
from uuid import UUID

# Directus file ids are UUIDs. Matched loosely on purpose (hex + dashes, any
# length) rather than to a strict RFC 4122 pattern: the point is to tell "bare
# identifier" apart from "URL", not to validate an identifier's version bits.
_UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

# Schemes that make a value self-addressing, so it must never be prefixed.
_ABSOLUTE_SCHEMES = ("http://", "https://", "//", "data:", "file:", "asset://", "content://")


def is_uuid(value: object) -> bool:
    """True when ``value`` looks like a bare Directus file UUID.

    Accepts :class:`uuid.UUID` as well as ``str`` because that is what psycopg
    actually hands back for a ``uuid`` column — the columns documented as "UUID
    as string" are real Postgres ``uuid`` types, so a value read straight from
    the database is a ``UUID`` instance, never a ``str``.
    """
    if isinstance(value, UUID):
        return True
    return isinstance(value, str) and bool(_UUID_RE.match(value.strip()))


def is_url(value: object) -> bool:
    """True when ``value`` is already addressable and must be passed through."""
    if isinstance(value, UUID):
        return False
    if not isinstance(value, str):
        return False
    return value.strip().lower().startswith(_ABSOLUTE_SCHEMES)


def asset_base_url(base_url: str | None) -> str:
    """Normalize a configured Directus base into the prefix assets hang off.

    Accepts the shapes an operator is likely to paste from the Directus UI —
    ``https://host/directus``, ``https://host/directus/``, or the bare host —
    and always returns ``<origin>/<app-path>/assets`` with no trailing slash.
    An unset/blank value yields ``""``, which makes :func:`asset_url` return the
    raw stored value instead of a broken relative URL.
    """
    if not isinstance(base_url, str):
        return ""
    trimmed = base_url.strip().rstrip("/")
    if not trimmed:
        return ""
    # A bare "host:port" or a bare origin still needs a scheme before urlsplit
    # can find its netloc; without this, urlsplit("directus:8055").netloc is
    # "" and everything downstream degrades to a relative path.
    candidate = trimmed if "//" in trimmed else f"https://{trimmed}"
    parts = urlsplit(candidate)
    if not parts.scheme or not parts.netloc:
        return ""
    return f"{parts.scheme}://{parts.netloc}{parts.path}/assets"


def _as_text(value: object) -> str | None:
    """Coerce a stored reference to text, or ``None`` when it is not usable.

    ``uuid.UUID`` is spelled out because that is what a Postgres ``uuid`` column
    yields. Rejecting it would silently drop every real artwork reference in the
    database while still passing a unit test that feeds plain strings — which is
    exactly what happened before this was fixed.
    """
    if isinstance(value, UUID):
        return str(value)
    if not isinstance(value, str):
        return None
    text = value.strip()
    return text or None


def asset_url(value: object, base_url: str | None) -> str | None:
    """Turn a stored asset reference into a URL the client can fetch.

    * ``None`` / blank -> ``None`` (no artwork; the client draws a placeholder)
    * already a URL  -> passed through untouched
    * bare UUID      -> ``<directus_base>/assets/<uuid>``
    * anything else  -> passed through untouched, so a hand-entered relative
      path in the admin UI survives instead of being silently mangled.
    """
    text = _as_text(value)
    if text is None:
        return None
    if is_url(text):
        return text
    base = asset_base_url(base_url)
    if base and is_uuid(text):
        return f"{base}/{text}"
    return text