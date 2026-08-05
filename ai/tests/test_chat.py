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


def test_chat_passes_conversation_history_to_financial_agent():
    client = Mock()
    service = ChatService(client)
    request = ChatRequest(
        message="그중에서 두 번째 방법은?",
        history=[
            {"role": "user", "content": "저축 방법 세 가지를 알려줘"},
            {"role": "assistant", "content": "예산 설정, 자동이체, 소비 점검이 있어요."},
        ],
    )

    with patch("app.chat.service.FinancialAgent.run", return_value="자동이체를 설명할게요.") as run_mock:
        service.chat(request)

    run_mock.assert_called_once_with(
        "그중에서 두 번째 방법은?",
        [
            {"role": "user", "content": "저축 방법 세 가지를 알려줘"},
            {"role": "assistant", "content": "예산 설정, 자동이체, 소비 점검이 있어요."},
        ],
        None,
    )


def test_chat_passes_long_term_summary_to_financial_agent():
    client = Mock()
    service = ChatService(client)
    request = ChatRequest(
        message="목표까지 얼마나 남았어?",
        summary="사용자는 여행 자금 840만 원을 목표로 한다.",
    )

    with patch("app.chat.service.FinancialAgent.run", return_value="목표를 기준으로 계산할게요.") as run_mock:
        service.chat(request)

    run_mock.assert_called_once_with(
        "목표까지 얼마나 남았어?",
        [],
        "사용자는 여행 자금 840만 원을 목표로 한다.",
    )


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
