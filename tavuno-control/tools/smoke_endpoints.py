"""Smoke every endpoint the Android client calls, against the running API.

Run manually:  python tools/smoke_endpoints.py
Exits non-zero if any endpoint regressed to a non-2xx.
"""

import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8000"

PATHS = [
    "/v1/home",
    "/v1/channels",
    "/v1/channels/8845",
    "/v1/channels/8845/details",
    "/v1/categories",
    "/v1/categories?kind=live",
    "/v1/search?q=sydney",
    "/v1/movies",
    "/v1/series",
    "/v1/epg?channel_id=8845",
    "/v1/epg/channel/8845/now-next",
    "/v1/sports/competitions",
    "/v1/sports/matches",
    "/v1/customize/live_channel",
    "/v1/profiles",
    "/v1/favourites/channel",
    "/v1/resume",
    "/v1/dispatcharr/health",
    "/v1/ome/health",
]


def main():
    failures = 0
    for path in PATHS:
        try:
            with urllib.request.urlopen(BASE + path, timeout=30) as response:
                body = response.read().decode()
                shape = type(json.loads(body)).__name__ if body else "empty"
                print(f"[ok  ] {response.status} {path} -> {shape}")
        except urllib.error.HTTPError as error:
            failures += 1
            print(f"[FAIL] {error.code} {path}")
        except Exception as error:  # noqa: BLE001 - a smoke test wants them all
            failures += 1
            print(f"[FAIL] {path} -> {error}")

    print(f"\n{len(PATHS) - failures}/{len(PATHS)} endpoints answered 2xx")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())