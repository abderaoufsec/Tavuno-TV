"""Tests for retry and circuit breaker resilience layer."""

import unittest
import time
import httpx
from unittest.mock import patch, MagicMock

from app.resilience import with_retry_and_circuit_breaker, CircuitBreaker, _circuit_breakers


class CircuitBreakerTests(unittest.TestCase):
    """Test circuit breaker functionality."""
    
    def setUp(self):
        """Reset shared circuit breakers before each test."""
        _circuit_breakers.clear()
    
    def test_circuit_breaker_opens_after_threshold(self):
        """Test that circuit breaker opens after failure threshold is reached."""
        breaker = CircuitBreaker(failure_threshold=3, cooldown_seconds=1)
        
        # Should allow requests initially
        self.assertTrue(breaker.allow_request())
        
        # Record failures up to threshold
        for _ in range(3):
            breaker.record_failure()
        
        # Circuit should be open now
        self.assertFalse(breaker.allow_request())
        self.assertEqual(breaker.state, "open")
    
    def test_circuit_breaker_half_open_after_cooldown(self):
        """Test that circuit breaker enters half-open state after cooldown."""
        breaker = CircuitBreaker(failure_threshold=2, cooldown_seconds=1)
        
        # Open the circuit
        breaker.record_failure()
        breaker.record_failure()
        self.assertEqual(breaker.state, "open")
        self.assertFalse(breaker.allow_request())
        
        # Wait for cooldown to pass
        time.sleep(1.1)
        
        # Should be in half-open state now
        self.assertTrue(breaker.allow_request())
        self.assertEqual(breaker.state, "half-open")
    
    def test_circuit_breaker_closes_on_success(self):
        """Test that circuit breaker closes on successful call."""
        breaker = CircuitBreaker(failure_threshold=2, cooldown_seconds=1)
        
        # Open the circuit
        breaker.record_failure()
        breaker.record_failure()
        self.assertEqual(breaker.state, "open")
        
        # Record success
        breaker.record_success()
        
        # Circuit should be closed now
        self.assertTrue(breaker.allow_request())
        self.assertEqual(breaker.state, "closed")
        self.assertEqual(breaker.failure_count, 0)


class RetryDecoratorTests(unittest.TestCase):
    """Test retry decorator with circuit breaker."""
    
    def setUp(self):
        """Reset shared circuit breakers before each test."""
        _circuit_breakers.clear()
    
    def test_retry_then_succeed(self):
        """Test that retry logic eventually succeeds after transient failures."""
        call_count = [0]
        
        @with_retry_and_circuit_breaker("TestClient1", max_attempts=3, failure_threshold=5, cooldown_seconds=1)
        def flaky_function():
            call_count[0] += 1
            if call_count[0] < 2:
                raise ConnectionError("Transient error")
            return "success"
        
        result = flaky_function()
        
        # Should have retried and succeeded
        self.assertEqual(result, "success")
        self.assertEqual(call_count[0], 2)
    
    def test_circuit_breaker_opens_on_persistent_failure(self):
        """Test that circuit breaker opens after persistent failures."""
        call_count = [0]
        
        @with_retry_and_circuit_breaker("TestClient2", max_attempts=3, failure_threshold=2, cooldown_seconds=1)
        def failing_function():
            call_count[0] += 1
            raise ConnectionError("Persistent error")
        
        # First batch of attempts (should hit max attempts)
        with self.assertRaises(Exception):
            failing_function()
        
        self.assertEqual(call_count[0], 3)
        
        # Circuit should be open now - verify by checking call count doesn't increase
        with self.assertRaises(Exception):
            failing_function()
        
        # Should not have made additional calls due to open circuit
        self.assertEqual(call_count[0], 3)
    
    def test_no_retry_on_client_error(self):
        """Test that 4xx client errors are not retried."""
        call_count = [0]
        
        class MockResponse:
            def __init__(self, status_code):
                self.status_code = status_code
        
        @with_retry_and_circuit_breaker("TestClient3", max_attempts=3, failure_threshold=5, cooldown_seconds=1)
        def client_error_function():
            call_count[0] += 1
            return MockResponse(404)
        
        result = client_error_function()
        
        # Should return immediately without retry
        self.assertEqual(result.status_code, 404)
        self.assertEqual(call_count[0], 1)
    
    def test_retries_on_http_5xx_status_error(self):
        """A real httpx.HTTPStatusError from a 5xx response should be retried."""
        call_count = [0]
        
        @with_retry_and_circuit_breaker("TestClient4", max_attempts=3, failure_threshold=5, cooldown_seconds=1)
        def flaky_http_call():
            call_count[0] += 1
            if call_count[0] < 2:
                request = httpx.Request("GET", "http://example.test/")
                response = httpx.Response(503, request=request)
                raise httpx.HTTPStatusError("Server error", request=request, response=response)
            return {"ok": True}
        
        result = flaky_http_call()
        
        self.assertEqual(result, {"ok": True})
        self.assertEqual(call_count[0], 2)  # confirms a retry actually happened
    
    def test_does_not_retry_on_http_4xx_status_error(self):
        """A real httpx.HTTPStatusError from a 4xx response should not be retried."""
        call_count = [0]
        
        @with_retry_and_circuit_breaker("TestClient5", max_attempts=3, failure_threshold=5, cooldown_seconds=1)
        def bad_request_call():
            call_count[0] += 1
            request = httpx.Request("GET", "http://example.test/")
            response = httpx.Response(404, request=request)
            raise httpx.HTTPStatusError("Not found", request=request, response=response)
        
        with self.assertRaises(httpx.HTTPStatusError):
            bad_request_call()
        
        self.assertEqual(call_count[0], 1)  # confirms no retry happened


class DispatcharrClientRetryTests(unittest.TestCase):
    """Test retry behavior through actual DispatcharrClient._get_json method."""
    
    def setUp(self):
        """Reset shared circuit breakers before each test."""
        _circuit_breakers.clear()
    
    def test_dispatcharr_client_retries_on_503_then_succeeds(self):
        """Test that DispatcharrClient._get_json retries on 503 and eventually succeeds."""
        from app.dispatcharr_client import DispatcharrClient
        
        call_count = [0]
        
        with patch('httpx.Client') as mock_client_class:
            mock_client = MagicMock()
            
            def mock_get(*args, **kwargs):
                call_count[0] += 1
                if call_count[0] == 1:
                    # First call: 503 error
                    request = httpx.Request("GET", "http://dispatcharr:9191/api/core/version/")
                    response = httpx.Response(503, request=request)
                    raise httpx.HTTPStatusError("Service unavailable", request=request, response=response)
                else:
                    # Second call: success
                    mock_response = MagicMock()
                    mock_response.status_code = 200
                    mock_response.json.return_value = {"version": "0.28.0"}
                    mock_response.raise_for_status.return_value = None
                    return mock_response
            
            mock_client.get.side_effect = mock_get
            mock_client.__enter__ = MagicMock(return_value=mock_client)
            mock_client.__exit__ = MagicMock(return_value=False)
            mock_client_class.return_value = mock_client
            
            client = DispatcharrClient(
                base_url="http://dispatcharr:9191",
                api_key="test-key",
                timeout=20.0
            )
            
            result = client.get_version()
            
            # Should have succeeded after retry
            self.assertEqual(result["version"], "0.28.0")
            self.assertEqual(call_count[0], 2)  # confirms retry happened


if __name__ == "__main__":
    unittest.main()
