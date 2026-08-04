"""chat.py 단위 테스트. 실제 OpenAI API를 호출하지 않는다."""

import pytest
from fastapi import HTTPException
from openai import APIConnectionError

from app.chat import ChatRequest, call_chat_completion, chat


class FakeChatResponse:
    def __init__(self, output_text):
        self.output_text = output_text


class FakeResponsesResource:
    def __init__(self, output_text=None, exception=None):
        self._output_text = output_text
        self._exception = exception
        self.calls = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        if self._exception is not None:
            raise self._exception
        return FakeChatResponse(self._output_text)


class FakeOpenAiClient:
    """openai.OpenAI 대신 주입하는 테스트 전용 Fake. responses.create(...)만 흉내낸다."""

    def __init__(self, output_text=None, exception=None):
        self.responses = FakeResponsesResource(output_text, exception)


# 기존 채팅 정상 응답
def test_chat_returns_answer_from_fake_client():
    client = FakeOpenAiClient(output_text="안녕하세요, 무엇을 도와드릴까요?")

    response = call_chat_completion(client, ChatRequest(message="안녕"), "gpt-4o-mini")

    assert response.answer == "안녕하세요, 무엇을 도와드릴까요?"
    assert client.responses.calls[0]["model"] == "gpt-4o-mini"
    assert client.responses.calls[0]["input"] == "안녕"


# OpenAI 예외
def test_chat_raises_bad_gateway_when_openai_call_fails():
    client = FakeOpenAiClient(exception=APIConnectionError(message="connection failed", request=None))

    with pytest.raises(HTTPException) as exc_info:
        call_chat_completion(client, ChatRequest(message="안녕"), "gpt-4o-mini")

    assert exc_info.value.status_code == 502


# API 키 없음 (OpenAI() 생성 시점에 OpenAIError가 나고, chat()이 그대로 502로 변환한다 — 기존 동작 그대로)
def test_chat_raises_bad_gateway_when_api_key_is_missing(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    with pytest.raises(HTTPException) as exc_info:
        chat(ChatRequest(message="안녕"))

    assert exc_info.value.status_code == 502
