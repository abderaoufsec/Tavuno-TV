#!/usr/bin/env python3
"""
Runtime API Testing Script for Tavuno Authentication
Tests Priority 1: Runtime API testing with actual backend
"""

import requests
import json
import time

BASE_URL = "http://localhost:8000"

def print_section(title):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")

def test_registration():
    print_section("TEST 1: Registration")
    
    # Test 1a: Valid registration
    print("\n1a. Valid registration:")
    test_email = f"testuser_{int(time.time())}@example.com"
    payload = {
        "username": "testuser",
        "email": test_email,
        "password": "TestPassword123!"
    }
    
    response = requests.post(f"{BASE_URL}/v1/auth/register", json=payload)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    
    return test_email

def test_login(email):
    print_section("TEST 2: Login")
    
    # Test 2a: Valid login
    print("\n2a. Valid login:")
    device_fingerprint = f"test_device_{int(time.time())}"
    payload = {
        "email": email,
        "password": "TestPassword123!",
        "device_fingerprint": device_fingerprint,
        "platform": "android-tv"
    }
    
    response = requests.post(f"{BASE_URL}/v1/auth/login", json=payload)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    
    if response.status_code == 200:
        data = response.json()
        access_token = data.get("access_token")
        refresh_token = data.get("refresh_token")
        return access_token, refresh_token
    else:
        return None, None

def test_subscription(access_token):
    print_section("TEST 3: Subscription Status")
    
    print("\n3a. Get subscription with valid token:")
    headers = {"Authorization": f"Bearer {access_token}"}
    response = requests.get(f"{BASE_URL}/v1/auth/subscription", headers=headers)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")

def test_refresh_token(refresh_token):
    print_section("TEST 4: Token Refresh")
    
    print("\n4a. Refresh with valid refresh token:")
    payload = {"refresh_token": refresh_token}
    response = requests.post(f"{BASE_URL}/v1/auth/refresh", json=payload)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    
    if response.status_code == 200:
        data = response.json()
        return data.get("access_token")
    return None

def test_catalog_api(access_token):
    print_section("TEST 5: Catalog API")
    
    headers = {"Authorization": f"Bearer {access_token}"}
    
    print("\n5a. Get channels:")
    response = requests.get(f"{BASE_URL}/v1/channels", headers=headers)
    print(f"   Status: {response.status_code}")
    data = response.json() if response.text else []
    print(f"   Channels count: {len(data) if isinstance(data, list) else 'N/A'}")
    if isinstance(data, list) and len(data) > 0:
        print(f"   First channel: {json.dumps(data[0], indent=4)}")
    
    print("\n5b. Get categories:")
    response = requests.get(f"{BASE_URL}/v1/categories", headers=headers)
    print(f"   Status: {response.status_code}")
    data = response.json() if response.text else []
    print(f"   Categories count: {len(data) if isinstance(data, list) else 'N/A'}")
    
    print("\n5c. Get EPG:")
    response = requests.get(f"{BASE_URL}/v1/epg", headers=headers)
    print(f"   Status: {response.status_code}")
    data = response.json() if response.text else []
    print(f"   EPG entries count: {len(data) if isinstance(data, list) else 'N/A'}")

def test_sports_api(access_token):
    print_section("TEST 6: Sports API")
    
    headers = {"Authorization": f"Bearer {access_token}"}
    
    print("\n6a. Get competitions:")
    response = requests.get(f"{BASE_URL}/v1/sports/competitions", headers=headers)
    print(f"   Status: {response.status_code}")
    data = response.json() if response.text else []
    print(f"   Competitions count: {len(data) if isinstance(data, list) else 'N/A'}")
    if isinstance(data, list) and len(data) > 0:
        print(f"   First competition: {json.dumps(data[0], indent=4)}")
    
    print("\n6b. Get matches:")
    response = requests.get(f"{BASE_URL}/v1/sports/matches", headers=headers)
    print(f"   Status: {response.status_code}")
    data = response.json() if response.text else []
    print(f"   Matches count: {len(data) if isinstance(data, list) else 'N/A'}")

def test_vod_api(access_token):
    print_section("TEST 7: VOD API")
    
    headers = {"Authorization": f"Bearer {access_token}"}
    
    print("\n7a. Get movies:")
    response = requests.get(f"{BASE_URL}/v1/movies", headers=headers)
    print(f"   Status: {response.status_code}")
    data = response.json() if response.text else []
    print(f"   Movies count: {len(data) if isinstance(data, list) else 'N/A'}")
    if isinstance(data, list) and len(data) > 0:
        print(f"   First movie: {json.dumps(data[0], indent=4)}")
    
    print("\n7b. Get series:")
    response = requests.get(f"{BASE_URL}/v1/series", headers=headers)
    print(f"   Status: {response.status_code}")
    data = response.json() if response.text else []
    print(f"   Series count: {len(data) if isinstance(data, list) else 'N/A'}")

def test_playback_authorization(access_token):
    print_section("TEST 8: Playback Authorization")
    
    headers = {"Authorization": f"Bearer {access_token}"}
    
    print("\n8a. Live playback authorization (channel 1):")
    response = requests.post(f"{BASE_URL}/v1/playback/live/1", headers=headers, json={})
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    
    print("\n8b. Movie playback authorization (movie 1):")
    response = requests.post(f"{BASE_URL}/v1/playback/movie/1", headers=headers, json={})
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    print(f"   Note: Movie may not exist in database (404 expected if not found)")
    
    print("\n8c. Episode playback authorization (episode 1):")
    response = requests.post(f"{BASE_URL}/v1/playback/episode/1", headers=headers, json={})
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    print(f"   Note: Episode may not exist in database (404 expected if not found)")

def test_error_scenarios():
    print_section("TEST 9: Error Scenarios")
    
    print("\n9a. Login with wrong password:")
    device_fingerprint = f"test_device_error_{int(time.time())}"
    payload = {
        "email": "test@example.com",
        "password": "WrongPassword123!",
        "device_fingerprint": device_fingerprint,
        "platform": "android-tv"
    }
    response = requests.post(f"{BASE_URL}/v1/auth/login", json=payload)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    
    print("\n9b. Login with unknown email:")
    device_fingerprint = f"test_device_error2_{int(time.time())}"
    payload = {
        "email": "unknown@example.com",
        "password": "TestPassword123!",
        "device_fingerprint": device_fingerprint,
        "platform": "android-tv"
    }
    response = requests.post(f"{BASE_URL}/v1/auth/login", json=payload)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    
    print("\n9c. Access API without token:")
    response = requests.get(f"{BASE_URL}/v1/channels")
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")
    
    print("\n9d. Access API with invalid token:")
    headers = {"Authorization": "Bearer invalid_token"}
    response = requests.get(f"{BASE_URL}/v1/channels", headers=headers)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")

def test_logout(refresh_token):
    print_section("TEST 10: Logout")
    
    print("\n10a. Logout with valid refresh token:")
    payload = {"refresh_token": refresh_token}
    response = requests.post(f"{BASE_URL}/v1/auth/logout", json=payload)
    print(f"   Status: {response.status_code}")
    print(f"   Response: {json.dumps(response.json(), indent=2) if response.text else 'No content'}")

def main():
    print_section("TAVUNO AUTHENTICATION API RUNTIME TESTS")
    print(f"Backend URL: {BASE_URL}")
    print(f"Test started at: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    
    try:
        # Test registration
        test_email = test_registration()
        
        # Test login
        access_token, refresh_token = test_login(test_email)
        
        if access_token:
            # Test subscription
            test_subscription(access_token)
            
            # Test catalog API
            test_catalog_api(access_token)
            
            # Test sports API
            test_sports_api(access_token)
            
            # Test VOD API
            test_vod_api(access_token)
            
            # Test playback authorization
            test_playback_authorization(access_token)
            
            # Test token refresh
            new_access_token = test_refresh_token(refresh_token)
            
            # Test logout
            test_logout(refresh_token)
        
        # Test error scenarios
        test_error_scenarios()
        
        print_section("TEST SUMMARY")
        print("[SUCCESS] All runtime API tests completed")
        print(f"Test completed at: {time.strftime('%Y-%m-%d %H:%M:%S')}")
        
    except requests.exceptions.ConnectionError:
        print("\n[ERROR] Cannot connect to backend")
        print(f"   Make sure backend is running at {BASE_URL}")
    except Exception as e:
        print(f"\n[ERROR] {str(e)}")

if __name__ == "__main__":
    main()
