"""Live-database checks for the migration runner.

These run against a real PostgreSQL server, so the module skips unless
``TAVUNO_TEST_POSTGRES_DSN`` points at one. Each test creates a throwaway
database (random suffix, dropped afterwards), applies the migration set,
asserts the resulting schema, and tears the database down again.

The prerequisite these tests encode
-----------------------------------
The files in ``tavuno-control/migrations`` are *incremental patches* on the
Directus-managed base schema, not a standalone bootstrap. Migration ``001``
alters ``tavuno_profiles``; ``002`` deletes from ``tavuno_channel_sources``;
``004`` inserts into ``tavuno_plans``; ``006`` and ``008`` reference
``tavuno_categories`` and ``tavuno_profiles`` by foreign key. Not one of those
tables is created by a migration — they come from
``tavuno-infra/scripts/apply-m2-schema.ps1`` (the M2 base schema, applied
against Directus before the API ever starts).

So every scratch database is seeded with a minimal stand-in for that base
schema (:data:`BASE_SCHEMA_DDL`) before the runner sees it. Remove that seed
and ``001`` fails with ``relation "tavuno_profiles" does not exist`` — which is
precisely what a fresh database hits if auto-migration runs before the base
schema exists.
"""

import os
import uuid
from collections.abc import Generator
from contextlib import contextmanager
from typing import Any

import psycopg
import pytest

from app.db_migrations import LEDGER_TABLE, apply_pending, discover_migrations

DSN = os.getenv("TAVUNO_TEST_POSTGRES_DSN")

pytestmark = pytest.mark.skipif(
    not DSN,
    reason="TAVUNO_TEST_POSTGRES_DSN not set — skipping live-DB migration tests",
)

# The Directus base schema, reduced to the tables and columns the migration set
# touches. Mirrors tavuno-infra/scripts/apply-m2-schema.ps1: same table names,
# same column names, same uniqueness that migrations 002 and 004 rely on.
BASE_SCHEMA_DDL: tuple[str, ...] = (
    "CREATE TABLE tavuno_categories ("
    " id SERIAL PRIMARY KEY, name VARCHAR(120) NOT NULL);",
    "CREATE TABLE tavuno_channels ("
    " id SERIAL PRIMARY KEY, name VARCHAR(160) NOT NULL);",
    "CREATE TABLE tavuno_channel_sources ("
    " id SERIAL PRIMARY KEY, channel INTEGER NOT NULL REFERENCES tavuno_channels(id),"
    " provider VARCHAR(64) NOT NULL, external_id VARCHAR(128) NOT NULL);",
    "CREATE TABLE tavuno_profiles ("
    " id SERIAL PRIMARY KEY, directus_user UUID NOT NULL UNIQUE,"
    " display_name VARCHAR(120) NOT NULL, status VARCHAR(32) NOT NULL);",
    "CREATE TABLE tavuno_devices ("
    " id SERIAL PRIMARY KEY, profile INTEGER NOT NULL REFERENCES tavuno_profiles(id),"
    " name VARCHAR(120) NOT NULL);",
    "CREATE TABLE tavuno_plans ("
    " id SERIAL PRIMARY KEY, name VARCHAR(120) NOT NULL,"
    " code VARCHAR(64) NOT NULL UNIQUE, description TEXT,"
    " max_devices INTEGER NOT NULL, max_concurrent_streams INTEGER NOT NULL,"
    " is_active BOOLEAN NOT NULL DEFAULT TRUE);",
)
# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
# One suffix per process: parallel workers then pick different database names
# and cannot drop each other's work out from under themselves.
DB_SUFFIX = uuid.uuid4().hex[:12]


def _maintenance_dsn(dsn: str) -> str:
    """*dsn* with its database replaced by ``postgres``, the maintenance DB."""
    prefix, _, _ = dsn.rpartition("/")
    return f"{prefix}/postgres"


@contextmanager
def scratch_database(dsn: str) -> Generator[str, None, None]:
    """Yield the DSN of a fresh, empty database; drop it on the way out."""
    maintenance = _maintenance_dsn(dsn)
    name = f"tavuno_migrations_test_{DB_SUFFIX}"
    scratch = f"{dsn.rpartition('/')[0]}/{name}"

    def drop() -> None:
        # CREATE/DROP DATABASE cannot run inside a transaction, and DROP needs no
        # other backend attached, hence the terminate-then-FORCE pair.
        with psycopg.connect(maintenance, autocommit=True) as conn:
            conn.execute(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity"
                " WHERE datname = %s AND pid <> pg_backend_pid()",
                (name,),
            )
            conn.execute(f'DROP DATABASE IF EXISTS "{name}" WITH (FORCE)')

    drop()
    with psycopg.connect(maintenance, autocommit=True) as conn:
        conn.execute(f'CREATE DATABASE "{name}"')

    try:
        yield scratch
    finally:
        drop()


def _seed_base_schema(dsn: str) -> None:
    """Apply the Directus base-schema stand-in the migrations extend."""
    with psycopg.connect(dsn, autocommit=True) as conn:
        for statement in BASE_SCHEMA_DDL:
            conn.execute(statement)


def _run_all(dsn: str) -> Any:
    """Run the whole migration set on *dsn* through a single transaction."""
    with psycopg.connect(dsn) as conn:
        report = apply_pending(conn)
        conn.commit()
    return report


def _column_exists(conn: Any, table: str, column: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM information_schema.columns"
        " WHERE table_name = %s AND column_name = %s",
        (table, column),
    ).fetchone()
    return row is not None


def _assert_column(conn: Any, table: str, column: str) -> None:
    assert _column_exists(conn, table, column), f"{table}.{column} is missing"


def _assert_no_column(conn: Any, table: str, column: str) -> None:
    assert not _column_exists(conn, table, column), f"{table}.{column} should not exist"
# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------
class TestMigrationRunnerAgainstLivePostgres:
    def test_runner_requires_the_base_schema_first(self) -> None:
        """Documents the coupling: without the Directus base schema, 001 fails.

        This is the failure mode a fresh deployment hits when auto-migration runs
        before ``apply-m2-schema.ps1`` has created the base tables, so the test
        pins it rather than leaving it to be rediscovered in production.
        """
        with scratch_database(DSN) as scratch:
            with pytest.raises(psycopg.errors.UndefinedTable):
                _run_all(scratch)

    def test_all_migrations_apply_to_a_freshly_seeded_database(self) -> None:
        with scratch_database(DSN) as scratch:
            _seed_base_schema(scratch)
            report = _run_all(scratch)

            expected = [path.name for path in discover_migrations()]
            assert list(report.applied) == expected, "every migration should apply once"
            assert report.skipped == ()

            with psycopg.connect(scratch, autocommit=True) as conn:
                # 001-005 patch the base schema in place.
                _assert_column(conn, "tavuno_profiles", "email")
                _assert_column(conn, "tavuno_profiles", "password_hash")
                _assert_column(conn, "tavuno_profiles", "role")
                _assert_column(conn, "tavuno_devices", "device_fingerprint")
                _assert_column(conn, "tavuno_devices", "last_seen_at")
                _assert_column(conn, "tavuno_devices", "revoked_at")

                # 006 creates the VOD tables in their corrected shape.
                _assert_column(conn, "tavuno_movies", "category")
                _assert_no_column(conn, "tavuno_movies", "category_id")
                _assert_column(conn, "tavuno_movies", "external_id")
                _assert_column(conn, "tavuno_movies", "updated_at")
                _assert_column(conn, "tavuno_series", "category")
                _assert_no_column(conn, "tavuno_series", "category_id")
                _assert_column(conn, "tavuno_seasons", "series")
                _assert_column(conn, "tavuno_seasons", "updated_at")
                _assert_column(conn, "tavuno_episodes", "season")
                _assert_column(conn, "tavuno_episodes", "external_id")
                _assert_column(conn, "tavuno_episodes", "stream_url")
                _assert_column(conn, "tavuno_episodes", "updated_at")

                # 008 adds per-profile customization.
                _assert_column(conn, "tavuno_profiles", "owner_profile")
                _assert_column(conn, "tavuno_profiles", "is_kids")
                _assert_column(conn, "tavuno_profiles", "avatar")

                for table in ("tavuno_movies", "tavuno_series", "tavuno_seasons", "tavuno_episodes"):
                    row = conn.execute(
                        "SELECT 1 FROM pg_trigger"
                        " WHERE tgrelid = %s::regclass AND tgname = %s",
                        (table, f"update_{table}_updated_at"),
                    ).fetchone()
                    assert row is not None, f"update_{table}_updated_at trigger is missing"

    def test_second_run_applies_nothing(self) -> None:
        with scratch_database(DSN) as scratch:
            _seed_base_schema(scratch)
            _run_all(scratch)

            report = _run_all(scratch)

            assert report.applied == (), "a rerun must apply nothing"
            assert len(report.skipped) == len(discover_migrations())

    def test_ledger_records_every_file_in_name_order(self) -> None:
        with scratch_database(DSN) as scratch:
            _seed_base_schema(scratch)
            _run_all(scratch)

            with psycopg.connect(scratch, autocommit=True) as conn:
                rows = conn.execute(
                    f"SELECT filename FROM {LEDGER_TABLE} ORDER BY filename"
                ).fetchall()

            assert [row[0] for row in rows] == [path.name for path in discover_migrations()]

    def test_replay_converges_a_drifted_vod_schema(self) -> None:
        """A VOD schema from an earlier revision must be patched, not tripped over.

        ``CREATE TABLE IF NOT EXISTS`` leaves an existing table untouched, so a
        table created before this file declared ``category``/``external_id``/
        ``updated_at``/``stream_url`` would be missing exactly the columns the
        indexes and triggers below it reference. The reconciliation block of
        ``ALTER TABLE ... ADD COLUMN IF NOT EXISTS`` statements closes that gap.
        """
        drifted_ddl = (
            "CREATE TABLE tavuno_movies ("
            " id SERIAL PRIMARY KEY, title VARCHAR(255) NOT NULL,"
            " slug VARCHAR(255) NOT NULL UNIQUE);",
            "CREATE TABLE tavuno_series ("
            " id SERIAL PRIMARY KEY, title VARCHAR(255) NOT NULL,"
            " slug VARCHAR(255) NOT NULL UNIQUE);",
            "CREATE TABLE tavuno_seasons ("
            " id SERIAL PRIMARY KEY, series INTEGER NOT NULL REFERENCES tavuno_series(id),"
            " season_number INTEGER NOT NULL, title VARCHAR(255),"
            " UNIQUE(series, season_number));",
            "CREATE TABLE tavuno_episodes ("
            " id SERIAL PRIMARY KEY, season INTEGER NOT NULL REFERENCES tavuno_seasons(id),"
            " episode_number INTEGER NOT NULL, title VARCHAR(255) NOT NULL,"
            " UNIQUE(season, episode_number));",
        )

        with scratch_database(DSN) as scratch:
            _seed_base_schema(scratch)
            with psycopg.connect(scratch, autocommit=True) as conn:
                for statement in drifted_ddl:
                    conn.execute(statement)

            report = _run_all(scratch)
            assert "006_m12_vod_schema.sql" in report.applied

            # Every column 006's own indexes and triggers reference must now be
            # present. tavuno_seasons.external_id is deliberately not listed: no
            # statement in 006 reads it, so the reconciliation block leaves it to
            # the CREATE TABLE path, exactly as the file's comment explains.
            with psycopg.connect(scratch, autocommit=True) as conn:
                for table, column in (
                    ("tavuno_movies", "category"),
                    ("tavuno_movies", "external_id"),
                    ("tavuno_movies", "updated_at"),
                    ("tavuno_series", "category"),
                    ("tavuno_series", "external_id"),
                    ("tavuno_series", "updated_at"),
                    ("tavuno_seasons", "updated_at"),
                    ("tavuno_episodes", "external_id"),
                    ("tavuno_episodes", "stream_url"),
                    ("tavuno_episodes", "updated_at"),
                ):
                    _assert_column(conn, table, column)

                _assert_no_column(conn, "tavuno_movies", "category_id")
                _assert_no_column(conn, "tavuno_series", "category_id")