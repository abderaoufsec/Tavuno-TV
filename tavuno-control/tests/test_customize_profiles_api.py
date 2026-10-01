"""Route-level tests for the Slice D customization and profiles endpoints.

The service-level tests cover the queries; these cover the layer above — that the
routes exist, that they read the caller's profile rather than whatever the path
claims, and that an unauthenticated caller gets a 401 instead of a KeyError.
"""

from unittest.mock import MagicMock, patch

import pytest
from fastapi import Depends, HTTPException

from app.auth.deps import current_principal
from app.customize.models import CustomizationItem, CustomizationPayload, CustomizationSet
from app.main import (
    app,
    create_profile,
    delete_customizations,
    delete_profile,
    get_customizations,
    list_profiles,
    profile_id_of,
    put_customizations,
    require_profile_id,
    update_profile,
)
from app.profiles.models import CreateProfileRequest, ProfileSummary, UpdateProfileRequest

OWNER_PRINCIPAL = {"profile_id": 42, "role": "user"}


def registered_routes() -> set:
    routes = set()
    for route in app.routes:
        for method in getattr(route, "methods", []) or []:
            routes.add(f"{method} {route.path}")
    return routes


class TestRouteRegistration:
    @pytest.mark.parametrize(
        "route",
        [
            "GET /v1/customize/{kind}",
            "PUT /v1/customize/{kind}",
            "DELETE /v1/customize/{kind}",
            "GET /v1/profiles",
            "POST /v1/profiles",
            "PATCH /v1/profiles/{profile_id}",
            "DELETE /v1/profiles/{profile_id}",
        ],
    )
    def test_the_endpoint_is_registered(self, route):
        assert route in registered_routes()


class TestPrincipalHelpers:
    def test_a_dict_principal_yields_its_profile(self):
        assert profile_id_of({"profile_id": 42}) == 42

    def test_a_dict_without_a_profile_yields_none(self):
        assert profile_id_of({"role": "admin"}) is None

    def test_an_unresolved_dependency_yields_none(self):
        # ``tests/test_api.py`` calls handlers without FastAPI resolving Depends.
        assert profile_id_of(Depends(current_principal)) is None

    def test_require_raises_401_without_a_profile(self):
        with pytest.raises(HTTPException) as exc:
            require_profile_id({"role": "admin"})
        assert exc.value.status_code == 401

    def test_require_returns_the_profile_id(self):
        assert require_profile_id({"profile_id": 7}) == 7


class TestCustomizeRoutes:
    def test_get_passes_the_callers_profile_to_the_service(self):
        svc = MagicMock()
        svc.return_value.list.return_value = CustomizationSet(kind="live_channel", items=[])
        with patch("app.main.CustomizeService", svc):
            payload = get_customizations("live_channel", MagicMock(), OWNER_PRINCIPAL)

        svc.return_value.list.assert_called_once_with(42, "live_channel")
        assert payload["kind"] == "live_channel"

    def test_put_forwards_the_full_payload_for_replace(self):
        svc = MagicMock()
        svc.return_value.save.return_value = CustomizationSet(
            kind="live_channel",
            items=[CustomizationItem(item_id=5, sort_order=1)],
        )
        payload = CustomizationPayload(items=[CustomizationItem(item_id=5, sort_order=1)])
        with patch("app.main.CustomizeService", svc):
            result = put_customizations("live_channel", payload, MagicMock(), OWNER_PRINCIPAL)

        profile_id, kind, items = svc.return_value.save.call_args[0]
        assert (profile_id, kind) == (42, "live_channel")
        assert [i.item_id for i in items] == [5]
        assert result["items"][0]["item_id"] == 5

    def test_delete_reports_how_many_rows_were_dropped(self):
        svc = MagicMock()
        svc.return_value.reset.return_value = 3
        with patch("app.main.CustomizeService", svc):
            payload = delete_customizations("live_category", MagicMock(), OWNER_PRINCIPAL)

        svc.return_value.reset.assert_called_once_with(42, "live_category")
        assert payload == {"kind": "live_category", "removed": 3}

    def test_unauthenticated_callers_get_401_and_no_lookup(self):
        svc = MagicMock()
        with patch("app.main.CustomizeService", svc):
            with pytest.raises(HTTPException) as exc:
                get_customizations("live_channel", MagicMock(), {"role": "admin"})

        assert exc.value.status_code == 401
        svc.return_value.list.assert_not_called()

    def test_an_unknown_kind_is_reported_as_a_client_error(self):
        svc = MagicMock()
        svc.return_value.list.side_effect = ValueError("unknown customization kind: 'nope'")
        with patch("app.main.CustomizeService", svc):
            with pytest.raises(HTTPException) as exc:
                get_customizations("nope", MagicMock(), OWNER_PRINCIPAL)
        assert exc.value.status_code == 400


class TestProfilesRoutes:
    def test_list_returns_the_callers_family(self):
        svc = MagicMock()
        svc.return_value.list_for.return_value = [
            ProfileSummary(id=42, display_name="Ben", is_owner=True),
            ProfileSummary(id=7, display_name="Mila"),
        ]
        with patch("app.main.ProfilesService", svc):
            payload = list_profiles(MagicMock(), OWNER_PRINCIPAL)

        svc.return_value.list_for.assert_called_once_with(OWNER_PRINCIPAL)
        assert [p["id"] for p in payload] == [42, 7]
        assert payload[0]["is_owner"] is True

    def test_create_is_bound_to_the_callers_account(self):
        svc = MagicMock()
        svc.return_value.create.return_value = ProfileSummary(id=7, display_name="Mila")
        request = CreateProfileRequest(display_name="Mila")
        with patch("app.main.ProfilesService", svc):
            payload = create_profile(request, MagicMock(), OWNER_PRINCIPAL)

        assert svc.return_value.create.call_args[0][0] is OWNER_PRINCIPAL
        assert payload["display_name"] == "Mila"

    def test_delete_maps_not_found_to_404(self):
        svc = MagicMock()
        svc.return_value.delete.side_effect = ValueError("profile_not_found")
        with patch("app.main.ProfilesService", svc):
            with pytest.raises(HTTPException) as exc:
                delete_profile(999, MagicMock(), OWNER_PRINCIPAL)
        assert exc.value.status_code == 404

    def test_update_maps_other_errors_to_400(self):
        svc = MagicMock()
        svc.return_value.update.side_effect = ValueError("nothing to update")
        with patch("app.main.ProfilesService", svc):
            with pytest.raises(HTTPException) as exc:
                update_profile(7, UpdateProfileRequest(), MagicMock(), OWNER_PRINCIPAL)
        assert exc.value.status_code == 400

    def test_listing_profiles_without_a_principal_is_401(self):
        svc = MagicMock()
        with patch("app.main.ProfilesService", svc):
            with pytest.raises(HTTPException) as exc:
                list_profiles(MagicMock(), {"role": "admin"})
        assert exc.value.status_code == 401
        svc.return_value.list_for.assert_not_called()
