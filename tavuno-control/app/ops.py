"""Read-only operational data for the M15 ops dashboard.

Deliberately *read-only*: Directus remains the CRUD admin surface (this is the
decision recorded in ``docs/00_TavunoTV_Master_Strategy.md`` — "Directus Studio
initially, custom Tavuno Admin UI later where needed"). What was missing was
somewhere to see *state*: is the catalog populated, when did sync last run, how
many sessions are live, is the schema migrated. That is what this module
answers, and nothing here writes.

Why a token and not ``require_admin``
-------------------------------------
The admin dependency resolves the caller's role, and in free-launch mode
(``AUTH_OPEN_ACCESS``) the seeded guest identity has role ``user`` — so every
``require_admin`` route answers 403 while the app is shipping without login.
Gating a read-only dashboard on a login system that is deliberately turned off
would make it unreachable by construction. ``X-Ops-Token`` instead is a single
shared secret: no user model, no session, and it keeps working when
authentication returns.

Queries are per-area and independently guarded: a table that does not exist yet
(such as the migration ledger, before the first ``TAVUNO_AUTO_MIGRATE``) yields
``None`` rather than failing the whole snapshot.
"""

from __future__ import annotations

import hmac
import logging
from datetime import datetime, timezone
from typing import Any

logger = logging.getLogger("tavuno-control.ops")

# Shipped as a default so the dashboard works on a fresh clone, and called out
# in config.py as a value to override in production. A wrong token is a 401,
# compared in constant time so the header cannot be probed byte by byte.
DEFAULT_OPS_TOKEN = "tavuno-ops-local"

# Row counts are cheap and bounded (eight single-column COUNTs), so the page
# can poll without worrying about load. Each is isolated: one missing table
# must not blank the dashboard.
_COUNT_QUERIES: tuple[tuple[str, str], ...] = (
    ("channels", "SELECT COUNT(*) AS count FROM tavuno_channels"),
    ("active_channels", "SELECT COUNT(*) AS count FROM tavuno_channels WHERE is_active = TRUE"),
    ("categories", "SELECT COUNT(*) AS count FROM tavuno_categories"),
    ("movies", "SELECT COUNT(*) AS count FROM tavuno_movies"),
    ("series", "SELECT COUNT(*) AS count FROM tavuno_series"),
    ("episodes", "SELECT COUNT(*) AS count FROM tavuno_episodes"),
    ("epg_programmes", "SELECT COUNT(*) AS count FROM tavuno_epg_programmes"),
    ("profiles", "SELECT COUNT(*) AS count FROM tavuno_profiles"),
)


def token_matches(expected: str, supplied: str | None) -> bool:
    """Constant-time token comparison.

    ``hmac.compare_digest`` rather than ``==``: a plain equality on a header an
    attacker controls leaks matching prefix length through timing.
    """
    if not expected:
        return False
    return hmac.compare_digest(expected.encode("utf-8"), (supplied or "").encode("utf-8"))


class OpsService:
    """Read-only assembly of the dashboard snapshot."""

    def __init__(self, services: Any):
        self.services = services

    def snapshot(self) -> dict[str, Any]:
        """Every section, degraded independently rather than all-or-nothing."""
        return {
            "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "health": self._health(),
            "sync": self._sync(),
            "counts": self._counts(),
            "playback": self._playback(),
            "migrations": self._migrations(),
            "scope": self._scope(),
        }

    def _health(self) -> dict[str, Any] | None:
        """Dependency reachability, reusing the existing ``/health`` probe."""
        try:
            return self.services.health()
        except Exception as exc:
            # A dependency being down is exactly what this page exists to
            # show, so report it as data rather than failing the request.
            logger.debug("ops health probe failed", exc_info=True)
            return {"error": str(exc)}

    def _sync(self) -> dict[str, Any]:
        try:
            summary = self.services.sync.last_sync()
        except Exception:
            summary = None
        if not summary:
            return {"status": "never", "detail": "no successful sync recorded"}
        return {
            "status": summary.get("status", "unknown"),
            "synced_at": summary.get("synced_at"),
            "dispatcharr_version": summary.get("dispatcharr_version"),
            "channels_synced": summary.get("channels_synced"),
        }

    def _counts(self) -> dict[str, Any]:
        counts: dict[str, Any] = {}
        try:
            with self.services.connection() as connection:
                for key, query in _COUNT_QUERIES:
                    try:
                        row = connection.execute(query).fetchone()
                        counts[key] = (
                            (row["count"] if isinstance(row, dict) else row[0]) if row else None
                        )
                    except Exception:
                        counts[key] = None
        except Exception:
            logger.debug("ops count query failed", exc_info=True)
        return counts

    def _playback(self) -> dict[str, Any]:
        """Live session count.

        Same predicate as ``playback.py`` and ``app/metrics.py`` — three
        readers, one definition, or the dashboard would eventually disagree
        with the enforcement it is describing.
        """
        try:
            with self.services.connection() as connection:
                row = connection.execute(
                    "SELECT COUNT(*) AS count FROM tavuno_playback_sessions "
                    "WHERE status = 'active' AND last_seen_at >= NOW() - INTERVAL '90 SECONDS'"
                ).fetchone()
            if row is None:
                return {"active_sessions": None}
            return {"active_sessions": (row["count"] if isinstance(row, dict) else row[0])}
        except Exception:
            logger.debug("ops playback query failed", exc_info=True)
            return {"active_sessions": None}

    def _migrations(self) -> dict[str, Any]:
        """Ledger state, or ``None`` before the first auto-migrate run."""
        try:
            with self.services.connection() as connection:
                rows = connection.execute(
                    "SELECT filename FROM tavuno_schema_migrations ORDER BY filename"
                ).fetchall()
        except Exception:
            # The ledger table does not exist until TAVUNO_AUTO_MIGRATE runs
            # once. That is a legitimate state, not an error.
            return {"applied": None, "latest": None}
        filenames = [row["filename"] if isinstance(row, dict) else row[0] for row in rows]
        return {"applied": len(filenames), "latest": filenames[-1] if filenames else None}

    def _scope(self) -> dict[str, Any]:
        """The live-channel test scope, so the dashboard explains an empty
        catalog instead of leaving someone to guess at it."""
        settings = getattr(self.services, "settings", None)
        allowlist = (getattr(settings, "live_channel_allowlist", "") or "").strip()
        return {
            "allowlist": allowlist,
            "limit": getattr(settings, "live_channel_limit", 0) or 0,
            "restricted": bool(allowlist) or (getattr(settings, "live_channel_limit", 0) or 0) > 0,
        }


# The page is server-rendered once and drives itself from /v1/ops/summary.
# No build step and no framework on purpose: this repository has no frontend
# toolchain, and adding one to display eight numbers would be a poor trade.
#
# The token is deliberately *not* rendered into this HTML. The page reads it
# from sessionStorage and sends it as a header, so the secret never appears in
# the document, in a URL, or in a server access log.
OPS_HTML = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Tavuno Ops</title>
<style>
  :root { color-scheme: dark; }
  body { font: 15px/1.5 ui-monospace, SFMono-Regular, Menlo, monospace; margin: 0;
         background: #14181d; color: #e6edf3; }
  header { padding: 18px 22px; border-bottom: 1px solid #2a313a; display: flex;
           gap: 14px; align-items: center; flex-wrap: wrap; }
  h1 { font-size: 17px; margin: 0; letter-spacing: .04em; }
  section { padding: 16px 22px; border-bottom: 1px solid #1e242b; }
  h2 { font-size: 12px; margin: 0 0 10px; text-transform: uppercase;
       letter-spacing: .12em; color: #8b98a5; }
  table { border-collapse: collapse; width: 100%; max-width: 760px; }
  td { padding: 5px 10px 5px 0; vertical-align: top; }
  td:first-child { color: #8b98a5; width: 42%; }
  .ok   { color: #56d364; }
  .bad  { color: #ff7b72; }
  .warn { color: #e3b341; }
  .muted{ color: #6e7b8a; }
  input, button { font: inherit; background: #1c232b; color: #e6edf3;
                  border: 1px solid #303841; border-radius: 6px; padding: 6px 9px; }
  button { cursor: pointer; }
  #status { color: #8b98a5; font-size: 13px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
          gap: 10px 18px; max-width: 820px; }
  .cell b { display: block; font-size: 22px; font-weight: 600; }
  .cell span { color: #8b98a5; font-size: 12px; }
</style>
</head>
<body>
<header>
  <h1>TAVUNO OPS</h1>
  <input id="token" type="password" placeholder="X-Ops-Token" size="22" autocomplete="off">
  <button id="connect">Connect</button>
  <span id="status">not connected</span>
</header>
<div id="app"></div>
<script>
const KEY = "tavuno.ops.token";
const $ = (s) => document.querySelector(s);
$("#token").value = sessionStorage.getItem(KEY) || "";

function row(k, v, cls) {
  return `<tr><td>${k}</td><td class="${cls || ""}">${v === null || v === undefined ? "&mdash;" : v}</td></tr>`;
}
function block(title, rows) { return `<section><h2>${title}</h2><table>${rows}</table></section>`; }
function cell(v, label, cls) { return `<div class="cell"><b class="${cls || ""}">${v}</b><span>${label}</span></div>`; }

async function load() {
  const token = $("#token").value.trim();
  sessionStorage.setItem(KEY, token);
  let res;
  try {
    res = await fetch("/v1/ops/summary", { headers: { "X-Ops-Token": token } });
  } catch (e) {
    $("#status").textContent = "unreachable: " + e.message;
    return;
  }
  if (res.status === 401 || res.status === 404) {
    $("#status").textContent = "rejected (" + res.status + ") — check the token";
    $("#app").innerHTML = "";
    return;
  }
  if (!res.ok) { $("#status").textContent = "error " + res.status; return; }
  const d = await res.json();
  $("#status").textContent = "updated " + d.generated_at;

  const health = d.health && !d.health.error
    ? Object.entries(d.health).map(([k, v]) => row(k, v, String(v).startsWith("ok") ? "ok" : "warn")).join("")
    : row("error", (d.health || {}).error || "unavailable", "bad");

  const counts = d.counts || {};
  const scopeNote = d.scope && d.scope.restricted
    ? `<div class="muted" style="margin-top:8px">Counts below are raw database totals.`
      + ` The API is currently scoped to the live-channel test set`
      + ` (${d.scope.limit || 0} max${d.scope.allowlist ? "; allowlist set" : ""}),`
      + ` so the app serves fewer channels than shown here.</div>` : "";

  $("#app").innerHTML =
    `<section><h2>Catalog</h2><div class="grid">` +
      cell(counts.channels ?? "&mdash;", "channels") +
      cell(counts.active_channels ?? "&mdash;", "active channels") +
      cell(counts.categories ?? "&mdash;", "categories") +
      cell(counts.movies ?? "&mdash;", "movies") +
      cell(counts.series ?? "&mdash;", "series") +
      cell(counts.episodes ?? "&mdash;", "episodes") +
      cell(counts.epg_programmes ?? "&mdash;", "epg programmes") +
      cell((d.playback || {}).active_sessions ?? "&mdash;", "active sessions") +
    `</div>${scopeNote}</section>` +
    block("Health", health) +
    block("Dispatcharr sync",
      row("status", (d.sync || {}).status, (d.sync || {}).status === "success" ? "ok" : "warn") +
      row("last success", (d.sync || {}).synced_at, "muted") +
      row("version", (d.sync || {}).dispatcharr_version, "muted") +
      row("channels synced", (d.sync || {}).channels_synced)) +
    block("Schema",
      row("migrations applied", (d.migrations || {}).applied, "muted") +
      row("latest", (d.migrations || {}).latest, "muted") +
      row("profiles", counts.profiles, "muted"));
}

$("#connect").addEventListener("click", load);
document.getElementById("token").addEventListener("keydown", (e) => { if (e.key === "Enter") load(); });
if (sessionStorage.getItem(KEY)) { load(); setInterval(load, 10000); }
else { $("#connect").addEventListener("click", () => setInterval(load, 10000), { once: true }); }
</script>
</body>
</html>
"""
