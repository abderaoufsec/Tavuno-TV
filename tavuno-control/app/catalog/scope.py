"""Live-channel test-scope helpers.

Free-launch testing needs a handful of verified-working channels instead of the
full synced catalog. These helpers translate the settings into an *additive* SQL
filter so the underlying data is never mutated and lifting the test scope is a
config change (empty allowlist + limit 0 = no filter).

They live in their own module because two readers need them — the catalog
(``/v1/channels``, ``/v1/home``, ``/v1/search``) and the EPG guide
(``/v1/epg/window``). Keeping one implementation is what stops the guide from
advertising a channel the channel list refuses to show.
"""

from typing import Any, List


def scope_text(value: Any) -> str:
    """Coerce a settings value to text; non-strings (e.g. test doubles) -> ""."""
    return value.strip() if isinstance(value, str) else ""


def scope_int(value: Any) -> int:
    """Coerce a settings value to int; anything unusable -> 0."""
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def live_channel_scope(settings: Any) -> tuple:
    """Return an extra WHERE fragment + params restricting live channels.

    The allowlist accepts numeric channel IDs and/or slugs, comma separated.
    Returns ``("", [])`` when no allowlist is configured.

    The returned clause always begins with `` AND `` because callers append it to
    an existing predicate rather than replacing one.
    """
    allowlist = scope_text(getattr(settings, "live_channel_allowlist", ""))
    if not allowlist:
        return "", []

    ids: List[int] = []
    slugs: List[str] = []
    for token in (part.strip() for part in allowlist.split(",")):
        if not token:
            continue
        if token.lstrip("+-").isdigit():
            ids.append(int(token))
        else:
            slugs.append(token)

    clauses: List[str] = []
    params: List[Any] = []
    if ids:
        clauses.append("id = ANY(%s)")
        params.append(ids)
    if slugs:
        clauses.append("slug = ANY(%s)")
        params.append(slugs)
    if not clauses:
        return "", []
    return " AND (" + " OR ".join(clauses) + ")", params


def live_channel_limit(settings: Any) -> int:
    """Cap on how many live channels are listed; 0 means unlimited."""
    return max(0, scope_int(getattr(settings, "live_channel_limit", 0)))