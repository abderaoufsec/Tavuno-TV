"""Live end-to-end check for A6 against the running stack (not part of pytest).

Run manually:  python tools/verify_a6.py
Exits non-zero on the first failed assertion.
"""

import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8000"


def call(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(
        BASE + path, data=data, method=method, headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read().decode()
            return response.status, (json.loads(body) if body else None)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def check(label, condition, detail=""):
    mark = "ok  " if condition else "FAIL"
    print(f"[{mark}] {label}{(' -> ' + str(detail)) if detail else ''}")
    return bool(condition)


def main():
    passed = True

    status, home = call("GET", "/v1/home")
    passed &= check("GET /v1/home -> 200", status == 200, status)
    keys = set(home or {})
    for key in ("categories", "channels", "movies", "series", "continue_watching", "favourites"):
        passed &= check(f"home carries '{key}'", key in keys)
    passed &= check("legacy featured_channels kept", "featured_channels" in keys)

    channels = (home or {}).get("channels") or []
    passed &= check("home has channels", bool(channels), len(channels))
    if not channels:
        print("\nno channels to favourite; aborting")
        return 1

    target = channels[0]
    print(f"\n-- favourites on channel {target['id']} ({target['name']}) --")

    status, state = call("POST", "/v1/favourites", {"kind": "channel", "item_id": target["id"]})
    passed &= check("POST toggle on -> 200", status == 200, status)
    passed &= check("toggle reports is_favourite=true", (state or {}).get("is_favourite") is True)
    passed &= check(
        "toggle echoes the resolved item", (state or {}).get("item", {}).get("id") == target["id"]
    )

    status, listing = call("GET", "/v1/favourites/channel")
    ids = [item["id"] for item in (listing or {}).get("items", [])]
    passed &= check("GET list contains the item", target["id"] in ids, ids)

    status, _ = call("GET", "/v1/channels")
    fresh = json.loads(
        urllib.request.urlopen(BASE + "/v1/channels", timeout=30).read().decode()
    )
    row = next((c for c in fresh if c["id"] == target["id"]), None)
    passed &= check("catalog projects viewer.is_favourite", bool(row and row.get("viewer", {}).get("is_favourite")))

    status, state = call("POST", "/v1/favourites", {"kind": "channel", "item_id": target["id"]})
    passed &= check("second toggle flips back off", (state or {}).get("is_favourite") is False)

    status, _ = call("DELETE", f"/v1/favourites/channel/{target['id']}")
    passed &= check("DELETE -> 200 (idempotent)", status == 200, status)

    status, listing = call("GET", "/v1/favourites/channel")
    passed &= check("list is empty again", (listing or {}).get("items") == [], listing)

    print("\n-- resume --")
    status, payload = call(
        "PUT", "/v1/resume", {"kind": "channel", "item_id": target["id"], "position_ms": 60_000, "duration_ms": 120_000}
    )
    passed &= check("PUT position -> 200", status == 200, status)
    passed &= check("ratio is 0.5", (payload or {}).get("progress") == 0.5, payload)

    status, payload = call("GET", f"/v1/resume/channel/{target['id']}")
    passed &= check("GET position -> 200", status == 200, status)
    passed &= check("position round-trips", (payload or {}).get("position_ms") == 60_000, payload)

    status, listing = call("GET", "/v1/resume")
    kinds = [item.get("kind") for item in (listing or {}).get("items", [])]
    passed &= check("continue-watching rail has it", "channel" in kinds, kinds)

    status, _ = call("GET", "/v1/home")
    home2 = json.loads(urllib.request.urlopen(BASE + "/v1/home", timeout=30).read().decode())
    rail = home2.get("continue_watching") or []
    passed &= check("home continue_watching rail populated", bool(rail), len(rail))

    status, payload = call(
        "PUT", "/v1/resume", {"kind": "channel", "item_id": target["id"], "position_ms": 119_500, "duration_ms": 120_000}
    )
    passed &= check("a finished item answers 204 (cleared)", status == 204, status)

    status, _ = call("GET", f"/v1/resume/channel/{target['id']}")
    passed &= check("a cleared item then 404s", status == 404, status)

    print("\n-- error contract --")
    status, _ = call("GET", "/v1/favourites/nonsense")
    passed &= check("unknown kind -> 400", status == 400, status)
    status, _ = call("GET", "/v1/resume/nonsense/1")
    passed &= check("unknown kind on resume -> 400", status == 400, status)

    print("\nRESULT:", "PASS" if passed else "FAIL")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())