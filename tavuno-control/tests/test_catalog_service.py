"""Unit tests for CatalogService (M11.2)."""

import pytest
from unittest.mock import Mock, MagicMock, patch
from contextlib import contextmanager

from app.catalog.service import CatalogService
from app.catalog.models import Channel, Category, Movie, Series, Competition, Team, Match, MatchDetails


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


# M11 Sports Tests

class TestCompetitionMapping:
    """Test competition row to model mapping (M11)."""

    def test_map_competition_basic(self, catalog_service):
        """Test mapping a basic competition row."""
        row = {
            "id": 1,
            "name": "Premier League",
            "slug": "premier-league",
            "sport": "football",
            "category": 5,
            "external_id": "prem_123",
            "is_active": True,
        }
        competition = catalog_service._map_competition(row)

        assert isinstance(competition, Competition)
        assert competition.id == 1
        assert competition.name == "Premier League"
        assert competition.slug == "premier-league"
        assert competition.sport == "football"
        assert competition.category_id == 5
        assert competition.external_id == "prem_123"
        assert competition.is_active is True

    def test_map_competition_nullable_fields(self, catalog_service):
        """Test mapping competition with nullable fields."""
        row = {
            "id": 2,
            "name": "Custom League",
            "slug": "custom-league",
            "sport": "basketball",
            "category": None,
            "external_id": None,
            "is_active": True,
        }
        competition = catalog_service._map_competition(row)

        assert competition.category_id is None
        assert competition.external_id is None


class TestTeamMapping:
    """Test team row to model mapping (M11)."""

    def test_map_team_basic(self, catalog_service):
        """Test mapping a basic team row."""
        row = {
            "id": 1,
            "name": "Manchester United",
            "slug": "manchester-united",
            "competition": 5,
            "logo": "uuid-123",
            "external_id": "team_456",
            "is_active": True,
        }
        team = catalog_service._map_team(row)

        assert isinstance(team, Team)
        assert team.id == 1
        assert team.name == "Manchester United"
        assert team.slug == "manchester-united"
        assert team.competition_id == 5
        assert team.logo == "uuid-123"
        assert team.external_id == "team_456"
        assert team.is_active is True

    def test_map_team_nullable_fields(self, catalog_service):
        """Test mapping team with nullable fields."""
        row = {
            "id": 2,
            "name": "Independent Team",
            "slug": "independent-team",
            "competition": None,
            "logo": None,
            "external_id": None,
            "is_active": True,
        }
        team = catalog_service._map_team(row)

        assert team.competition_id is None
        assert team.logo is None
        assert team.external_id is None


class TestMatchMapping:
    """Test match row to model mapping (M11)."""

    def test_map_match_basic(self, catalog_service):
        """Test mapping a basic match row."""
        from datetime import datetime, timezone

        kickoff = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        row = {
            "id": 1,
            "competition": 5,
            "home_team": 10,
            "away_team": 11,
            "channel": 20,
            "kickoff": kickoff,
            "status": "upcoming",
            "home_score": None,
            "away_score": None,
            "external_id": "match_789",
            "is_active": True,
        }
        match = catalog_service._map_match(row)

        assert isinstance(match, Match)
        assert match.id == 1
        assert match.competition_id == 5
        assert match.home_team_id == 10
        assert match.away_team_id == 11
        assert match.channel_id == 20
        assert match.status == "upcoming"
        assert match.home_score is None
        assert match.away_score is None
        assert match.external_id == "match_789"
        assert match.is_active is True

    def test_map_match_with_scores(self, catalog_service):
        """Test mapping match with scores."""
        from datetime import datetime, timezone

        kickoff = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        row = {
            "id": 2,
            "competition": 5,
            "home_team": 10,
            "away_team": 11,
            "channel": 20,
            "kickoff": kickoff,
            "status": "live",
            "home_score": 2,
            "away_score": 1,
            "external_id": None,
            "is_active": True,
        }
        match = catalog_service._map_match(row)

        assert match.status == "live"
        assert match.home_score == 2
        assert match.away_score == 1


class TestMatchDetailsMapping:
    """Test match details row to model mapping (M11)."""

    def test_map_match_details_basic(self, catalog_service):
        """Test mapping a basic match details row."""
        from datetime import datetime, timezone

        kickoff = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        row = {
            "id": 1,
            "competition": 5,
            "competition_name": "Premier League",
            "home_team": 10,
            "home_team_name": "Manchester United",
            "home_team_logo": "uuid-123",
            "away_team": 11,
            "away_team_name": "Chelsea",
            "away_team_logo": "uuid-456",
            "channel": 20,
            "channel_name": "Sports 1",
            "kickoff": kickoff,
            "status": "upcoming",
            "home_score": None,
            "away_score": None,
            "is_active": True,
        }
        match_details = catalog_service._map_match_details(row)

        assert isinstance(match_details, MatchDetails)
        assert match_details.id == 1
        assert match_details.competition_name == "Premier League"
        assert match_details.home_team_name == "Manchester United"
        assert match_details.away_team_name == "Chelsea"
        assert match_details.channel_name == "Sports 1"
        assert match_details.status == "upcoming"


class TestGetCompetitions:
    """Test get_competitions method (M11)."""

    def test_get_competitions_all(self, catalog_service, mock_connection):
        """Test getting all competitions."""
        mock_rows = [
            {
                "id": 1,
                "name": "Premier League",
                "slug": "premier-league",
                "sport": "football",
                "category": 5,
                "external_id": None,
                "is_active": True,
            },
            {
                "id": 2,
                "name": "La Liga",
                "slug": "la-liga",
                "sport": "football",
                "category": 5,
                "external_id": None,
                "is_active": True,
            },
        ]

        mock_connection.execute.return_value.fetchall.return_value = mock_rows

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            competitions = catalog_service.get_competitions()

        assert len(competitions) == 2
        assert all(isinstance(c, Competition) for c in competitions)
        assert competitions[0].name == "Premier League"
        assert competitions[1].name == "La Liga"

    def test_get_competitions_filtered_by_sport(self, catalog_service, mock_connection):
        """Test getting competitions filtered by sport."""
        mock_rows = [
            {
                "id": 1,
                "name": "Premier League",
                "slug": "premier-league",
                "sport": "football",
                "category": 5,
                "external_id": None,
                "is_active": True,
            },
        ]

        mock_connection.execute.return_value.fetchall.return_value = mock_rows

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            competitions = catalog_service.get_competitions(sport="football")

        assert len(competitions) == 1
        assert competitions[0].sport == "football"


class TestGetCompetition:
    """Test get_competition method (M11)."""

    def test_get_competition_found(self, catalog_service, mock_connection):
        """Test getting a competition that exists."""
        mock_row = {
            "id": 1,
            "name": "Premier League",
            "slug": "premier-league",
            "sport": "football",
            "category": 5,
            "external_id": None,
            "is_active": True,
        }

        mock_connection.execute.return_value.fetchone.return_value = mock_row

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            competition = catalog_service.get_competition(1)

        assert competition is not None
        assert isinstance(competition, Competition)
        assert competition.id == 1
        assert competition.name == "Premier League"

    def test_get_competition_not_found(self, catalog_service, mock_connection):
        """Test getting a competition that doesn't exist."""
        mock_connection.execute.return_value.fetchone.return_value = None

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            competition = catalog_service.get_competition(999)

        assert competition is None


class TestGetMatches:
    """Test get_matches method (M11)."""

    def test_get_matches_all(self, catalog_service, mock_connection):
        """Test getting all matches."""
        from datetime import datetime, timezone

        kickoff = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        mock_rows = [
            {
                "id": 1,
                "competition": 5,
                "home_team": 10,
                "away_team": 11,
                "channel": 20,
                "kickoff": kickoff,
                "status": "upcoming",
                "home_score": None,
                "away_score": None,
                "external_id": None,
                "is_active": True,
            },
        ]

        mock_connection.execute.return_value.fetchall.return_value = mock_rows

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            matches = catalog_service.get_matches()

        assert len(matches) == 1
        assert all(isinstance(m, Match) for m in matches)

    def test_get_matches_filtered_by_status(self, catalog_service, mock_connection):
        """Test getting matches filtered by status."""
        from datetime import datetime, timezone

        kickoff = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        mock_rows = [
            {
                "id": 1,
                "competition": 5,
                "home_team": 10,
                "away_team": 11,
                "channel": 20,
                "kickoff": kickoff,
                "status": "live",
                "home_score": 2,
                "away_score": 1,
                "external_id": None,
                "is_active": True,
            },
        ]

        mock_connection.execute.return_value.fetchall.return_value = mock_rows

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            matches = catalog_service.get_matches(status="live")

        assert len(matches) == 1
        assert matches[0].status == "live"


class TestGetMatch:
    """Test get_match method (M11)."""

    def test_get_match_found(self, catalog_service, mock_connection):
        """Test getting a match that exists."""
        from datetime import datetime, timezone

        kickoff = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        mock_row = {
            "id": 1,
            "competition": 5,
            "home_team": 10,
            "away_team": 11,
            "channel": 20,
            "kickoff": kickoff,
            "status": "upcoming",
            "home_score": None,
            "away_score": None,
            "external_id": None,
            "is_active": True,
        }

        mock_connection.execute.return_value.fetchone.return_value = mock_row

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            match = catalog_service.get_match(1)

        assert match is not None
        assert isinstance(match, Match)
        assert match.id == 1

    def test_get_match_not_found(self, catalog_service, mock_connection):
        """Test getting a match that doesn't exist."""
        mock_connection.execute.return_value.fetchone.return_value = None

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            match = catalog_service.get_match(999)

        assert match is None


class TestGetMatchDetails:
    """Test get_match_details method (M11)."""

    def test_get_match_details_found(self, catalog_service, mock_connection):
        """Test getting match details that exist."""
        from datetime import datetime, timezone

        kickoff = datetime(2024, 6, 15, 15, 0, 0, tzinfo=timezone.utc)
        mock_row = {
            "id": 1,
            "competition": 5,
            "competition_name": "Premier League",
            "home_team": 10,
            "home_team_name": "Manchester United",
            "home_team_logo": "uuid-123",
            "away_team": 11,
            "away_team_name": "Chelsea",
            "away_team_logo": "uuid-456",
            "channel": 20,
            "channel_name": "Sports 1",
            "kickoff": kickoff,
            "status": "upcoming",
            "home_score": None,
            "away_score": None,
            "is_active": True,
        }

        mock_connection.execute.return_value.fetchone.return_value = mock_row

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            match_details = catalog_service.get_match_details(1)

        assert match_details is not None
        assert isinstance(match_details, MatchDetails)
        assert match_details.id == 1
        assert match_details.competition_name == "Premier League"

    def test_get_match_details_not_found(self, catalog_service, mock_connection):
        """Test getting match details that don't exist."""
        mock_connection.execute.return_value.fetchone.return_value = None

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            match_details = catalog_service.get_match_details(999)

        assert match_details is None


class TestLiveChannelTestScope:
    """Test the reversible live-channel test scope (TAVUNO_LIVE_CHANNEL_*)."""

    def test_default_scope_is_unrestricted(self, catalog_service):
        """Without configuration the whole synced catalog is exposed."""
        # mock_services is a Mock, so settings.live_channel_* are Mocks too: the
        # scope helpers must coerce them to "no restriction" rather than crash.
        assert catalog_service._live_channel_scope() == ("", [])
        assert catalog_service._live_channel_limit() == 0

    def test_allowlist_of_ids_builds_any_clause(self, catalog_service):
        catalog_service.services.settings.live_channel_allowlist = "8845, 6860,6869"
        catalog_service.services.settings.live_channel_limit = 3

        clause, params = catalog_service._live_channel_scope()

        assert clause == " AND (id = ANY(%s))"
        assert params == [[8845, 6860, 6869]]
        assert catalog_service._live_channel_limit() == 3

    def test_allowlist_accepts_slugs_too(self, catalog_service):
        catalog_service.services.settings.live_channel_allowlist = "10-sydney, 24h"

        clause, params = catalog_service._live_channel_scope()

        assert clause == " AND (slug = ANY(%s))"
        assert params == [["10-sydney", "24h"]]

    def test_blank_allowlist_and_zero_limit_is_unrestricted(self, catalog_service):
        catalog_service.services.settings.live_channel_allowlist = "   "
        catalog_service.services.settings.live_channel_limit = 0

        assert catalog_service._live_channel_scope() == ("", [])
        assert catalog_service._live_channel_limit() == 0

    def test_get_channels_applies_scope_and_limit(self, catalog_service, mock_connection):
        """The scope must reach the SQL so the app only lists the test set."""
        catalog_service.services.settings.live_channel_allowlist = "8845,6860"
        catalog_service.services.settings.live_channel_limit = 2
        mock_connection.execute.return_value.fetchall.return_value = []

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            catalog_service.get_channels()

        query, params = mock_connection.execute.call_args[0]
        assert "id = ANY(%s)" in query
        assert "LIMIT %s" in query
        assert params == ([8845, 6860], 2)

    def test_get_channels_keeps_category_filter_with_scope(self, catalog_service, mock_connection):
        catalog_service.services.settings.live_channel_allowlist = "8845"
        catalog_service.services.settings.live_channel_limit = 1
        mock_connection.execute.return_value.fetchall.return_value = []

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            catalog_service.get_channels(category_id=5)

        query, params = mock_connection.execute.call_args[0]
        assert "category = %s" in query
        assert "id = ANY(%s)" in query
        assert params == (5, [8845], 1)

    def test_get_channels_without_scope_is_unchanged(self, catalog_service, mock_connection):
        """No scope configured -> the historic query/params shape is preserved."""
        mock_connection.execute.return_value.fetchall.return_value = []

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            catalog_service.get_channels(category_id=5)

        query, params = mock_connection.execute.call_args[0]
        assert "id = ANY(%s)" not in query
        assert "LIMIT %s" not in query
        assert params == (5,)

    def test_get_home_limit_doubles_as_rail_size(self, catalog_service, mock_connection):
        """A 10-channel test scope must not advertise 12 featured channels."""
        catalog_service.services.settings.live_channel_allowlist = "8845"
        catalog_service.services.settings.live_channel_limit = 10
        mock_connection.execute.return_value.fetchall.side_effect = [[], []]

        with patch.object(catalog_service, '_db') as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            catalog_service.get_home()

        channel_query, channel_params = mock_connection.execute.call_args_list[1][0]
        assert "id = ANY(%s)" in channel_query
        assert channel_params == ([8845], 10)


class TestCatalogSearch:
    """Search (Slice B): grouped, scoped, wildcard-safe."""

    @staticmethod
    def _run(catalog_service, mock_connection, query, **kwargs):
        with patch.object(catalog_service, "_db") as mock_db:
            mock_db.return_value.__enter__.return_value = mock_connection
            return catalog_service.search(query, **kwargs)

    def test_blank_query_short_circuits_without_touching_the_db(self, catalog_service):
        mock_connection = MagicMock()

        result = self._run(catalog_service, mock_connection, "   ")

        assert result == {"query": "", "channels": [], "movies": [], "series": []}
        mock_connection.execute.assert_not_called()

    def test_search_queries_each_collection_with_ilike(self, catalog_service):
        mock_connection = MagicMock()
        mock_connection.execute.return_value.fetchall.return_value = []

        result = self._run(catalog_service, mock_connection, "news", limit=10)

        assert result["query"] == "news"
        assert mock_connection.execute.call_count == 3
        channel_query, channel_params = mock_connection.execute.call_args_list[0][0]
        assert "name ILIKE %s" in channel_query
        assert channel_query.endswith("ORDER BY name LIMIT %s")
        assert channel_params == ("%news%", 10)
        movie_query, movie_params = mock_connection.execute.call_args_list[1][0]
        assert "title ILIKE %s" in movie_query
        assert movie_params == ("%news%", 10)
        series_query, _ = mock_connection.execute.call_args_list[2][0]
        assert "title ILIKE %s" in series_query

    def test_search_is_case_insensitive_and_matches_infixes(self, catalog_service):
        mock_connection = MagicMock()
        mock_connection.execute.return_value.fetchall.return_value = []

        self._run(catalog_service, mock_connection, "NeWs")

        _, params = mock_connection.execute.call_args_list[0][0]
        # ILIKE supplies the case folding; the pattern is a plain infix match.
        assert params[0] == "%NeWs%"

    def test_search_escapes_like_wildcards(self, catalog_service):
        mock_connection = MagicMock()
        mock_connection.execute.return_value.fetchall.return_value = []

        self._run(catalog_service, mock_connection, "100% ")

        _, params = mock_connection.execute.call_args_list[0][0]
        assert params[0] == "%100\\%%"

    def test_search_applies_live_channel_scope_and_scope_limit(self, catalog_service):
        catalog_service.services.settings.live_channel_allowlist = "8845,6860"
        catalog_service.services.settings.live_channel_limit = 2
        mock_connection = MagicMock()
        mock_connection.execute.return_value.fetchall.return_value = []

        self._run(catalog_service, mock_connection, "news", limit=20)

        channel_query, channel_params = mock_connection.execute.call_args_list[0][0]
        assert "id = ANY(%s)" in channel_query
        # pattern, allowlist ids, then the cap — clamped by the test-scope limit.
        assert channel_params == ("%news%", [8845, 6860], 2)

    def test_search_limit_is_clamped(self, catalog_service):
        mock_connection = MagicMock()
        mock_connection.execute.return_value.fetchall.return_value = []

        self._run(catalog_service, mock_connection, "a", limit=500)

        for call in mock_connection.execute.call_args_list:
            assert call[0][1][-1] == 50

    def test_search_maps_rows_to_models(self, catalog_service):
        channel_row = {"id": 1, "name": "News 24", "slug": "news-24", "category": 5,
                       "logo": None, "is_active": True}
        movie_row = {"id": 2, "title": "Newsroom", "slug": "newsroom", "category": None,
                     "synopsis": None, "release_year": 2020, "is_active": True}
        series_row = {"id": 3, "title": "Newsroom Tonight", "slug": "newsroom-tonight",
                      "category": None, "synopsis": None, "is_active": True}
        mock_connection = MagicMock()
        mock_connection.execute.return_value.fetchall.side_effect = [
            [channel_row], [movie_row], [series_row],
        ]

        result = self._run(catalog_service, mock_connection, "news")

        assert isinstance(result["channels"][0], Channel)
        assert isinstance(result["movies"][0], Movie)
        assert isinstance(result["series"][0], Series)
        assert result["channels"][0].name == "News 24"
        assert result["movies"][0].title == "Newsroom"
        assert result["series"][0].title == "Newsroom Tonight"
