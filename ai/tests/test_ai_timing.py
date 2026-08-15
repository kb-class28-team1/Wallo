import logging
import threading
from types import SimpleNamespace
from unittest.mock import patch

import httpx
from groq import Groq as GroqSdk

from app.clients.groq_client import Groq
from app.core.ai_timing import (
    get_groq_retry_count,
    log_groq_completion_timing,
    reset_groq_retry_tracking,
    start_timer,
    was_groq_rate_limited,
)


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
