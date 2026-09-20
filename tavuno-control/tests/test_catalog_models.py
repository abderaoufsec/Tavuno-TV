"""Unit tests for catalog domain models (M11.1)."""

import unittest
from pydantic import ValidationError

from app.catalog.models import Channel, Category, Movie, Series


class TestChannel(unittest.TestCase):
    """Test Channel model validation and serialization."""

    def test_valid_channel(self):
        """A valid database-shaped channel should validate."""
        channel = Channel(
            id=1,
            name="Test Channel",
            slug="test-channel",
            category_id=5,
            logo="550e8400-e29b-41d4-a716-446655440000",
            is_active=True,
        )
        self.assertEqual(channel.id, 1)
        self.assertEqual(channel.name, "Test Channel")
        self.assertEqual(channel.slug, "test-channel")
        self.assertEqual(channel.category_id, 5)
        self.assertEqual(channel.logo, "550e8400-e29b-41d4-a716-446655440000")
        self.assertTrue(channel.is_active)

    def test_channel_nullable_category(self):
        """Optional fields that are nullable in the database should accept None."""
        channel = Channel(
            id=1,
            name="Test Channel",
            slug="test-channel",
            category_id=None,
            logo=None,
            is_active=True,
        )
        self.assertIsNone(channel.category_id)
        self.assertIsNone(channel.logo)

    def test_channel_missing_required_name(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Channel(
                id=1,
                name="",  # Too short
                slug="test-channel",
                is_active=True,
            )

    def test_channel_missing_required_slug(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Channel(
                id=1,
                name="Test Channel",
                slug="",  # Too short
                is_active=True,
            )

    def test_channel_serialization(self):
        """Verify that models serialize into the expected JSON-compatible structure."""
        channel = Channel(
            id=1,
            name="Test Channel",
            slug="test-channel",
            category_id=5,
            logo=None,
            is_active=True,
        )
        serialized = channel.model_dump()
        self.assertEqual(serialized["id"], 1)
        self.assertEqual(serialized["name"], "Test Channel")
        self.assertEqual(serialized["slug"], "test-channel")
        self.assertEqual(serialized["category_id"], 5)
        self.assertIsNone(serialized["logo"])
        self.assertTrue(serialized["is_active"])


class TestCategory(unittest.TestCase):
    """Test Category model validation and serialization."""

    def test_valid_category(self):
        """A valid database-shaped category should validate."""
        category = Category(
            id=1,
            name="Sports",
            kind="sports",
            parent_id=None,
            sort_order=10,
            is_active=True,
        )
        self.assertEqual(category.id, 1)
        self.assertEqual(category.name, "Sports")
        self.assertEqual(category.kind, "sports")
        self.assertIsNone(category.parent_id)
        self.assertEqual(category.sort_order, 10)
        self.assertTrue(category.is_active)

    def test_category_nullable_parent(self):
        """Optional fields that are nullable in the database should accept None."""
        category = Category(
            id=1,
            name="Sports",
            kind="sports",
            parent_id=None,
            sort_order=0,
            is_active=True,
        )
        self.assertIsNone(category.parent_id)

    def test_category_with_parent(self):
        """Category with parent should validate."""
        category = Category(
            id=2,
            name="Football",
            kind="sports",
            parent_id=1,
            sort_order=5,
            is_active=True,
        )
        self.assertEqual(category.parent_id, 1)

    def test_category_missing_required_name(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Category(
                id=1,
                name="",  # Too short
                kind="sports",
                is_active=True,
            )

    def test_category_missing_required_kind(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Category(
                id=1,
                name="Sports",
                kind="",  # Too short
                is_active=True,
            )

    def test_category_serialization(self):
        """Verify that models serialize into the expected JSON-compatible structure."""
        category = Category(
            id=1,
            name="Sports",
            kind="sports",
            parent_id=None,
            sort_order=10,
            is_active=True,
        )
        serialized = category.model_dump()
        self.assertEqual(serialized["id"], 1)
        self.assertEqual(serialized["name"], "Sports")
        self.assertEqual(serialized["kind"], "sports")
        self.assertIsNone(serialized["parent_id"])
        self.assertEqual(serialized["sort_order"], 10)
        self.assertTrue(serialized["is_active"])


class TestMovie(unittest.TestCase):
    """Test Movie model validation and serialization."""

    def test_valid_movie(self):
        """A valid database-shaped movie should validate."""
        movie = Movie(
            id=1,
            title="Test Movie",
            slug="test-movie",
            category_id=5,
            synopsis="A test movie",
            release_year=2024,
            is_active=True,
        )
        self.assertEqual(movie.id, 1)
        self.assertEqual(movie.title, "Test Movie")
        self.assertEqual(movie.slug, "test-movie")
        self.assertEqual(movie.category_id, 5)
        self.assertEqual(movie.synopsis, "A test movie")
        self.assertEqual(movie.release_year, 2024)
        self.assertTrue(movie.is_active)

    def test_movie_nullable_fields(self):
        """Optional fields that are nullable in the database should accept None."""
        movie = Movie(
            id=1,
            title="Test Movie",
            slug="test-movie",
            category_id=None,
            synopsis=None,
            release_year=None,
            is_active=True,
        )
        self.assertIsNone(movie.category_id)
        self.assertIsNone(movie.synopsis)
        self.assertIsNone(movie.release_year)

    def test_movie_missing_required_title(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Movie(
                id=1,
                title="",  # Too short
                slug="test-movie",
                is_active=True,
            )

    def test_movie_missing_required_slug(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Movie(
                id=1,
                title="Test Movie",
                slug="",  # Too short
                is_active=True,
            )

    def test_movie_serialization(self):
        """Verify that models serialize into the expected JSON-compatible structure."""
        movie = Movie(
            id=1,
            title="Test Movie",
            slug="test-movie",
            category_id=5,
            synopsis="A test movie",
            release_year=2024,
            is_active=True,
        )
        serialized = movie.model_dump()
        self.assertEqual(serialized["id"], 1)
        self.assertEqual(serialized["title"], "Test Movie")
        self.assertEqual(serialized["slug"], "test-movie")
        self.assertEqual(serialized["category_id"], 5)
        self.assertEqual(serialized["synopsis"], "A test movie")
        self.assertEqual(serialized["release_year"], 2024)
        self.assertTrue(serialized["is_active"])


class TestSeries(unittest.TestCase):
    """Test Series model validation and serialization."""

    def test_valid_series(self):
        """A valid database-shaped series should validate."""
        series = Series(
            id=1,
            title="Test Series",
            slug="test-series",
            category_id=5,
            synopsis="A test series",
            is_active=True,
        )
        self.assertEqual(series.id, 1)
        self.assertEqual(series.title, "Test Series")
        self.assertEqual(series.slug, "test-series")
        self.assertEqual(series.category_id, 5)
        self.assertEqual(series.synopsis, "A test series")
        self.assertTrue(series.is_active)

    def test_series_nullable_fields(self):
        """Optional fields that are nullable in the database should accept None."""
        series = Series(
            id=1,
            title="Test Series",
            slug="test-series",
            category_id=None,
            synopsis=None,
            is_active=True,
        )
        self.assertIsNone(series.category_id)
        self.assertIsNone(series.synopsis)

    def test_series_missing_required_title(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Series(
                id=1,
                title="",  # Too short
                slug="test-series",
                is_active=True,
            )

    def test_series_missing_required_slug(self):
        """Clearly invalid required values should fail validation."""
        with self.assertRaises(ValidationError):
            Series(
                id=1,
                title="Test Series",
                slug="",  # Too short
                is_active=True,
            )

    def test_series_serialization(self):
        """Verify that models serialize into the expected JSON-compatible structure."""
        series = Series(
            id=1,
            title="Test Series",
            slug="test-series",
            category_id=5,
            synopsis="A test series",
            is_active=True,
        )
        serialized = series.model_dump()
        self.assertEqual(serialized["id"], 1)
        self.assertEqual(serialized["title"], "Test Series")
        self.assertEqual(serialized["slug"], "test-series")
        self.assertEqual(serialized["category_id"], 5)
        self.assertEqual(serialized["synopsis"], "A test series")
        self.assertTrue(serialized["is_active"])


if __name__ == "__main__":
    unittest.main()
