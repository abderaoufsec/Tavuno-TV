"""A6: the favourites/resume routes themselves.

The services are unit-tested elsewhere; what matters here is the HTTP contract —
specifically the two failure modes a feature like this introduces: an
unauthenticated caller must be told 401 (not 500, and not quietly handed some
other profile's data), and an unknown content kind must be a 400 rather than a
table lookup against whatever the caller typed.
"""

import pytest

from app.main import delete_favourite, get_resume, list_favourites, list_resume, put_resume
from app.favourites.models import FavouriteToggle
from app.resume.models import ProgressUpdate

from unittest.mock import Mock, patch

PRINCIPAL = {"profile_id": 7}


def services_double():
    return Mock()


class TestFavouriteRoutes:
    def test_listing_requires_a_profile(self):
        with pytest.raises(Exception) as caught:
            list_favourites("movie", services_double(), {})
        assert caught.value.status_code == 401

    def test_an_unknown_kind_is_a_400(self):
        with patch("app.main.FavouritesService") as service:
            service.return_value.list_rows.side_effect = ValueError("unknown content kind")
            with pytest.raises(Exception) as caught:
                list_favourites("nope", services_double(), PRINCIPAL)
        assert caught.value.status_code == 400

    def test_listing_clamps_the_limit(self):
        with patch("app.main.FavouritesService") as service:
            service.return_value.list_rows.return_value = []
            list_favourites("movie", services_double(), PRINCIPAL, limit=10_000)
        # A caller cannot ask for the whole table by asking for a huge page.
        assert service.return_value.list_rows.call_args.kwargs["limit"] <= 200

    def test_delete_is_idempotent(self):
        with patch("app.main.FavouritesService") as service:
            result = delete_favourite("movie", 42, services_double(), PRINCIPAL)
        service.return_value.set.assert_called_once_with(7, "movie", 42, False)
        assert result["is_favourite"] is False


class TestResumeRoutes:
    def test_listing_requires_a_profile(self):
        with pytest.raises(Exception) as caught:
            list_resume(services_double(), {})
        assert caught.value.status_code == 401

    def test_a_missing_position_is_a_404_not_an_empty_200(self):
        with patch("app.main.ResumeService") as service:
            service.return_value.get.return_value = None
            with pytest.raises(Exception) as caught:
                get_resume("movie", 42, services_double(), PRINCIPAL)
        assert caught.value.status_code == 404

    def test_an_unknown_kind_is_a_400(self):
        with patch("app.main.ResumeService") as service:
            service.return_value.get.side_effect = ValueError("unknown content kind")
            with pytest.raises(Exception) as caught:
                get_resume("nope", 1, services_double(), PRINCIPAL)
        assert caught.value.status_code == 400

    def test_a_stored_position_is_returned_with_its_ratio(self):
        from app.resume.models import ProgressPayload

        with patch("app.main.ResumeService") as service:
            service.return_value.get.return_value = ProgressPayload(
                kind="movie", item_id=42, position_ms=10_000, duration_ms=40_000, progress=0.25
            )
            body = get_resume("movie", 42, services_double(), PRINCIPAL)
        assert body["progress"] == 0.25

    def test_reporting_a_position_answers_the_stored_payload(self):
        from app.resume.models import ProgressPayload

        payload = ProgressPayload(
            kind="movie", item_id=42, position_ms=5_000, duration_ms=20_000, progress=0.25
        )
        with patch("app.main.ResumeService") as service:
            service.return_value.record.return_value = payload
            body = put_resume(
                ProgressUpdate(kind="movie", item_id=42, position_ms=5_000, duration_ms=20_000),
                services_double(),
                PRINCIPAL,
            )
        assert body["position_ms"] == 5_000
        assert body["progress"] == 0.25

    def test_a_cleared_position_answers_204_so_the_client_stops_drawing_a_bar(self):
        with patch("app.main.ResumeService") as service:
            service.return_value.record.return_value = None
            response = put_resume(
                ProgressUpdate(kind="movie", item_id=42, position_ms=99_000, duration_ms=100_000),
                services_double(),
                PRINCIPAL,
            )
        assert response.status_code == 204

    def test_a_negative_position_is_rejected_by_the_model(self):
        with pytest.raises(Exception):
            ProgressUpdate(kind="movie", item_id=1, position_ms=-1)