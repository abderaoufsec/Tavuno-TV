"""Tests for the startup check on published default secrets (M17).

The defaults exist so a fresh clone runs without setup, which is only safe
while they stay inside development. The check is a *warning*, not an error:
refusing to start would convert a configuration smell into an outage and hide
the very log that explains it.
"""

import logging
import os
import unittest

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.config import Settings


def build_settings(**overrides):
    """A Settings instance with only the fields the check reads."""
    base = {
        "POSTGRES_PASSWORD": "test",
        "REDIS_PASSWORD": "test",
    }
    base.update({k: v for k, v in overrides.items()})
    previous = {key: os.environ.get(key) for key in base}
    os.environ.update(base)
    try:
        return Settings()
    finally:
        for key, value in previous.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


class DefaultSecretWarningTests(unittest.TestCase):
    LOGGER = "tavuno-control.config"

    # Fields whose own default in config.py IS the published constant, so they
    # warn with no environment variable set at all.
    FIELDS_DEFAULTING_TO_A_CONSTANT = ("jwt_secret", "playback_token_secret", "ops_token")

    def test_development_environment_is_silent(self):
        """The defaults are correct here — warning would train people to ignore it."""
        with self.assertNoLogs(self.LOGGER, level=logging.WARNING):
            build_settings(TAVUNO_ENV="development")

    def test_test_environment_is_silent(self):
        with self.assertNoLogs(self.LOGGER, level=logging.WARNING):
            build_settings(TAVUNO_ENV="test")

    def test_production_warns_about_fields_that_default_to_a_constant(self):
        with self.assertLogs(self.LOGGER, level=logging.WARNING) as captured:
            build_settings(TAVUNO_ENV="production")
        messages = " ".join(captured.output)
        for field_name in self.FIELDS_DEFAULTING_TO_A_CONSTANT:
            with self.subTest(secret=field_name):
                self.assertIn(field_name.upper(), messages)

    def test_ome_token_is_not_warned_about_until_it_is_supplied(self):
        """config.py leaves it None while compose passes the constant, so the
        check has to catch the *value*, not just the field default — and stay
        quiet when nothing supplied it."""
        with self.assertLogs(self.LOGGER, level=logging.WARNING) as captured:
            build_settings(TAVUNO_ENV="production")
        self.assertNotIn("OME_API_TOKEN", " ".join(captured.output))

    def test_ome_token_default_is_caught_when_supplied(self):
        """config.py leaves it None, but compose passes the published constant,
        so the check has to catch the value and not only the field default."""
        with self.assertLogs(self.LOGGER, level=logging.WARNING) as captured:
            build_settings(TAVUNO_ENV="production", OME_API_TOKEN="tavuno-m1-local")
        self.assertIn("OME_API_TOKEN", " ".join(captured.output))

    def test_overridden_secrets_do_not_warn(self):
        with self.assertNoLogs(self.LOGGER, level=logging.WARNING):
            build_settings(
                TAVUNO_ENV="production",
                JWT_SECRET="a-real-secret",
                PLAYBACK_TOKEN_SECRET="another-real-secret",
                TAVUNO_OPS_TOKEN="yet-another",
                OME_API_TOKEN="also-real",
            )

    def test_a_warning_never_prevents_construction(self):
        """An operator must be able to boot and read the warning."""
        settings = build_settings(TAVUNO_ENV="production")
        self.assertEqual("production", settings.environment)


if __name__ == "__main__":
    unittest.main()