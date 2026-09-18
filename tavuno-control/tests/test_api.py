from contextlib import contextmanager
import unittest

from app.main import get_channel, health, home, list_channels


class FakeServices:
    def health(self):
        return {"database": "ok", "cache": "ok"}

    @contextmanager
    def connection(self):
        yield FakeConnection()


class FakeConnection:
    def execute(self, query, parameters=()):
        self.query = query
        self.parameters = parameters
        return self

    def fetchall(self):
        if "tavuno_categories" in self.query:
            return [{"id": 1, "name": "M2 Demo Live", "kind": "live"}]
        if "tavuno_channels" in self.query:
            return [{"id": 1, "name": "M2 Demo Channel", "slug": "m2-demo-channel", "category": 1, "is_active": True}]
        return []

    def fetchone(self):
        if "WHERE id = %s" in self.query:
            if self.parameters == (999,):
                return None
            return {"id": 1, "name": "M2 Demo Channel", "slug": "m2-demo-channel", "category": 1, "is_active": True}
        return None


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.services = FakeServices()

    def test_health_reports_dependencies(self):
        self.assertEqual(health(self.services)["status"], "ok")

    def test_home_returns_catalog_shape(self):
        self.assertEqual(home(self.services)["categories"][0]["name"], "M2 Demo Live")

    def test_channel_lookup_and_not_found_behavior(self):
        self.assertEqual(get_channel(1, self.services)["id"], 1)
        with self.assertRaises(Exception) as response:
            get_channel(999, self.services)
        self.assertEqual(response.exception.status_code, 404)

    def test_channel_listing_filters_active_catalog(self):
        self.assertEqual(len(list_channels(self.services)), 1)
