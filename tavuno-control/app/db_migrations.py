"""Ordered, ledger-tracked SQL migrations for the Tavuno database.

``tavuno-control/migrations`` holds plain-SQL migrations that guard themselves:
each statement sits inside an ``IF NOT EXISTS`` check (or carries ``ON CONFLICT``),
so re-running one is a no-op. What was missing was the *ledger* — nothing recorded
which files had been applied, so bringing up a fresh database meant remembering to
run ``psql -f migrations/NNN_name.sql`` by hand, in order, every time.

This module supplies that half:

* :func:`discover_migrations` finds the ``.sql`` files and orders them by name
  (``001_``, ``002_``, … — the zero-padded prefix *is* the apply order).
* :func:`apply_pending` creates the ledger table, reads it, and executes only the
  files it does not already name, recording each one as it goes.
* :func:`run_migrations` wraps that in a single transaction through a caller-supplied
  connection factory, so a failing migration leaves behind neither its own changes
  nor a half-written ledger.

It is wired into the FastAPI lifespan behind ``TAVUNO_AUTO_MIGRATE`` (see
``app/config.py``). Because every file is self-guarding, switching that flag on
for an already-migrated database replays the whole set as no-ops rather than
erroring.

The set patches a schema that already exists; it does not bootstrap one. The
tables these files alter and reference — ``tavuno_profiles``, ``tavuno_devices``,
``tavuno_plans``, ``tavuno_categories``, ``tavuno_channels``,
``tavuno_channel_sources`` — come from the Directus base schema applied by
``tavuno-infra/scripts/apply-m2-schema.ps1``, not from any migration. Against a
database where that has never run, ``001_m9_auth_schema.sql`` fails with
``relation "tavuno_profiles" does not exist``. ``tests/test_migrations_live.py``
pins both halves of this: the failure without the base schema, and a full apply
once it is present, against a real PostgreSQL server.
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from contextlib import AbstractContextManager
from dataclasses import dataclass
from pathlib import Path
from typing import Any

logger = logging.getLogger("tavuno-control.db_migrations")

# The ledger table lives in the same schema as the tables it describes, so a
# database copied between environments carries its migration state with it.
LEDGER_TABLE = "tavuno_schema_migrations"

_CREATE_LEDGER = f"""
CREATE TABLE IF NOT EXISTS {LEDGER_TABLE} (
    filename TEXT PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
)
"""

_SELECT_APPLIED = f"SELECT filename FROM {LEDGER_TABLE} ORDER BY filename"

# DO NOTHING rather than a bare insert: two API workers starting at once can race
# to apply the same file, and the loser should record the win, not crash on it.
_INSERT_APPLIED = (
    f"INSERT INTO {LEDGER_TABLE} (filename) VALUES (%s) ON CONFLICT (filename) DO NOTHING"
)


@dataclass(frozen=True)
class MigrationReport:
    """What one run did: the files it executed, and the ones already recorded."""

    applied: tuple[str, ...] = ()
    skipped: tuple[str, ...] = ()

    @property
    def applied_count(self) -> int:
        return len(self.applied)

    def summary(self) -> str:
        """One line for the startup log — the count is what an operator wants."""
        if not self.applied:
            return f"schema up to date ({len(self.skipped)} migration(s) already applied)"
        return f"applied {len(self.applied)} migration(s): {', '.join(self.applied)}"


def migrations_dir() -> Path:
    """The ``migrations`` directory beside the ``app`` package.

    Resolved from this file rather than from the process working directory, so it
    points at ``tavuno-control/migrations`` in a checkout and at ``/app/migrations``
    in the container image (the Dockerfile copies that directory in).
    """
    return Path(__file__).resolve().parent.parent / "migrations"


def discover_migrations(directory: Path | None = None) -> list[Path]:
    """Every ``*.sql`` file in *directory*, ordered by filename.

    The numeric prefix is zero-padded, so plain name ordering is apply order.
    An absent directory yields an empty list, which lets the runner run — and
    no-op — in an environment that ships the package without the SQL files.
    """
    root = migrations_dir() if directory is None else Path(directory)
    if not root.is_dir():
        return []
    return sorted(
        (path for path in root.iterdir() if path.is_file() and path.suffix == ".sql"),
        key=lambda path: path.name,
    )


def ensure_ledger(connection: Any) -> None:
    """Create the ledger table if this database has never been migrated."""
    connection.execute(_CREATE_LEDGER)


def applied_filenames(connection: Any) -> set[str]:
    """Names of the migrations the ledger already records."""
    rows = connection.execute(_SELECT_APPLIED).fetchall()
    return {_filename_of(row) for row in rows}


def apply_pending(
    connection: Any,
    directory: Path | None = None,
    *,
    log: logging.Logger = logger,
) -> MigrationReport:
    """Apply every un-applied migration, in order, on *connection*.

    The caller owns the transaction: hand in a connection from
    ``Services.connection()`` (or go through :func:`run_migrations`) so that a
    failure rolls the migration and its ledger row back together instead of
    leaving the ledger claiming work the schema never received.
    """
    ensure_ledger(connection)
    done = applied_filenames(connection)

    applied: list[str] = []
    skipped: list[str] = []
    for path in discover_migrations(directory):
        if path.name in done:
            skipped.append(path.name)
            continue
        log.info("Applying migration %s", path.name)
        _execute_script(connection, path.read_text(encoding="utf-8"))
        connection.execute(_INSERT_APPLIED, (path.name,))
        applied.append(path.name)

    return MigrationReport(applied=tuple(applied), skipped=tuple(skipped))


def run_migrations(
    connection_factory: Callable[[], AbstractContextManager[Any]],
    directory: Path | None = None,
    *,
    log: logging.Logger = logger,
) -> MigrationReport:
    """Apply pending migrations in one transaction.

    *connection_factory* is any zero-argument callable returning a context manager
    that yields a connection — ``Services.connection`` is exactly that shape, and
    its context manager commits on a clean exit and rolls back on an exception.
    """
    with connection_factory() as connection:
        return apply_pending(connection, directory, log=log)


def _filename_of(row: Any) -> str:
    """Read the single ``filename`` column out of a ledger row.

    ``Services.connection`` installs ``dict_row`` in production; the index fallback
    keeps the helper usable with the plain tuple rows a test double may return.
    """
    if isinstance(row, dict):
        return str(row["filename"])
    return str(row[0])


def _execute_script(connection: Any, sql: str) -> None:
    """Run a whole ``.sql`` file through a single parameterless ``execute``.

    psycopg sends a query carrying no parameters over the *simple* protocol, which
    is what makes the many statements inside one migration file legal in one call.
    Every result set but the last then has to be walked with ``nextset()`` — left
    unconsumed, the next statement on this connection would trip over it.
    """
    cursor = connection.execute(sql)
    nextset = getattr(cursor, "nextset", None)
    if nextset is None:  # pragma: no cover - psycopg cursors always provide it
        return
    while nextset():
        pass