#!/usr/bin/env python3
"""Test to check all available routes"""

import requests

BASE_URL = "http://localhost:8000"

print("Checking all available routes...")

response = requests.get(f"{BASE_URL}/openapi.json")
if response.status_code == 200:
    openapi = response.json()
    paths = openapi.get("paths", {})
    
    print(f"\nTotal endpoints found: {len(paths)}")
    
    print("\nAuth endpoints:")
    for path in sorted(paths.keys()):
        if "/auth" in path:
            methods = list(paths[path].keys())
            print(f"  {path} - {methods}")
    
    print("\nCatalog endpoints:")
    for path in sorted(paths.keys()):
        if "/channels" in path or "/categories" in path or "/movies" in path or "/series" in path or "/sports" in path:
            methods = list(paths[path].keys())
            print(f"  {path} - {methods}")
    
    print("\nPlayback endpoints:")
    for path in sorted(paths.keys()):
        if "/playback" in path:
            methods = list(paths[path].keys())
            print(f"  {path} - {methods}")
    
    print("\nDevice endpoints:")
    for path in sorted(paths.keys()):
        if "/devices" in path:
            methods = list(paths[path].keys())
            print(f"  {path} - {methods}")
else:
    print(f"Could not fetch OpenAPI spec: {response.status_code}")
