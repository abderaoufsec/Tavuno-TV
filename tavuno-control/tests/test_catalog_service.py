"""Unit tests for CatalogService (M11.2)."""

import pytest
from unittest.mock import Mock, MagicMock, patch
from contextlib import contextmanager

from app.catalog.service import CatalogService
from app.catalog.models import Channel, Category, Movie, Series


@pytest.fixture
def mock_services():
    """Mock Services instance."""
    services = Mock()
    return services


@pytest.fixture
def catalog_service(mock_services):
    """CatalogService instance with mocked Services."""
    return CatalogService(mock_services)


@pytest.fixture
def mock_connection():
    """Mock database connection."""
    conn = MagicMock()
    conn.execute.return_value.fetchall.return_value = []
    conn.execute.return_value.fetchone.return_value = None
    return conn


class TestCatalogServiceInitialization:
    """Test CatalogService initialization."""

    def test_init_with_services(self, mock_services):
        """Test that CatalogService initializes with Services instance."""
        service = CatalogService(mock_services)
        assert service.services is mock_services


class TestChannelMapping:
    """Test channel row to model mapping."""

    def test_map_channel_basic(self, catalog_service):
        """Test mapping a basic channel row."""
        row = {
            "id": 1,
            "name": "Test Channel",
            "slug": "test-channel",
            "category": 5,
            "logo": "123e4567-e89b-12d3-a456-426614174000",
            "is_active": True,
        }
        channel = catalog_service._map_channel(row)
        
        assert isinstance(channel, Channel)
        assert channel.id == 1
        assert channel.name == "Test Channel"
        assert channel.slug == "test-channel"
        assert channel.category_id == 5  # Mapped from 'category'
        assert channel.logo == "123e4567-e89b-12d3-a456-426614174000"
        assert channel.is_active is True

    def test_map_channel_nullable_fields(self, catalog_service):
        """Test mapping channel with nullable fields."""
        row = {
            "id": 2,
            "name": "No Category Channel",
            "slug": "no-category",
            "category": None,
            "logo": None,
            "is_active": True,
        }
        channel = catalog_service._map_channel(row)
        
        assert channel.category_id is None
        assert channel.logo is None

    def test_map_channel_inactive(self, catalog_service):
        """Test mapping inactive channel."""
        row = {
            "id": 3,
            "name": "Inactive Channel",
            "slug": "inactive",
            "category": None,
            "logo": None,
            "is_active": False,
        }
        channel = catalog_service._map_channel(row)
        
        assert channel.is_active is False


class TestCategoryMapping:
    """Test category row to model mapping."""

    def test_map_category_basic(self, catalog_service):
        """Test mapping a basic category row."""
        row = {
            "id": 1,
            "name": "Sports",
            "kind": "sports",
            "parent": None,
            "sort_order": 10,
            "is_active": True,
        }
        category = catalog_service._map_category(row)
        
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == "Sports"
        assert category.kind == "sports"
        assert category.parent_id is None  # Mapped from 'parent'
        assert category.sort_order == 10
        assert category.is_active is True

    def test_map_category_with_parent(self, catalog_service):
        """Test mapping category with parent."""
        row = {
            "id": 2,
            "name": "Football",
            "kind": "sports",
            "parent": 1,
            "sort_order": 5,
            "is_active": True,
        }
        category = catalog_service._map_category(row)
        
        assert category.parent_id == 1  # Mapped from 'parent'

    def test_map_category_nullable_parent(self, catalog_service):
        """Test mapping category with nullable parent."""
        row = {
            "id": 3,
            "name": "Root Category",
            "kind": "live",
            "parent": None,
            "sort_order": 0,
            "is_active": True,
        }
        category = catalog_service._map_category(row)
        
        assert category.parent_id is None


class TestMovieMapping:
    """Test movie row to model mapping."""

    def test_map_movie_basic(self, catalog_service):
        """Test mapping a basic movie row."""
        row = {
            "id": 1,
            "title": "Test Movie",
            "slug": "test-movie",
            "category": 5,
            "synopsis": "A test movie",
            "release_year": 2024,
            "is_active": True,
        }
        movie = catalog_service._map_movie(row)
        
        assert isinstance(movie, Movie)
        assert movie.id == 1
        assert movie.title == "Test Movie"
        assert movie.slug == "test-movie"
        assert movie.category_id == 5  # Mapped from 'category'
        assert movie.synopsis == "A test movie"
        assert movie.release_year == 2024
        assert movie.is_active is True

    def test_map_movie_nullable_fields(self, catalog_service):
        """Test mapping movie with nullable fields."""
        row = {
            "id": 2,
            "title": "No Info Movie",
            "slug": "no-info",
            "category": None,
            "synopsis": None,
            "release_year": None,
            "is_active": True,
        }
        movie = catalog_service._map_movie(row)
        
        assert movie.category_id is None
        assert movie.synopsis is None
        assert movie.release_year is None


class TestSeriesMapping:
    """Test series row to model mapping."""

    def test_map_series_basic(self, catalog_service):
        """Test mapping a basic series row."""
        row = {
            "id": 1,
            "title": "Test Series",
            "slug": "test-series",
            "category": 5,
            "synopsis": "A test series",
            "is_active": True,
        }
        series = catalog_service._map_series(row)
        
        assert isinstance(series, Series)
        assert series.id == 1
        assert series.title == "Test Series"
        assert series.slug == "test-series"
        assert series.category_id == 5  # Mapped from 'category'
        assert series.synopsis == "A test series"
        assert series.is_active is True

    def test_map_series_nullable_fields(self, catalog_service):
        """Test mapping series with nullable fields."""
        row = {
            "id": 2,
            "title": "No Info Series",
            "slug": "no-info",
            "category": None,
            "synopsis": None,
            "is_active": True,
        }
        series = catalog_service._map_series(row)
        
        assert series.category_id is None
        assert series.synopsis is None


class TestGetChannels:
    """Test get_channels method."""

    def test_get_channels_all(self, catalog_service, mock_connection):
        """Test getting all channels."""
        mock_rows = [
            {
                "id": 1,
                "name": "Channel 1",
                "slug": "channel-1",
                "category": 5,
                "logo": None,
                "is_active": True,
            },
            {
                "id": 2,
                "name": "Channel 2",
                "slug": "channel-2",
                "category": None,
                "logo": None,
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            channels = catalog_service.get_channels()
        
        assert len(channels) == 2
        assert all(isinstance(c, Channel) for c in channels)
        assert channels[0].name == "Channel 1"
        assert channels[1].name == "Channel 2"

    def test_get_channels_filtered_by_category(self, catalog_service, mock_connection):
        """Test getting channels filtered by category."""
        mock_rows = [
            {
                "id": 1,
                "name": "Sports Channel",
                "slug": "sports-channel",
                "category": 5,
                "logo": None,
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            channels = catalog_service.get_channels(category_id=5)
        
        assert len(channels) == 1
        assert channels[0].category_id == 5
        # Verify query includes category filter
        mock_connection.execute.assert_called_once()
        call_args = mock_connection.execute.call_args[0][0]  # Get the query string
        assert "category = %s" in call_args


class TestGetChannel:
    """Test get_channel method."""

    def test_get_channel_found(self, catalog_service, mock_connection):
        """Test getting a channel that exists."""
        mock_row = {
            "id": 1,
            "name": "Test Channel",
            "slug": "test-channel",
            "category": 5,
            "logo": None,
            "is_active": True,
        }
        
        mock_connection.execute.return_value.fetchone.return_value = mock_row
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            channel = catalog_service.get_channel(1)
        
        assert channel is not None
        assert isinstance(channel, Channel)
        assert channel.id == 1
        assert channel.name == "Test Channel"

    def test_get_channel_not_found(self, catalog_service, mock_connection):
        """Test getting a channel that doesn't exist."""
        mock_connection.execute.return_value.fetchone.return_value = None
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            channel = catalog_service.get_channel(999)
        
        assert channel is None


class TestGetCategories:
    """Test get_categories method."""

    def test_get_categories_all(self, catalog_service, mock_connection):
        """Test getting all categories."""
        mock_rows = [
            {
                "id": 1,
                "name": "Sports",
                "kind": "sports",
                "parent": None,
                "sort_order": 10,
                "is_active": True,
            },
            {
                "id": 2,
                "name": "Movies",
                "kind": "movie",
                "parent": None,
                "sort_order": 20,
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            categories = catalog_service.get_categories()
        
        assert len(categories) == 2
        assert all(isinstance(c, Category) for c in categories)
        assert categories[0].name == "Sports"
        assert categories[1].name == "Movies"

    def test_get_categories_filtered_by_kind(self, catalog_service, mock_connection):
        """Test getting categories filtered by kind."""
        mock_rows = [
            {
                "id": 1,
                "name": "Football",
                "kind": "sports",
                "parent": None,
                "sort_order": 5,
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            categories = catalog_service.get_categories(kind="sports")
        
        assert len(categories) == 1
        assert categories[0].kind == "sports"
        # Verify query includes kind filter
        mock_connection.execute.assert_called_once()
        call_args = mock_connection.execute.call_args[0][0]  # Get the query string
        assert "kind = %s" in call_args


class TestGetCategory:
    """Test get_category method."""

    def test_get_category_found(self, catalog_service, mock_connection):
        """Test getting a category that exists."""
        mock_row = {
            "id": 1,
            "name": "Sports",
            "kind": "sports",
            "parent": None,
            "sort_order": 10,
            "is_active": True,
        }
        
        mock_connection.execute.return_value.fetchone.return_value = mock_row
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            category = catalog_service.get_category(1)
        
        assert category is not None
        assert isinstance(category, Category)
        assert category.id == 1
        assert category.name == "Sports"

    def test_get_category_not_found(self, catalog_service, mock_connection):
        """Test getting a category that doesn't exist."""
        mock_connection.execute.return_value.fetchone.return_value = None
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            category = catalog_service.get_category(999)
        
        assert category is None


class TestGetMovies:
    """Test get_movies method."""

    def test_get_movies_all(self, catalog_service, mock_connection):
        """Test getting all movies."""
        mock_rows = [
            {
                "id": 1,
                "title": "Movie 1",
                "slug": "movie-1",
                "category": 5,
                "synopsis": "Synopsis 1",
                "release_year": 2024,
                "is_active": True,
            },
            {
                "id": 2,
                "title": "Movie 2",
                "slug": "movie-2",
                "category": None,
                "synopsis": None,
                "release_year": None,
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            movies = catalog_service.get_movies()
        
        assert len(movies) == 2
        assert all(isinstance(m, Movie) for m in movies)
        assert movies[0].title == "Movie 1"
        assert movies[1].title == "Movie 2"

    def test_get_movies_filtered_by_category(self, catalog_service, mock_connection):
        """Test getting movies filtered by category."""
        mock_rows = [
            {
                "id": 1,
                "title": "Action Movie",
                "slug": "action-movie",
                "category": 5,
                "synopsis": "Action",
                "release_year": 2024,
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            movies = catalog_service.get_movies(category_id=5)
        
        assert len(movies) == 1
        assert movies[0].category_id == 5


class TestGetMovie:
    """Test get_movie method."""

    def test_get_movie_found(self, catalog_service, mock_connection):
        """Test getting a movie that exists."""
        mock_row = {
            "id": 1,
            "title": "Test Movie",
            "slug": "test-movie",
            "category": 5,
            "synopsis": "Test",
            "release_year": 2024,
            "is_active": True,
        }
        
        mock_connection.execute.return_value.fetchone.return_value = mock_row
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            movie = catalog_service.get_movie(1)
        
        assert movie is not None
        assert isinstance(movie, Movie)
        assert movie.id == 1
        assert movie.title == "Test Movie"

    def test_get_movie_not_found(self, catalog_service, mock_connection):
        """Test getting a movie that doesn't exist."""
        mock_connection.execute.return_value.fetchone.return_value = None
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            movie = catalog_service.get_movie(999)
        
        assert movie is None


class TestGetSeries:
    """Test get_series method."""

    def test_get_series_all(self, catalog_service, mock_connection):
        """Test getting all series."""
        mock_rows = [
            {
                "id": 1,
                "title": "Series 1",
                "slug": "series-1",
                "category": 5,
                "synopsis": "Synopsis 1",
                "is_active": True,
            },
            {
                "id": 2,
                "title": "Series 2",
                "slug": "series-2",
                "category": None,
                "synopsis": None,
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            series_list = catalog_service.get_series()
        
        assert len(series_list) == 2
        assert all(isinstance(s, Series) for s in series_list)
        assert series_list[0].title == "Series 1"
        assert series_list[1].title == "Series 2"

    def test_get_series_filtered_by_category(self, catalog_service, mock_connection):
        """Test getting series filtered by category."""
        mock_rows = [
            {
                "id": 1,
                "title": "Drama Series",
                "slug": "drama-series",
                "category": 5,
                "synopsis": "Drama",
                "is_active": True,
            },
        ]
        
        mock_connection.execute.return_value.fetchall.return_value = mock_rows
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            series_list = catalog_service.get_series(category_id=5)
        
        assert len(series_list) == 1
        assert series_list[0].category_id == 5


class TestGetSeriesById:
    """Test get_series_by_id method."""

    def test_get_series_by_id_found(self, catalog_service, mock_connection):
        """Test getting a series by ID that exists."""
        mock_row = {
            "id": 1,
            "title": "Test Series",
            "slug": "test-series",
            "category": 5,
            "synopsis": "Test",
            "is_active": True,
        }
        
        mock_connection.execute.return_value.fetchone.return_value = mock_row
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            series = catalog_service.get_series_by_id(1)
        
        assert series is not None
        assert isinstance(series, Series)
        assert series.id == 1
        assert series.title == "Test Series"

    def test_get_series_by_id_not_found(self, catalog_service, mock_connection):
        """Test getting a series by ID that doesn't exist."""
        mock_connection.execute.return_value.fetchone.return_value = None
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            series = catalog_service.get_series_by_id(999)
        
        assert series is None


class TestGetHome:
    """Test get_home method."""

    def test_get_home(self, catalog_service, mock_connection):
        """Test getting home page data."""
        mock_categories = [
            {
                "id": 1,
                "name": "Sports",
                "kind": "sports",
                "parent": None,
                "sort_order": 10,
                "is_active": True,
            },
        ]
        mock_channels = [
            {
                "id": 1,
                "name": "Channel 1",
                "slug": "channel-1",
                "category": 5,
                "logo": None,
                "is_active": True,
            },
        ]
        
        # Mock execute to return different results for each call
        execute_results = [mock_categories, mock_channels]
        mock_connection.execute.return_value.fetchall.side_effect = execute_results
        
        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            home_data = catalog_service.get_home()
        
        assert "categories" in home_data
        assert "featured_channels" in home_data
        assert len(home_data["categories"]) == 1
        assert len(home_data["featured_channels"]) == 1
        assert isinstance(home_data["categories"][0], Category)
        assert isinstance(home_data["featured_channels"][0], Channel)
