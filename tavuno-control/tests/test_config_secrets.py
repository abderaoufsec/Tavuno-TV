"""Tests for the startup check on published default secrets (M17/F-2).

The defaults exist so a fresh clone runs without setup, which is only safe while
they stay inside development. The check is tiered:

- development/test -> silent, so a fresh clone stays usable and the warning does
  not become background noise.
- any other non-development, non-production environment -> warn and still build,
  because an operator has to be able to boot and read the log that explains the
  fix; converting a configuration smell into an outage hides that log.
- production -> refuse. A published default means anyone who has read the
  repository can forge JWTs, playback tokens, guest identity and ops access on
  the deployed instance, so refusing to start is the better failure mode.
"""

import logging
import os
import unittest

os.environ.setdefault("POSTGRES_PASSWORD", "test")
os.environ.setdefault("REDIS_PASSWORD", "test")

from app.config import _INSECURE_DEFAULT_SECRETS, Settings

# The exact constant config.py publishes, plus the decorated copy the deployed
# tavuno-infra/.env actually carries. An equality-only check missed the second.
PUBLISHED_JWT_SECRET = _INSECURE_DEFAULT_SECRETS["jwt_secret"]
DERIVED_JWT_SECRET = PUBLISHED_JWT_SECRET + "-use-random-64-hex-in-production"


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


def overridden_secrets():
    """Every guarded secret set to something that is not published."""
    return {
        "JWT_SECRET": "a-real-secret",
        "PLAYBACK_TOKEN_SECRET": "another-real-secret",
        "TAVUNO_OPS_TOKEN": "yet-another",
        "OME_API_TOKEN": "also-real",
        "AUTH_GUEST_DEVICE_KEY": "and-this-one",
    }


class DevEnvironmentsAreSilentTests(unittest.TestCase):
    LOGGER = "tavuno-control.config"

    def test_development_environment_is_silent(self):
        """The defaults are correct here — warning would train people to ignore it."""
        with self.assertNoLogs(self.LOGGER, level=logging.WARNING):
            build_settings(TAVUNO_ENV="development")

    def test_test_environment_is_silent(self):
        with self.assertNoLogs(self.LOGGER, level=logging.WARNING):
            build_settings(TAVUNO_ENV="test")


class ProductionRefusesDefaultSecretsTests(unittest.TestCase):
    def test_production_refuses_published_defaults(self):
        """The whole point of the check: do not boot with forgeable secrets."""
        with self.assertRaises(ValueError) as caught:
            build_settings(TAVUNO_ENV="production")
        message = str(caught.exception)
        self.assertIn("Refusing to start", message)
        # Four of the five have a field default, so they are named with no
        # environment variable supplied at all.
        for field_name in ("jwt_secret", "playback_token_secret", "ops_token", "auth_guest_device_key"):
            with self.subTest(secret=field_name):
                self.assertIn(field_name.upper(), message)

    def test_decorated_default_is_refused(self):
        """Regression: the deployed value is the constant with a suffix appended.

        The pre-F-2 check compared with `==`, so the 74-character value in
        tavuno-infra/.env never matched the 42-character constant and the guard
        stayed silent while the secret was still published in the repository.
        """
        with self.assertRaises(ValueError) as caught:
            build_settings(TAVUNO_ENV="production", JWT_SECRET=DERIVED_JWT_SECRET)
        self.assertIn("JWT_SECRET", str(caught.exception))

    def test_guest_device_key_is_guarded_on_its_own(self):
        """The fifth secret: its default is a published constant like the rest."""
        secrets = overridden_secrets()
        secrets.pop("AUTH_GUEST_DEVICE_KEY")
        with self.assertRaises(ValueError) as caught:
            build_settings(TAVUNO_ENV="production", **secrets)
        self.assertIn("AUTH_GUEST_DEVICE_KEY", str(caught.exception))

    def test_change_me_placeholder_is_refused_in_production(self):
        """A forgotten .env.example placeholder is guessable the same way."""
        secrets = overridden_secrets()
        secrets["JWT_SECRET"] = "CHANGE_ME_USE_RANDOM_64_HEX"
        with self.assertRaises(ValueError) as caught:
            build_settings(TAVUNO_ENV="production", **secrets)
        self.assertIn("JWT_SECRET", str(caught.exception))

    def test_overridden_secrets_start_in_production(self):
        """Rotated secrets boot; only published ones are refused."""
        with self.assertNoLogs("tavuno-control.config", level=logging.WARNING):
            settings = build_settings(TAVUNO_ENV="production", **overridden_secrets())
        self.assertEqual("production", settings.environment)


class NonProductionWarnsWithoutBlockingTests(unittest.TestCase):
    LOGGER = "tavuno-control.config"

    def test_staging_warns_but_still_constructs(self):
        """An operator must be able to boot and read the warning."""
        with self.assertLogs(self.LOGGER, level=logging.WARNING) as captured:
            settings = build_settings(TAVUNO_ENV="staging")
        self.assertEqual("staging", settings.environment)
        messages = " ".join(captured.output)
        for field_name in ("jwt_secret", "playback_token_secret", "ops_token", "auth_guest_device_key"):
            with self.subTest(secret=field_name):
                self.assertIn(field_name.upper(), messages)

    def test_ome_token_is_not_warned_about_until_it_is_supplied(self):
        """config.py leaves it None while compose passes the constant, so the
        check has to catch the *value*, not just the field default — and stay
        quiet when nothing supplied it."""
        with self.assertLogs(self.LOGGER, level=logging.WARNING) as captured:
            build_settings(TAVUNO_ENV="staging")
        self.assertNotIn("OME_API_TOKEN", " ".join(captured.output))

    def test_ome_token_default_is_caught_when_supplied(self):
        """config.py leaves it None, but compose passes the published constant,
        so the check has to catch the value and not only the field default."""
        with self.assertLogs(self.LOGGER, level=logging.WARNING) as captured:
            build_settings(TAVUNO_ENV="staging", OME_API_TOKEN="tavuno-m1-local")
        self.assertIn("OME_API_TOKEN", " ".join(captured.output))

    def test_overridden_secrets_do_not_warn(self):
        with self.assertNoLogs(self.LOGGER, level=logging.WARNING):
            build_settings(TAVUNO_ENV="staging", **overridden_secrets())


if __name__ == "__main__":
    unittest.main()