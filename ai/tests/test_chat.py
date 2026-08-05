"""Groq 기반 채팅 서비스 단위 테스트. 실제 외부 API는 호출하지 않는다."""

from types import SimpleNamespace
from unittest.mock import Mock, patch

from app.chat.schemas import ChatRequest
from app.chat.service import ChatService


def test_chat_returns_financial_agent_answer_without_title():
    client = Mock()
    service = ChatService(client)

    with patch("app.chat.service.FinancialAgent.run", return_value="안녕하세요!"):
        response = service.chat(ChatRequest(message="안녕"))

    assert response.answer == "안녕하세요!"
    assert response.title is None


def test_chat_generates_title_when_requested():
    client = Mock()
    service = ChatService(client)

    with (
        patch("app.chat.service.FinancialAgent.run", return_value="저축 계획을 세워볼게요."),
        patch("app.chat.service.generate_conversation_title", return_value="3년 전세자금 계획") as title_mock,
    ):
        response = service.chat(ChatRequest(message="전세자금을 모으고 싶어", generateTitle=True))

    assert response.title == "3년 전세자금 계획"
    title_mock.assert_called_once_with(client, "전세자금을 모으고 싶어", "저축 계획을 세워볼게요.")
