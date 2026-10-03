"""A6: the 009 migration file itself.

Content-level checks rather than a live apply: the failure this guards against
was not a syntax error but a *missing* statement. Migration 006 declares the
artwork columns inside CREATE TABLE IF NOT EXISTS, which silently does nothing
to a table that already exists, so a database that predates 006 has no poster
column at all — and A6 is the first code that SELECTs it. A test that only
checks "the runner applied every file" would never notice.

``tests/test_migrations_live.py`` covers the apply path against a real server.
"""

import re
from pathlib import Path

MIGRATION = (
    Path(__file__).resolve().parents[1] / "migrations" / "009_m14_favourites_resume.sql"
)
TEXT = MIGRATION.read_text(encoding="utf-8")


class TestArtworkColumnsAreReconciled:
    """Every column the catalog now SELECTs must be created or reconciled."""

    def test_the_file_exists(self):
        assert MIGRATION.is_file(), f"{MIGRATION} is missing"

    def test_every_artwork_column_is_added_if_missing(self):
        added = set(
            match.group(2)
            for match in re.finditer(
                r"ALTER TABLE\s+(\w+)\s+ADD COLUMN IF NOT EXISTS\s+(\w+)", TEXT, re.IGNORECASE
            )
        )
        required = {
            "poster",
            "backdrop",
            "duration",
            "release_year",
            "thumbnail",
        }
        assert required <= added, f"009 must reconcile {required - added}"

    def test_the_additions_are_idempotent(self):
        # ADD COLUMN without IF NOT EXISTS would fail on the second run, and the
        # runner replays the whole set against an already-migrated database.
        for statement in re.findall(r"ALTER TABLE[^;]+;", TEXT, re.IGNORECASE):
            assert "IF NOT EXISTS" in statement.upper(), statement


class TestFavouriteAndResumeTables:
    def test_both_tables_are_created(self):
        for table in ("tavuno_favourites", "tavuno_resume"):
            assert re.search(rf"CREATE TABLE IF NOT EXISTS\s+{table}\b", TEXT), table

    def test_both_are_keyed_on_profile_kind_item(self):
        # The upsert path depends on this unique key; without it a double-tap
        # is a 500 rather than a no-op.
        assert TEXT.count("UNIQUE (profile, kind, item_id)") == 2

    def test_both_cascade_from_the_profile(self):
        """Deleting a profile must not orphan a favourite or a resume row."""
        assert TEXT.count("REFERENCES tavuno_profiles(id) ON DELETE CASCADE") == 2