"""Retry and circuit breaker decorator for external service calls."""

import time
import logging
from functools import wraps
from typing import Callable, TypeVar
import httpx

logger = logging.getLogger("tavuno-control.resilience")

T = TypeVar("T")


class CircuitBreaker:
    """Simple in-memory circuit breaker for external service calls."""
    
    def __init__(self, failure_threshold: int = 5, cooldown_seconds: int = 30):
        self.failure_threshold = failure_threshold
        self.cooldown_seconds = cooldown_seconds
        self.failure_count = 0
        self.last_failure_time = None
        self.state = "closed"  # closed, open, half-open
    
    def record_success(self):
        """Record a successful call and reset failure count."""
        self.failure_count = 0
        self.state = "closed"
    
    def record_failure(self):
        """Record a failed call and potentially open the circuit."""
        self.failure_count += 1
        self.last_failure_time = time.time()
        
        if self.failure_count >= self.failure_threshold:
            self.state = "open"
            logger.warning(
                "Circuit breaker opened after %d consecutive failures",
                self.failure_count
            )
    
    def allow_request(self) -> bool:
        """Check if a request should be allowed based on circuit state."""
        if self.state == "closed":
            return True
        
        if self.state == "open":
            # Check if cooldown period has passed
            if time.time() - self.last_failure_time >= self.cooldown_seconds:
                self.state = "half-open"
                logger.info("Circuit breaker entering half-open state")
                return True
            return False
        
        if self.state == "half-open":
            # Allow one probe request
            return True
        
        return False


# Shared circuit breakers per client name
_circuit_breakers: dict[str, CircuitBreaker] = {}


def get_or_create_circuit_breaker(
    client_name: str,
    failure_threshold: int = 5,
    cooldown_seconds: int = 30,
) -> CircuitBreaker:
    """Get or create a shared circuit breaker for a client."""
    if client_name not in _circuit_breakers:
        _circuit_breakers[client_name] = CircuitBreaker(failure_threshold, cooldown_seconds)
    return _circuit_breakers[client_name]


def with_retry_and_circuit_breaker(
    client_name: str,
    max_attempts: int = 3,
    failure_threshold: int = 5,
    cooldown_seconds: int = 30,
):
    """
    Decorator that adds retry logic with exponential backoff and circuit breaker protection.
    
    Retries on transient failures (connection errors, timeouts, 5xx server errors).
    Does not retry on 4xx client errors (won't succeed on retry).
    """
    circuit_breaker = get_or_create_circuit_breaker(client_name, failure_threshold, cooldown_seconds)
    
    def decorator(func: Callable[..., T]) -> Callable[..., T]:
        @wraps(func)
        def wrapper(*args, **kwargs) -> T:
            # Check circuit breaker first
            if not circuit_breaker.allow_request():
                raise Exception(f"Circuit breaker open for {client_name} - too many consecutive failures")
            
            last_exception = None
            for attempt in range(max_attempts):
                try:
                    result = func(*args, **kwargs)
                    
                    # Check for HTTP status codes
                    if hasattr(result, 'status_code'):
                        if 500 <= result.status_code < 600:
                            # Server error - retry
                            circuit_breaker.record_failure()
                            if attempt < max_attempts - 1:
                                backoff = min(2 ** attempt, 10) + (time.time() % 1)  # exponential with jitter
                                logger.warning(
                                    f"{client_name}: Server error {result.status_code}, retrying in {backoff:.1f}s (attempt {attempt + 1}/{max_attempts})"
                                )
                                time.sleep(backoff)
                                continue
                        elif 400 <= result.status_code < 500:
                            # Client error - don't retry
                            circuit_breaker.record_success()
                            return result
                    
                    # Success - record success and return
                    circuit_breaker.record_success()
                    return result
                    
                except (ConnectionError, TimeoutError, httpx.ConnectError, httpx.TimeoutException) as exc:
                    last_exception = exc
                    circuit_breaker.record_failure()
                    if attempt < max_attempts - 1:
                        backoff = min(2 ** attempt, 10) + (time.time() % 1)  # exponential with jitter
                        logger.warning(
                            f"{client_name}: Transient error {type(exc).__name__}, retrying in {backoff:.1f}s (attempt {attempt + 1}/{max_attempts})"
                        )
                        time.sleep(backoff)
                        continue
                    else:
                        break
                except httpx.HTTPStatusError as exc:
                    status_code = exc.response.status_code
                    if 500 <= status_code < 600:
                        # Server error - transient, retry
                        last_exception = exc
                        circuit_breaker.record_failure()
                        if attempt < max_attempts - 1:
                            backoff = min(2 ** attempt, 10) + (time.time() % 1)  # exponential with jitter
                            logger.warning(
                                f"{client_name}: Server error {status_code}, retrying in {backoff:.1f}s (attempt {attempt + 1}/{max_attempts})"
                            )
                            time.sleep(backoff)
                            continue
                        else:
                            break
                    else:
                        # 4xx client error - won't succeed on retry, don't affect circuit breaker state
                        raise
                except Exception as exc:
                    # Other exceptions - don't retry
                    circuit_breaker.record_failure()
                    raise
            
            # All attempts failed
            if last_exception:
                raise last_exception
            raise Exception(f"{client_name}: Max retries ({max_attempts}) exceeded")
        
        return wrapper
    return decorator


__all__ = ["with_retry_and_circuit_breaker", "CircuitBreaker", "get_or_create_circuit_breaker", "_circuit_breakers"]
