import httpx
import pytest
from fastapi import HTTPException
from groq import RateLimitError
from unittest.mock import patch

from app.chat.router import RATE_LIMIT_MESSAGE, chat
from app.chat.schemas import ChatRequest


def test_chat_returns_429_when_groq_usage_limit_is_reached():
    response = httpx.Response(
        429,
        headers={"retry-after": "181"},
        request=httpx.Request(
            "POST",
            "https://api.groq.com/openai/v1/chat/completions",
        ),
    )
    error = RateLimitError(
        "Rate limit reached",
        response=response,
        body={"error": {"code": "rate_limit_exceeded"}},
    )

    with (
        patch("app.chat.router.create_groq_client"),
        patch("app.chat.router.ChatService.chat", side_effect=error),
        pytest.raises(HTTPException) as exc_info,
    ):
        chat(ChatRequest(message="비상금 목표를 만들고 싶어"))

    assert exc_info.value.status_code == 429
    assert exc_info.value.detail == RATE_LIMIT_MESSAGE
    assert exc_info.value.headers == {"Retry-After": "181"}
