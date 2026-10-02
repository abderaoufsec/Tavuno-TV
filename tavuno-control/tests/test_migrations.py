"""Tests for the ordered SQL migration runner."""

import os
import re
import tempfile
import unittest
from pathlib import Path

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.db_migrations import (
    LEDGER_TABLE,
    MigrationReport,
    _filename_of,
    applied_filenames,
    apply_pending,
    discover_migrations,
    migrations_dir,
    run_migrations,
)


class FakeCursor:
    """Minimal stand-in for a psycopg cursor.

    ``nextset()`` is driven by a count handed in by the connection so a test can
    assert the runner drains the extra result sets a multi-statement migration
    produces before issuing the next statement on that connection.
    """

    def __init__(self, rows, extra_result_sets=0):
        self._rows = rows
        self.remaining_sets = extra_result_sets
        self.nextset_calls = 0

    def fetchall(self):
        return self._rows

    def nextset(self):
        self.nextset_calls += 1
        if self.remaining_sets > 0:
            self.remaining_sets -= 1
            return True
        return False


class FakeConnection:
    """An in-memory stand-in for a psycopg connection.

    ``execute`` records every statement in order and answers the three queries the
    runner issues against the ledger. Anything else is treated as a migration
    script: the body is kept verbatim so a test can assert what was sent, and the
    connection answers with a cursor that has result sets to drain.
    """

    def __init__(self, applied=(), extra_result_sets=0):
        self.statements = []
        self.ledger = list(applied)
        self.scripts = []
        self.cursors = []
        self.script_cursors = []
        self._extra_result_sets = extra_result_sets

    def execute(self, sql, parameters=()):
        normalized = " ".join(sql.split())
        self.statements.append((normalized, parameters))
        rows = []
        extra_result_sets = 0

        if normalized.startswith(f"SELECT filename FROM {LEDGER_TABLE}"):
            rows = [{"filename": name} for name in self.ledger]
        elif normalized.startswith(f"INSERT INTO {LEDGER_TABLE}"):
            if parameters and parameters[0] not in self.ledger:
                self.ledger.append(parameters[0])
        elif not normalized.startswith(f"CREATE TABLE IF NOT EXISTS {LEDGER_TABLE}"):
            # A migration script: one execute, several statements, so several result
            # sets for the runner to drain before the ledger insert follows.
            self.scripts.append(sql)
            extra_result_sets = self._extra_result_sets

        cursor = FakeCursor(rows, extra_result_sets)
        self.cursors.append(cursor)
        if self.scripts and self.scripts[-1] is sql:
            self.script_cursors.append(cursor)
        return cursor

    @property
    def create_ledger_statements(self):
        return [sql for sql, _ in self.statements if sql.startswith("CREATE TABLE IF NOT EXISTS")]

    @property
    def inserted_filenames(self):
        return [params[0] for sql, params in self.statements if sql.startswith(f"INSERT INTO {LEDGER_TABLE}")]


class MigrationTestCase(unittest.TestCase):
    """Shared scratch directory plus auth-free connection doubles."""

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.directory = Path(self._tmp.name)

    def write_migration(self, name, body="SELECT 1;"):
        path = self.directory / name
        path.write_text(body, encoding="utf-8")
        return path


class MigrationDiscoveryTests(MigrationTestCase):
    """The filename prefix is the contract: zero-padded names *are* apply order."""

    def test_orders_migrations_by_filename(self):
        self.write_migration("003_third.sql")
        self.write_migration("001_first.sql")
        self.write_migration("002_second.sql")

        names = [path.name for path in discover_migrations(self.directory)]

        self.assertEqual(names, ["001_first.sql", "002_second.sql", "003_third.sql"])

    def test_ignores_non_sql_files_and_directories(self):
        self.write_migration("001_first.sql")
        (self.directory / "notes.txt").write_text("not a migration", encoding="utf-8")
        (self.directory / "002_a_directory.sql").mkdir()

        names = [path.name for path in discover_migrations(self.directory)]

        self.assertEqual(names, ["001_first.sql"])

    def test_missing_directory_yields_no_migrations(self):
        self.assertEqual(discover_migrations(self.directory / "does-not-exist"), [])

    def test_packaged_directory_sits_beside_the_app_package(self):
        """Pins the layout the Dockerfile must reproduce: ./migrations next to ./app."""
        packaged = migrations_dir()

        self.assertTrue(packaged.is_dir(), f"{packaged} is missing")
        self.assertEqual(packaged.name, "migrations")

        discovered = discover_migrations()
        self.assertEqual(discovered, sorted(discovered, key=lambda path: path.name))
        self.assertGreaterEqual(len(discovered), 8)
        self.assertTrue(all(path.suffix == ".sql" for path in discovered))
        self.assertTrue(discovered[0].name.startswith("001_"), discovered[0].name)


class LedgerTests(MigrationTestCase):
    """The ledger is what makes a migration run exactly once."""

    def test_applied_filenames_accepts_dict_and_tuple_rows(self):
        self.assertEqual(_filename_of({"filename": "001_a.sql"}), "001_a.sql")
        self.assertEqual(_filename_of(("002_b.sql",)), "002_b.sql")

    def test_applied_filenames_reads_the_ledger(self):
        connection = FakeConnection(applied=["001_a.sql", "002_b.sql"])

        self.assertEqual(applied_filenames(connection), {"001_a.sql", "002_b.sql"})

    def test_every_run_creates_the_ledger_if_absent(self):
        connection = FakeConnection()

        apply_pending(connection, self.directory)

        self.assertEqual(len(connection.create_ledger_statements), 1)


class ApplyPendingTests(MigrationTestCase):
    """``apply_pending`` runs what is pending, records it, and skips the rest."""

    def test_applies_every_migration_on_a_fresh_database(self):
        self.write_migration("001_first.sql", "-- first\nCREATE TABLE t (id int);")
        self.write_migration("002_second.sql", "-- second\nCREATE INDEX i ON t (id);")
        connection = FakeConnection()

        report = apply_pending(connection, self.directory)

        self.assertEqual(report.applied, ("001_first.sql", "002_second.sql"))
        self.assertEqual(report.skipped, ())
        self.assertEqual(connection.ledger, ["001_first.sql", "002_second.sql"])
        self.assertEqual(connection.inserted_filenames, ["001_first.sql", "002_second.sql"])
        self.assertIn("-- first", connection.scripts[0])
        self.assertIn("-- second", connection.scripts[1])

    def test_skips_migrations_the_ledger_already_records(self):
        self.write_migration("001_first.sql", "CREATE TABLE t (id int);")
        self.write_migration("002_second.sql", "CREATE INDEX i ON t (id);")
        connection = FakeConnection(applied=["001_first.sql"])

        report = apply_pending(connection, self.directory)

        self.assertEqual(report.applied, ("002_second.sql",))
        self.assertEqual(report.skipped, ("001_first.sql",))
        self.assertEqual(len(connection.scripts), 1)
        self.assertIn("CREATE INDEX", connection.scripts[0])

    def test_second_run_of_the_same_set_is_a_no_op(self):
        self.write_migration("001_first.sql")
        self.write_migration("002_second.sql")
        connection = FakeConnection()

        first = apply_pending(connection, self.directory)
        scripts_after_first = len(connection.scripts)
        second = apply_pending(connection, self.directory)

        self.assertEqual(first.applied_count, 2)
        self.assertEqual(second.applied, ())
        self.assertEqual(second.skipped, ("001_first.sql", "002_second.sql"))
        self.assertEqual(len(connection.scripts), scripts_after_first)

    def test_empty_directory_applies_nothing(self):
        connection = FakeConnection()

        report = apply_pending(connection, self.directory)

        self.assertEqual(report.applied, ())
        self.assertEqual(report.skipped, ())
        self.assertEqual(connection.scripts, [])
        self.assertEqual(connection.ledger, [])

    def test_each_migration_runs_in_one_parameterless_statement(self):
        """A file is sent whole, with no parameters, so psycopg takes the simple protocol.

        That is what allows the several ``DO $$ … $$`` blocks inside one migration to
        share a single ``execute`` — and the cursor then has to be drained before the
        ledger insert follows on the same connection.
        """
        self.write_migration(
            "001_first.sql",
            "DO $$ BEGIN NULL; END $$;\nDO $$ BEGIN NULL; END $$;\n",
        )
        connection = FakeConnection(extra_result_sets=2)

        apply_pending(connection, self.directory)

        script_params = [params for sql, params in connection.statements if "DO $$" in sql]
        self.assertEqual(script_params, [()])
        self.assertEqual([cursor.nextset_calls for cursor in connection.script_cursors], [3])
        self.assertEqual(connection.inserted_filenames, ["001_first.sql"])


class RunMigrationsTests(MigrationTestCase):
    """``run_migrations`` owns the transaction through the caller's factory."""

    def test_runs_inside_the_connection_factory_context_manager(self):
        self.write_migration("001_first.sql")
        connection = FakeConnection()
        events = []

        class Factory:
            def __enter__(self):
                events.append("enter")
                return connection

            def __exit__(self, *exc_info):
                events.append("exit")
                return False

        report = run_migrations(Factory, self.directory)

        self.assertEqual(events, ["enter", "exit"])
        self.assertEqual(report.applied, ("001_first.sql",))
        self.assertEqual(connection.ledger, ["001_first.sql"])

    def test_propagates_failures_so_the_caller_can_roll_back(self):
        self.write_migration("001_first.sql")

        class Boom(Exception):
            pass

        class Factory:
            def __enter__(self):
                raise Boom("connection refused")

            def __exit__(self, *exc_info):  # pragma: no cover - never reached
                return False

        with self.assertRaises(Boom):
            run_migrations(Factory, self.directory)


class MigrationReportTests(unittest.TestCase):
    def test_summary_lists_what_ran(self):
        report = MigrationReport(applied=("001_a.sql", "002_b.sql"), skipped=())

        self.assertIn("applied 2 migration(s)", report.summary())
        self.assertIn("001_a.sql", report.summary())

    def test_summary_reports_an_up_to_date_schema(self):
        report = MigrationReport(applied=(), skipped=("001_a.sql",))

        self.assertIn("up to date", report.summary())
        self.assertIn("1 migration(s) already applied", report.summary())


class AutoMigrateSettingTests(unittest.TestCase):
    """Enabling auto-migration is a deliberate opt-in, so the default stays off."""

    def test_default_is_disabled(self):
        from app.config import Settings

        self.assertFalse(Settings().auto_migrate)

    def test_enabled_by_environment(self):
        from app.config import Settings

        self.assertTrue(Settings(TAVUNO_AUTO_MIGRATE="true").auto_migrate)


class PackagedMigrationsAreRerunnableTests(MigrationTestCase):
    """Every shipped migration has to be safe against an already-migrated schema.

    The ledger starts empty on databases that were migrated by hand, so the runner's
    first run replays the whole set. That is only safe while each file guards its own
    statements — these two tests pin the shapes the files rely on, so a future
    migration cannot quietly break replay. ``003_m11_sports_schema.sql`` is why the
    second test exists: its triggers were originally created without a drop.
    """

    # ``ALTER TABLE`` has no single "IF NOT EXISTS" prefix to strip the way
    # ``CREATE TABLE`` does — the guard sits *inside* the statement. These patterns
    # delete the whole guarded statement shape, so the substring assertions below
    # still catch an unguarded ``ALTER TABLE`` a future migration might add. Each
    # form listed is safe to replay: adding a column that exists and dropping a
    # constraint that is already gone are both no-ops, as is re-dropping NOT NULL.
    GUARDED_ALTER_PATTERNS = (
        r"ALTER TABLE\s+\w+\s+ADD COLUMN IF NOT EXISTS",
        r"ALTER TABLE\s+\w+\s+DROP CONSTRAINT IF EXISTS",
        r"ALTER TABLE\s+\w+\s+ALTER COLUMN\s+\w+\s+DROP NOT NULL",
    )

    @staticmethod
    def guarded_bodies(sql):
        """Text inside each ``DO $$ … END $$;`` block — the part behind an IF check."""
        bodies = []
        remainder = sql
        while "DO $$" in remainder:
            _, _, after = remainder.partition("DO $$")
            body, marker, remainder = after.partition("END $$;")
            if marker:
                bodies.append(body)
        return bodies

    def test_no_unguarded_ddl_outside_do_blocks(self):
        for path in discover_migrations():
            with self.subTest(migration=path.name):
                sql = path.read_text(encoding="utf-8")
                # Comments are prose, not DDL: 006's explanatory note talks *about* an
                # ALTER TABLE without performing one, and 003's does the same for
                # CREATE TRIGGER. Strip them so only executable SQL is judged — and
                # derive the guarded bodies from the same comment-free text, or the
                # replace below would never match.
                comment_free = re.sub(r"--[^\n]*", "", sql)
                unguarded = comment_free
                for body in self.guarded_bodies(comment_free):
                    unguarded = unguarded.replace(body, "", 1)
                for guarded_form in (
                    "CREATE TABLE IF NOT EXISTS",
                    "CREATE INDEX IF NOT EXISTS",
                    "CREATE UNIQUE INDEX IF NOT EXISTS",
                ):
                    unguarded = unguarded.replace(guarded_form, "")
                for guarded_pattern in self.GUARDED_ALTER_PATTERNS:
                    unguarded = re.sub(guarded_pattern, "", unguarded)

                for statement in (
                    "ALTER TABLE",
                    "CREATE TABLE",
                    "CREATE INDEX",
                    "DROP TABLE",
                    "DROP COLUMN",
                    "DROP CONSTRAINT",
                ):
                    self.assertNotIn(
                        statement, unguarded, f"{path.name} has an unguarded {statement}"
                    )

    def test_alter_allowlist_does_not_wave_through_an_unguarded_alter(self):
        """The ALTER patterns delete whole guarded statements, so prove they are not
        so broad that a bare ``ALTER TABLE`` would slip past the check above."""
        cases = {
            "ALTER TABLE t ADD COLUMN IF NOT EXISTS c TEXT;": False,
            "ALTER TABLE t ADD COLUMN c TEXT;": True,
            "ALTER TABLE t ALTER COLUMN c DROP NOT NULL;": False,
            "ALTER TABLE t ALTER COLUMN c SET NOT NULL;": True,
        }
        for statement, should_survive in cases.items():
            with self.subTest(statement=statement):
                scrubbed = statement
                for pattern in self.GUARDED_ALTER_PATTERNS:
                    scrubbed = re.sub(pattern, "", scrubbed)
                self.assertEqual(
                    should_survive,
                    "ALTER TABLE" in scrubbed,
                    f"allowlist treated {statement!r} incorrectly",
                )
    def test_every_create_trigger_is_dropped_first(self):
        """CREATE TRIGGER has no IF NOT EXISTS, so each one needs a DROP before it."""
        for path in discover_migrations():
            with self.subTest(migration=path.name):
                sql = path.read_text(encoding="utf-8")
                for line in sql.splitlines():
                    stripped = line.strip()
                    if not stripped.upper().startswith("CREATE TRIGGER"):
                        continue
                    trigger = stripped.split()[2]
                    self.assertIn(
                        f"DROP TRIGGER IF EXISTS {trigger} ON",
                        sql,
                        f"{path.name} creates {trigger} without dropping it first",
                    )


if __name__ == "__main__":
    unittest.main()