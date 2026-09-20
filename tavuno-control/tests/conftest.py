"""Pytest fixtures for integration tests."""

import pytest
import httpx
from typing import Generator


@pytest.fixture(scope="module")
def api_base():
    """Base URL for Tavuno Control API."""
    return "http://localhost:8000"


@pytest.fixture(scope="module")
def caddy_base():
    """Base URL for Caddy reverse proxy."""
    return "http://localhost:8080"


@pytest.fixture(scope="module")
def dispatcharr_base():
    """Base URL for Dispatcharr."""
    return "http://localhost:9191"


@pytest.fixture(scope="module")
def auth_token(api_base: str) -> str:
    """Skip M9 authentication tests - requires Directus user setup."""
    pytest.skip("M9 authentication tests skipped - requires Directus user with email/password")
