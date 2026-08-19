import logging
import threading
from types import SimpleNamespace
from unittest.mock import patch

import httpx
import pytest
from groq import Groq as GroqSdk, RateLimitError

from app.clients.groq_client import Groq
from app.core.ai_timing import (
    get_groq_retry_count,
    log_groq_completion_timing,
    request_id_context,
    reset_groq_retry_tracking,
    start_timer,
    timed_groq_completion,
    was_groq_rate_limited,
)


def test_timed_groq_completion_logs_actual_usage(caplog):
    caplog.set_level(logging.INFO, logger="wallo_ai")
    completion = SimpleNamespace(
        usage=SimpleNamespace(
            prompt_tokens=11,
            completion_tokens=7,
            total_tokens=18,
        ),
        choices=[SimpleNamespace(finish_reason="stop")],
    )
    calls = []

    class Completions:
        def create(self, **kwargs):
            calls.append(kwargs)
            return completion

    client = SimpleNamespace(
        chat=SimpleNamespace(completions=Completions())
    )

    with timed_groq_completion(
        client,
        operation="test.common",
        model="test-model",
    ) as timing:
        result = timing.create(messages=[], max_completion_tokens=123)

    assert result is completion
    assert calls == [{
        "model": "test-model",
        "messages": [],
        "max_completion_tokens": 123,
    }]
    message = next(
        record.getMessage()
        for record in caplog.records
        if "operation=test.common" in record.getMessage()
        and "[AI_TIMING]" in record.getMessage()
    )
    assert "promptTokens=11" in message
    assert "completionTokens=7" in message
    assert "totalTokens=18" in message
    assert "requestedCompletionTokens=123" in message
    assert "success=True" in message


def test_timed_groq_completion_inherits_request_id(caplog):
    caplog.set_level(logging.INFO, logger="wallo_ai")
    completion = SimpleNamespace(
        usage=None,
        choices=[SimpleNamespace(finish_reason="stop")],
    )

    class Completions:
        def create(self, **kwargs):
            return completion

    client = SimpleNamespace(
        chat=SimpleNamespace(completions=Completions())
    )

    with request_id_context("goal-confirm-123"):
        with timed_groq_completion(
            client,
            operation="test.request-id",
            model="test-model",
        ) as timing:
            timing.create(messages=[])

    message = next(
        record.getMessage()
        for record in caplog.records
        if "operation=test.request-id" in record.getMessage()
    )
    assert "requestId=goal-confirm-123" in message


def test_timed_groq_completion_logs_request_shape(caplog):
    caplog.set_level(logging.INFO, logger="wallo_ai")
    completion = SimpleNamespace(
        usage=None,
        choices=[SimpleNamespace(finish_reason="stop")],
    )

    class Completions:
        def create(self, **kwargs):
            return completion

    client = SimpleNamespace(
        chat=SimpleNamespace(completions=Completions())
    )

    with request_id_context("shape-check-123"):
        with timed_groq_completion(
            client,
            operation="test.shape",
            model="test-model",
        ) as timing:
            timing.create(
                messages=[
                    {"role": "system", "content": "system"},
                    {"role": "user", "content": "user"},
                ],
                tools=[{"type": "function"}],
                max_completion_tokens=200,
            )

    message = next(
        record.getMessage()
        for record in caplog.records
        if "operation=test.shape" in record.getMessage()
        and "[AI_REQUEST]" in record.getMessage()
    )
    assert "requestId=shape-check-123" in message
    assert "inFlight=1" in message
    assert "messageCount=2" in message
    assert "messageContentChars=10" in message
    assert "toolCount=1" in message
    assert "requestedCompletionTokens=200" in message


def test_timed_groq_completion_logs_provider_rate_limit_details(caplog):
    caplog.set_level(logging.INFO, logger="wallo_ai")
    response = httpx.Response(
        429,
        headers={
            "retry-after": "30",
            "x-ratelimit-limit-tokens": "8000",
            "x-ratelimit-remaining-tokens": "2040",
            "x-ratelimit-reset-tokens": "30s",
        },
        request=httpx.Request(
            "POST",
            "https://api.groq.com/openai/v1/chat/completions",
        ),
    )
    error = RateLimitError(
        "Rate limit reached",
        response=response,
        body={
            "error": {
                "type": "tokens",
                "code": "rate_limit_exceeded",
                "message": (
                    "Rate limit reached for model in tokens per minute (TPM): "
                    "Limit 8000, Used 5960, Requested 6041. "
                    "Please try again in 30.0075s."
                ),
            }
        },
    )

    class Completions:
        def create(self, **kwargs):
            raise error

    client = SimpleNamespace(
        chat=SimpleNamespace(completions=Completions())
    )

    with pytest.raises(RateLimitError), timed_groq_completion(
        client,
        operation="test.rate-limit",
        model="test-model",
        requested_completion_tokens=1600,
    ) as timing:
        timing.create(messages=[])

    message = next(
        record.getMessage()
        for record in caplog.records
        if "[AI_RATE_LIMIT]" in record.getMessage()
    )
    assert "operation=test.rate-limit" in message
    assert "status=429" in message
    assert "errorCode=rate_limit_exceeded" in message
    assert "retryAfter=30" in message
    assert "providerLimitTokens=8000" in message
    assert "providerUsedTokens=5960" in message
    assert "providerRequestedTokens=6041" in message
    assert "remainingTokens=2040" in message


def test_timed_groq_completion_logs_failure_type_without_raw_error(caplog):
    caplog.set_level(logging.INFO, logger="wallo_ai")
    sensitive_error = ValueError("sensitive user financial content")

    class Completions:
        def create(self, **kwargs):
            raise sensitive_error

    client = SimpleNamespace(
        chat=SimpleNamespace(completions=Completions())
    )

    with pytest.raises(ValueError), timed_groq_completion(
        client,
        operation="test.failure",
        model="test-model",
        requested_completion_tokens=123,
    ) as timing:
        timing.create(messages=[])

    message = next(
        record.getMessage()
        for record in caplog.records
        if "operation=test.failure" in record.getMessage()
        and "[AI_TIMING]" in record.getMessage()
    )
    assert "failureReason=ValueError" in message
    assert "success=False" in message
    assert str(sensitive_error) not in caplog.text


def test_timing_log_contains_retry_and_outcome_metadata(caplog):
    caplog.set_level(logging.INFO, logger="wallo_ai")
    completion = SimpleNamespace(
        usage=SimpleNamespace(
            prompt_tokens=10,
            completion_tokens=20,
            total_tokens=30,
        ),
        choices=[SimpleNamespace(finish_reason="tool_calls")],
    )

    log_groq_completion_timing(
        operation="goal.extract",
        model="test-model",
        started_at=start_timer(),
        completion=completion,
        requested_completion_tokens=900,
        retry_count=1,
        rate_limited=True,
        fallback_used=True,
        fallback_reason="rate_limit",
        success=True,
    )

    message = next(
        record.getMessage()
        for record in caplog.records
        if record.name == "wallo_ai" and "[AI_TIMING]" in record.getMessage()
    )
    assert "retryCount=1" in message
    assert "rateLimited=True" in message
    assert "fallbackUsed=True" in message
    assert "fallbackReason=rate_limit" in message
    assert "success=True" in message


def test_groq_client_tracks_rate_limit_and_internal_retry_count():
    client = Groq.__new__(Groq)
    client._retry_state = threading.local()
    reset_groq_retry_tracking(client)
    response = httpx.Response(
        429,
        request=httpx.Request(
            "POST",
            "https://api.groq.com/openai/v1/chat/completions",
        ),
    )

    assert client._should_retry(response) is True
    assert was_groq_rate_limited(client) is True

    with patch.object(GroqSdk, "_sleep_for_retry") as sleep_mock:
        client._sleep_for_retry(
            retries_taken=0,
            max_retries=1,
            options=SimpleNamespace(url="/chat/completions"),
            response=response,
        )

    assert get_groq_retry_count(client) == 1
    sleep_mock.assert_called_once()
