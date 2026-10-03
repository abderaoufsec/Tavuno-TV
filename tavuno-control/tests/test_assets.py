"""A6: asset-URL normalization (Directus UUID -> /assets/<uuid>).

Pure functions, so these need no database and no HTTP client. What is pinned
here is the rule that decides whether a stored artwork reference is usable at
all: Directus stores a relation to a ``directus_files`` row, so the column holds
a UUID, and a UUID handed to an image loader is a silent empty tile.
"""

from uuid import UUID

from app.assets import asset_base_url, asset_url, is_url, is_uuid

UUID_A = "3f6b1c2a-9d4e-4f7b-8a1d-2c5e6b7a8c9d"
UUID_B = "11111111-2222-3333-4444-555555555555"


class TestRecognition:
    def test_a_bare_uuid_is_recognized(self):
        assert is_uuid(UUID_A)

    def test_a_url_is_not_a_uuid(self):
        assert not is_uuid("https://example.test/assets/x.jpg")

    def test_the_scheme_list_is_the_only_thing_treated_as_self_addressing(self):
        for candidate in ("https://x/y", "http://x/y", "//cdn/x.jpg", "data:image/png;base64,AA"):
            assert is_url(candidate), candidate

    def test_a_relative_path_is_not_treated_as_self_addressing(self):
        # It needs prefixing/joining, so it must not short-circuit as a URL.
        assert not is_url("/assets/local.png")

    def test_non_strings_are_neither(self):
        assert not is_uuid(None)
        assert not is_uuid(42)
        assert not is_url(None)


class TestBaseUrl:
    def test_trailing_slashes_are_normalized_away(self):
        assert asset_base_url("https://media.test/directus/") == "https://media.test/directus/assets"

    def test_a_bare_host_gets_a_scheme_so_its_netloc_is_found(self):
        # urlsplit("directus:8055").netloc is "" without this.
        assert asset_base_url("directus:8055") == "https://directus:8055/assets"

    def test_a_full_origin_with_app_path_keeps_both(self):
        assert (
            asset_base_url("https://tavuno.test/directus")
            == "https://tavuno.test/directus/assets"
        )

    def test_blank_and_none_yield_nothing_rather_than_a_broken_prefix(self):
        assert asset_base_url("") == ""
        assert asset_base_url(None) == ""
        assert asset_base_url("   ") == ""


class TestAssetUrl:
    def test_a_uuid_becomes_an_asset_url(self):
        assert asset_url(UUID_A, "https://media.test/directus") == (
            f"https://media.test/directus/assets/{UUID_A}"
        )

    def test_an_existing_url_is_passed_through_untouched(self):
        for candidate in ("https://cdn.test/p.jpg", "//cdn.test/p.jpg", "/local/p.jpg"):
            assert asset_url(candidate, "https://media.test/directus") == candidate

    def test_no_base_url_leaves_a_uuid_alone_rather_than_inventing_a_path(self):
        # Better a UUID the client can recognise as unusable than a relative
        # "/assets/..." that resolves against the API host.
        assert asset_url(UUID_A, "") == UUID_A
        assert asset_url(UUID_A, None) == UUID_A

    def test_missing_artwork_stays_missing(self):
        assert asset_url(None, "https://media.test/directus") is None
        assert asset_url("", "https://media.test/directus") is None
        assert asset_url("   ", "https://media.test/directus") is None

    def test_non_string_input_is_ignored_rather_than_stringified(self):
        assert asset_url(12345, "https://media.test/directus") is None

    def test_two_different_uuids_get_two_different_urls(self):
        base = "https://media.test/directus"
        assert asset_url(UUID_A, base) != asset_url(UUID_B, base)


class TestPostgresUuidObjects:
    """Regression: a ``uuid`` column comes back from psycopg as ``uuid.UUID``.

    Every other test here feeds strings, which is how the original bug slipped
    through: the mapping looked right and silently returned ``None`` for every
    real row in the database.
    """

    def test_a_uuid_object_becomes_an_asset_url(self):
        value = UUID(UUID_A)
        assert asset_url(value, "https://media.test/directus") == (
            f"https://media.test/directus/assets/{UUID_A}"
        )

    def test_a_uuid_object_is_recognized_as_one(self):
        assert is_uuid(UUID(UUID_A))
        assert not is_url(UUID(UUID_A))

    def test_a_uuid_object_without_a_base_stays_the_uuid(self):
        assert asset_url(UUID(UUID_A), "") == UUID_A