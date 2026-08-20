"""Groq 기반 채팅 서비스 단위 테스트. 실제 외부 API는 호출하지 않는다."""

from types import SimpleNamespace
from unittest.mock import Mock, patch

import pytest
from pydantic import ValidationError

from app.agents.financial.tools.registry import (
    TOOL_SCHEMAS,
    select_route_tool_schemas,
)
from app.chat.schemas import (
    AccountSubtype,
    AssetAnalysisContext,
    ChatRequest,
    GoalFundAvailability,
)
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
        None,
        None,
        None,
        asset_analysis_context=None,
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
        None,
        None,
        None,
        asset_analysis_context=None,
    )


def test_chat_parses_and_passes_financial_context_to_financial_agent():
    client = Mock()
    service = ChatService(client)
    request = ChatRequest.model_validate({
        "message": "여행 목표를 만들고 싶어",
        "financialContext": {
            "hasConnectedAccounts": True,
            "readyAmount": 7_000_000,
            "conditionalAmount": 15_000_000,
            "riskAssetAmount": 14_500_000,
            "excludedAmount": 13_200_000,
            "unknownAmount": 0,
            "debtAmount": 4_800_000,
            "accounts": [
                {
                    "subtype": "CMA",
                    "sourceSubtype": "CMA",
                    "amount": 2_000_000,
                    "availability": "READY",
                }
            ],
        },
    })

    with patch(
        "app.chat.service.FinancialAgent.run",
        return_value="목표를 구체화해 볼게요.",
    ) as run_mock:
        service.chat(request)

    context = request.financial_context
    assert context is not None
    assert context.ready_amount == 7_000_000
    assert context.accounts[0].subtype == AccountSubtype.CMA
    assert context.accounts[0].availability == GoalFundAvailability.READY
    run_mock.assert_called_once_with(
        "여행 목표를 만들고 싶어",
        [],
        None,
        context,
        None,
        None,
        asset_analysis_context=None,
    )


def test_chat_parses_and_passes_asset_analysis_context_to_financial_agent():
    client = Mock()
    service = ChatService(client)
    request = ChatRequest.model_validate({
        "message": "내 자산을 분석해줘",
        "assetAnalysisContext": {
            "totalAssets": 120_000_000,
            "totalDebt": 20_000_000,
            "netAssets": 100_000_000,
            "monthlyIncome": 5_000_000,
            "monthlyExpense": 3_000_000,
            "monthlySaving": 2_000_000,
            "savingRatePercent": 40.0,
            "assetComposition": [
                {
                    "category": "DEPOSIT",
                    "amount": 80_000_000,
                    "sharePercent": 66.7,
                },
            ],
            "asOf": "2026-08-20T12:00:00+09:00",
        },
    })

    with patch(
        "app.chat.service.FinancialAgent.run",
        return_value="자산 분석 결과입니다.",
    ) as run_mock:
        service.chat(request)

    context = request.asset_analysis_context
    assert isinstance(context, AssetAnalysisContext)
    assert context.net_assets == 100_000_000
    run_mock.assert_called_once_with(
        "내 자산을 분석해줘",
        [],
        None,
        None,
        None,
        None,
        asset_analysis_context=context,
    )


@pytest.mark.parametrize(
    "financial_context",
    [
        {
            "hasConnectedAccounts": True,
            "readyAmount": -1,
            "conditionalAmount": 0,
            "riskAssetAmount": 0,
            "excludedAmount": 0,
            "unknownAmount": 0,
            "debtAmount": 0,
            "accounts": [],
        },
        {
            "hasConnectedAccounts": True,
            "readyAmount": 0,
            "conditionalAmount": 0,
            "riskAssetAmount": 0,
            "excludedAmount": 0,
            "unknownAmount": 0,
            "debtAmount": 0,
            "accounts": [
                {
                    "subtype": "ISA",
                    "sourceSubtype": "ISA",
                    "amount": 1_000_000,
                    "availability": "READY",
                }
            ],
        },
    ],
)
def test_chat_rejects_invalid_financial_context(financial_context):
    with pytest.raises(ValidationError):
        ChatRequest.model_validate({
            "message": "목표를 만들고 싶어",
            "financialContext": financial_context,
        })


def test_chat_generates_title_when_requested():
    client = Mock()
    service = ChatService(client)

    with (
        patch("app.chat.service.FinancialAgent.run", return_value="저축 계획을 세워볼게요."),
        patch("app.chat.service.build_conversation_title", return_value="3년 전세자금 계획") as title_mock,
    ):
        response = service.chat(ChatRequest(message="전세자금을 모으고 싶어", generateTitle=True))

    assert response.title == "3년 전세자금 계획"
    title_mock.assert_called_once_with("전세자금을 모으고 싶어")
    assert client.chat.completions.create.call_count == 0


def _tool_names(schemas):
    return {
        schema["function"]["name"]
        for schema in schemas
    }


def test_route_tool_selection_uses_only_product_tool_for_product_request():
    schemas = select_route_tool_schemas("12개월 적금 상품을 추천해줘")

    assert _tool_names(schemas) == {"recommend_financial_products"}


def test_route_tool_selection_groups_asset_tools():
    schemas = select_route_tool_schemas("내 자산과 부채를 분석해줘")

    assert _tool_names(schemas) == {
        "analyze_assets",
        "generate_financial_report",
    }


def test_route_tool_selection_omits_tools_for_simple_greeting():
    assert select_route_tool_schemas("안녕") == []


def test_route_tool_selection_keeps_full_set_for_ambiguous_financial_request():
    schemas = select_route_tool_schemas("월급 관리 방향을 알려줘")

    assert schemas == TOOL_SCHEMAS
