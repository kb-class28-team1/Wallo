import threading
from unittest.mock import patch

import httpx
import pytest

from app.clients import groq_client
from app.clients.groq_client import Groq, RateLimitCircuitBreaker
from app.core.ai_guard import ProviderCircuitOpenError
from app.core.config import (
    DEFAULT_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
    DEFAULT_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS,
    DEFAULT_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS,
    DEFAULT_GROQ_MAX_RETRIES,
    DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS,
    DEFAULT_GROQ_TIMEOUT_SECONDS,
    get_groq_circuit_breaker_failure_threshold,
    get_groq_circuit_breaker_open_seconds,
    get_groq_circuit_breaker_window_seconds,
    get_groq_max_retries,
    get_groq_max_retry_delay_seconds,
    get_groq_timeout_seconds,
)


@pytest.fixture(autouse=True)
def clear_groq_client_cache():
    groq_client._get_cached_groq_client.cache_clear()
    groq_client.reset_rate_limit_circuit_breaker()
    yield
    groq_client._get_cached_groq_client.cache_clear()
    groq_client.reset_rate_limit_circuit_breaker()


def test_groq_client_uses_bounded_retry_and_timeout_settings(monkeypatch):
    monkeypatch.setenv("GROQ_MAX_RETRIES", "1")
    monkeypatch.setenv("GROQ_MAX_RETRY_DELAY_SECONDS", "3")
    monkeypatch.setenv("GROQ_TIMEOUT_SECONDS", "15")

    with patch("app.clients.groq_client.Groq") as groq_constructor:
        groq_client.create_groq_client("test-key")

    groq_constructor.assert_called_once_with(
        api_key="test-key",
        max_retries=1,
        max_retry_delay_seconds=3.0,
        timeout=15.0,
    )


def test_create_groq_client_reuses_client_for_same_configuration():
    with patch("app.clients.groq_client.Groq") as groq_constructor:
        first_client = groq_client.create_groq_client("test-key")
        second_client = groq_client.create_groq_client("test-key")

    assert first_client is second_client
    groq_constructor.assert_called_once()


def test_groq_settings_have_latency_safe_defaults(monkeypatch):
    monkeypatch.delenv("GROQ_MAX_RETRIES", raising=False)
    monkeypatch.delenv("GROQ_MAX_RETRY_DELAY_SECONDS", raising=False)
    monkeypatch.delenv("GROQ_TIMEOUT_SECONDS", raising=False)

    assert get_groq_max_retries() == DEFAULT_GROQ_MAX_RETRIES == 1
    assert (
        get_groq_max_retry_delay_seconds()
        == DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS
        == 5.0
    )
    assert get_groq_timeout_seconds() == DEFAULT_GROQ_TIMEOUT_SECONDS == 30.0
    assert (
        get_groq_circuit_breaker_failure_threshold()
        == DEFAULT_GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD
        == 3
    )
    assert (
        get_groq_circuit_breaker_window_seconds()
        == DEFAULT_GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS
        == 30.0
    )
    assert (
        get_groq_circuit_breaker_open_seconds()
        == DEFAULT_GROQ_CIRCUIT_BREAKER_OPEN_SECONDS
        == 30.0
    )


def test_circuit_breaker_settings_are_configurable(monkeypatch):
    monkeypatch.setenv("GROQ_CIRCUIT_BREAKER_FAILURE_THRESHOLD", "4")
    monkeypatch.setenv("GROQ_CIRCUIT_BREAKER_WINDOW_SECONDS", "45")
    monkeypatch.setenv("GROQ_CIRCUIT_BREAKER_OPEN_SECONDS", "90")

    assert get_groq_circuit_breaker_failure_threshold() == 4
    assert get_groq_circuit_breaker_window_seconds() == 45.0
    assert get_groq_circuit_breaker_open_seconds() == 90.0


@pytest.mark.parametrize(
    "variable, value",
    [
        ("GROQ_MAX_RETRIES", "2"),
        ("GROQ_MAX_RETRIES", "-1"),
        ("GROQ_MAX_RETRY_DELAY_SECONDS", "0"),
        ("GROQ_MAX_RETRY_DELAY_SECONDS", "11"),
        ("GROQ_TIMEOUT_SECONDS", "0"),
        ("GROQ_TIMEOUT_SECONDS", "61"),
    ],
)
def test_groq_settings_reject_values_outside_safe_bounds(monkeypatch, variable, value):
    monkeypatch.setenv(variable, value)

    getter = (
        get_groq_max_retries
        if variable == "GROQ_MAX_RETRIES"
        else get_groq_max_retry_delay_seconds
        if variable == "GROQ_MAX_RETRY_DELAY_SECONDS"
        else get_groq_timeout_seconds
    )

    with pytest.raises(RuntimeError):
        getter()


def test_create_groq_client_requires_api_key(monkeypatch):
    monkeypatch.delenv("GROQ_API_KEY", raising=False)

    with pytest.raises(RuntimeError, match="GROQ_API_KEY"):
        groq_client.create_groq_client()


def _rate_limit_response(retry_after: str) -> httpx.Response:
    return httpx.Response(
        429,
        headers={"retry-after": retry_after},
        request=httpx.Request(
            "POST",
            "https://api.groq.com/openai/v1/chat/completions",
        ),
    )


def _bare_client(max_retry_delay_seconds: float = 5.0) -> Groq:
    client = Groq.__new__(Groq)
    client._max_retry_delay_seconds = max_retry_delay_seconds
    client._retry_state = threading.local()
    client.reset_retry_tracking()
    return client


def test_rate_limit_circuit_breaker_opens_after_repeated_failures_and_recovers():
    now = [0.0]
    breaker = RateLimitCircuitBreaker(
        failure_threshold=2,
        failure_window_seconds=10.0,
        open_seconds=20.0,
        clock=lambda: now[0],
    )

    assert breaker.record_rate_limit(8.0) is False
    now[0] = 1.0
    assert breaker.record_rate_limit(8.0) is True

    with pytest.raises(ProviderCircuitOpenError) as error_info:
        breaker.before_request()

    assert error_info.value.retry_after_seconds == pytest.approx(20.0)
    now[0] = 21.0
    breaker.before_request()


def test_groq_request_is_blocked_when_rate_limit_circuit_is_open(monkeypatch):
    now = [0.0]
    breaker = RateLimitCircuitBreaker(
        failure_threshold=2,
        failure_window_seconds=10.0,
        open_seconds=20.0,
        clock=lambda: now[0],
    )
    breaker.record_rate_limit()
    breaker.record_rate_limit()
    monkeypatch.setattr(groq_client, "_RATE_LIMIT_CIRCUIT_BREAKER", breaker)
    client = _bare_client()

    with patch.object(groq_client.GroqSdk, "request") as sdk_request:
        with pytest.raises(ProviderCircuitOpenError):
            client.request(object(), object())

    sdk_request.assert_not_called()


def test_repeated_429_opens_circuit_before_another_sdk_retry(monkeypatch):
    now = [0.0]
    breaker = RateLimitCircuitBreaker(
        failure_threshold=2,
        failure_window_seconds=10.0,
        open_seconds=20.0,
        clock=lambda: now[0],
    )
    breaker.record_rate_limit()
    monkeypatch.setattr(groq_client, "_RATE_LIMIT_CIRCUIT_BREAKER", breaker)
    client = _bare_client()

    assert client._should_retry(_rate_limit_response("1")) is False


def test_provider_retry_after_above_fallback_cap_allows_dynamic_retry():
    client = _bare_client()

    response = _rate_limit_response("8")

    assert client._should_retry(response) is True
    assert client.was_rate_limited() is True
    assert client.get_retry_count() == 0

    timeout = client._calculate_retry_timeout(
        remaining_retries=1,
        options=object(),
        response_headers=response.headers,
    )

    assert timeout == 8.0


def test_retry_after_over_safe_provider_limit_skips_automatic_retry():
    client = _bare_client()

    assert client._should_retry(_rate_limit_response("61")) is False


def test_rate_limit_is_retried_at_most_once():
    client = _bare_client()
    client._retry_state.retry_count = 1

    assert client._should_retry(_rate_limit_response("1")) is False


def test_short_retry_after_allows_one_bounded_retry():
    client = _bare_client()

    assert client._should_retry(_rate_limit_response("1.5")) is True


def test_sdk_backoff_is_capped_by_client_retry_delay():
    client = _bare_client(3.0)

    with patch.object(groq_client.GroqSdk, "_calculate_retry_timeout", return_value=60.0):
        timeout = client._calculate_retry_timeout(
            remaining_retries=1,
            options=object(),
            response_headers=None,
        )

    assert timeout == 3.0
