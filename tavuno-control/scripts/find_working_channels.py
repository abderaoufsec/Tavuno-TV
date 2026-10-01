#!/usr/bin/env python3
"""Find live channels whose upstream stream actually plays, for test scoping.

The Tavuno playback endpoint already verifies reachability: it only returns
``protocol == "http_hls"`` when it could fetch a real media/HLS payload from the
Dispatcharr stream URL. Anything unreachable falls back to the OME relay, so
"answered with http_hls" is a trustworthy "this stream plays right now" signal.

This script walks the catalog, keeps the first N channels that pass, then writes
``TAVUNO_LIVE_CHANNEL_ALLOWLIST`` / ``TAVUNO_LIVE_CHANNEL_LIMIT`` into the infra
``.env`` so the app can be tested against a small, working set.

Undo the test scope at any time by clearing those two values (or passing
``--clear``), which restores the full synced catalog.

Usage:
    python find_working_channels.py                 # find 10, write ../.env
    python find_working_channels.py --limit 25
    python find_working_channels.py --scan 400 --workers 32
    python find_working_channels.py --clear         # lift the test scope
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any

try:  # Keep channel names readable on a cp1252 Windows console.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # pragma: no cover - non-reconfigurable streams
    pass

ALLOWLIST_KEY = "TAVUNO_LIVE_CHANNEL_ALLOWLIST"
LIMIT_KEY = "TAVUNO_LIVE_CHANNEL_LIMIT"

DEFAULT_BASE_URL = "http://localhost:8000"
DEFAULT_ENV_PATH = Path(__file__).resolve().parents[2] / "tavuno-infra" / ".env"


def _request(method: str, url: str, timeout: float) -> tuple[int, Any]:
    """Perform a JSON request, returning (status_code, parsed_body|None)."""
    data = b"{}" if method == "POST" else None
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "User-Agent": "TavunoTV/1.0 (channel-scope probe)",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            try:
                return response.status, json.loads(raw) if raw else None
            except json.JSONDecodeError:
                return response.status, None
    except urllib.error.HTTPError as error:
        return error.code, None
    except Exception:
        return 0, None


def fetch_channels(base_url: str, timeout: float) -> list[dict[str, Any]]:
    status, body = _request("GET", f"{base_url}/v1/channels", timeout)
    if status != 200 or not isinstance(body, list):
        raise SystemExit(f"Could not list channels from {base_url}/v1/channels (status {status})")
    return [channel for channel in body if isinstance(channel, dict) and channel.get("id") is not None]


def probe_channel(base_url: str, channel: dict[str, Any], timeout: float) -> dict[str, Any] | None:
    """Return the channel when its upstream stream is verified playable."""
    status, body = _request("POST", f"{base_url}/v1/playback/live/{channel['id']}", timeout)
    if status != 200 or not isinstance(body, dict):
        return None
    playback = body.get("playback") or {}
    # http_hls == direct, verified-playable upstream. "hls" means the OME relay
    # fallback, i.e. the upstream was not reachable.
    if playback.get("protocol") != "http_hls":
        return None
    session_id = body.get("session_id")
    if session_id is not None:
        # Do not leave a probe session behind.
        _request("POST", f"{base_url}/v1/playback/stop", timeout)
    return {
        "id": int(channel["id"]),
        "name": channel.get("name") or "",
        "url": playback.get("url") or "",
    }


def scan(base_url: str, channel_count: int, limit: int, workers: int, timeout: float) -> list[dict[str, Any]]:
    channels = fetch_channels(base_url, timeout)
    window = channels[:channel_count] if channel_count else channels
    print(f"Scanning {len(window)} of {len(channels)} channels for {limit} working streams...")

    working: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(probe_channel, base_url, channel, timeout): channel for channel in window}
        try:
            for future in as_completed(futures):
                result = future.result()
                if not result:
                    continue
                working.append(result)
                print(f"  [ok] {result['id']:>6}  {result['name']}  ->  {result['url']}")
                if len(working) >= limit:
                    break
        finally:
            for future in futures:
                future.cancel()

    # Report in catalog order so the app's list looks deliberate.
    order = {channel["id"]: index for index, channel in enumerate(channels)}
    working.sort(key=lambda item: order.get(item["id"], 0))
    return working


def upsert_env(env_path: Path, values: dict[str, str]) -> None:
    lines = env_path.read_text(encoding="utf-8").splitlines() if env_path.exists() else []
    remaining = dict(values)
    output: list[str] = []
    for line in lines:
        key = line.split("=", 1)[0].strip() if "=" in line else None
        if key in remaining:
            output.append(f"{key}={remaining.pop(key)}")
        else:
            output.append(line)
    for key, value in remaining.items():
        output.append(f"{key}={value}")
    env_path.write_text("\n".join(output).rstrip("\n") + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Tavuno control API base URL")
    parser.add_argument("--limit", type=int, default=10, help="How many working channels to keep")
    parser.add_argument("--scan", type=int, default=250, help="Max channels to probe (0 = all)")
    parser.add_argument("--workers", type=int, default=24, help="Parallel probes")
    parser.add_argument("--timeout", type=float, default=30.0, help="Per-request timeout in seconds")
    parser.add_argument("--env", type=Path, default=DEFAULT_ENV_PATH, help="Infra .env to update")
    parser.add_argument("--no-write", action="store_true", help="Only report; do not touch .env")
    parser.add_argument("--clear", action="store_true", help="Lift the test scope (restore every channel)")
    args = parser.parse_args()

    if args.clear:
        upsert_env(args.env, {ALLOWLIST_KEY: "", LIMIT_KEY: "0"})
        print(f"Test scope cleared in {args.env}. Restart tavuno-control to expose the full catalog.")
        return 0

    working = scan(args.base_url, args.scan, max(args.limit, 1), max(args.workers, 1), args.timeout)
    if not working:
        print("No verified-working channels found in the scanned window.", file=sys.stderr)
        return 1

    ids = ",".join(str(item["id"]) for item in working)
    print(f"\nFound {len(working)} verified-working channel(s):")
    for item in working:
        print(f"  {item['id']:>6}  {item['name']}")

    if args.no_write:
        print(f"\n{ALLOWLIST_KEY}={ids}")
        print(f"{LIMIT_KEY}={len(working)}")
        return 0

    upsert_env(args.env, {ALLOWLIST_KEY: ids, LIMIT_KEY: str(len(working))})
    print(f"\nWrote {ALLOWLIST_KEY} ({len(working)} ids) to {args.env}")
    print("Restart tavuno-control to apply: docker compose up -d tavuno-control")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())