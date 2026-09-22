#!/usr/bin/env python3
"""Simple test to check if auth endpoints are accessible"""

import requests

BASE_URL = "http://localhost:8000"

print("Testing auth endpoints...")

# Test if auth router is mounted
print("\n1. Testing /v1/auth/register endpoint:")
response = requests.post(f"{BASE_URL}/v1/auth/register", json={
    "username": "test",
    "email": "test@example.com",
    "password": "Test123!"
})
print(f"   Status: {response.status_code}")
print(f"   Response: {response.text[:200]}")

print("\n2. Testing /v1/auth/login endpoint:")
response = requests.post(f"{BASE_URL}/v1/auth/login", json={
    "email": "test@example.com",
    "password": "Test123!",
    "device_fingerprint": "test_device",
    "platform": "android-tv"
})
print(f"   Status: {response.status_code}")
print(f"   Response: {response.text[:200]}")

print("\n3. Testing /v1/auth/me endpoint:")
response = requests.get(f"{BASE_URL}/v1/auth/me")
print(f"   Status: {response.status_code}")
print(f"   Response: {response.text[:200]}")

print("\n4. Testing catalog endpoint (should work):")
response = requests.get(f"{BASE_URL}/v1/channels")
print(f"   Status: {response.status_code}")
print(f"   Response: {response.text[:200]}")

print("\n5. Checking available routes:")
response = requests.get(f"{BASE_URL}/openapi.json")
if response.status_code == 200:
    openapi = response.json()
    paths = openapi.get("paths", {})
    auth_paths = [p for p in paths.keys() if "/auth" in p]
    print(f"   Auth endpoints found: {len(auth_paths)}")
    for path in auth_paths:
        print(f"   - {path}")
else:
    print(f"   Could not fetch OpenAPI spec: {response.status_code}")
