"""Tests for the Services cache serialization helper."""

import datetime
import os
import unittest

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.services import json_default


class JsonDefaultTests(unittest.TestCase):
    """Cache payloads must serialize exactly like FastAPI's JSON encoder."""

    def test_utc_datetime_matches_fastapi_format(self):
        value = datetime.datetime(2026, 10, 1, 14, 10, tzinfo=datetime.timezone.utc)
        self.assertEqual(json_default(value), "2026-10-01T14:10:00Z")

    def test_offset_datetime_keeps_offset(self):
        value = datetime.datetime(
            2026,
            10,
            1,
            16,
            10,
            tzinfo=datetime.timezone(datetime.timedelta(hours=2)),
        )
        self.assertEqual(json_default(value), "2026-10-01T16:10:00+02:00")

    def test_naive_datetime_is_isoformatted(self):
        value = datetime.datetime(2026, 10, 1, 14, 10, 30)
        self.assertEqual(json_default(value), "2026-10-01T14:10:30")

    def test_other_values_fall_back_to_str(self):
        self.assertEqual(json_default(42), "42")


if __name__ == "__main__":
    unittest.main()
