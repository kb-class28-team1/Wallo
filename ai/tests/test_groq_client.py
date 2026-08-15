from unittest.mock import patch

import pytest

from app.clients import groq_client
from app.core.config import (
    DEFAULT_GROQ_MAX_RETRIES,
    DEFAULT_GROQ_TIMEOUT_SECONDS,
    get_groq_max_retries,
    get_groq_timeout_seconds,
)


@pytest.fixture(autouse=True)
def clear_groq_client_cache():
    groq_client._get_cached_groq_client.cache_clear()
    yield
    groq_client._get_cached_groq_client.cache_clear()


def test_groq_client_uses_bounded_retry_and_timeout_settings(monkeypatch):
    monkeypatch.setenv("GROQ_MAX_RETRIES", "1")
    monkeypatch.setenv("GROQ_TIMEOUT_SECONDS", "15")

    with patch("app.clients.groq_client.Groq") as groq_constructor:
        groq_client.create_groq_client("test-key")

    groq_constructor.assert_called_once_with(
        api_key="test-key",
        max_retries=1,
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
    monkeypatch.delenv("GROQ_TIMEOUT_SECONDS", raising=False)

    assert get_groq_max_retries() == DEFAULT_GROQ_MAX_RETRIES == 1
    assert get_groq_timeout_seconds() == DEFAULT_GROQ_TIMEOUT_SECONDS == 30.0


@pytest.mark.parametrize(
    "variable, value",
    [
        ("GROQ_MAX_RETRIES", "2"),
        ("GROQ_MAX_RETRIES", "-1"),
        ("GROQ_TIMEOUT_SECONDS", "0"),
        ("GROQ_TIMEOUT_SECONDS", "61"),
    ],
)
def test_groq_settings_reject_values_outside_safe_bounds(monkeypatch, variable, value):
    monkeypatch.setenv(variable, value)

    getter = (
        get_groq_max_retries
        if variable == "GROQ_MAX_RETRIES"
        else get_groq_timeout_seconds
    )

    with pytest.raises(RuntimeError):
        getter()


def test_create_groq_client_requires_api_key(monkeypatch):
    monkeypatch.delenv("GROQ_API_KEY", raising=False)

    with pytest.raises(RuntimeError, match="GROQ_API_KEY"):
        groq_client.create_groq_client()
