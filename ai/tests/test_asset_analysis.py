from unittest.mock import Mock, patch

from app.chat.schemas import ChatRequest
from app.chat.service import ChatService


def test_chat_exposes_selected_asset_analysis_as_structured_response():
    client = Mock()
    asset_analysis = {
        "calculatedMetrics": {
            "totalAssetsKrw": 100_000_000,
            "totalDebtKrw": 10_000_000,
            "netAssetsKrw": 90_000_000,
        },
        "profile": {"assets": {"items": []}},
    }
    financial_agent = Mock()
    financial_agent.run.return_value = "asset analysis answer"
    financial_agent.selected_tool = "analyze_assets"
    financial_agent.selected_tool_result = asset_analysis

    with patch(
        "app.chat.service.FinancialAgent",
        return_value=financial_agent,
    ):
        response = ChatService(client).chat(
            ChatRequest(message="analyze my assets")
        )

    assert response.asset_analysis == asset_analysis
    assert response.consumption_analysis is None
    assert response.model_dump(by_alias=True)["assetAnalysis"] == asset_analysis


def test_chat_does_not_attach_asset_analysis_to_general_answer():
    client = Mock()
    financial_agent = Mock()
    financial_agent.run.return_value = "general answer"
    financial_agent.selected_tool = None
    financial_agent.selected_tool_result = None

    with patch(
        "app.chat.service.FinancialAgent",
        return_value=financial_agent,
    ):
        response = ChatService(client).chat(ChatRequest(message="hello"))

    assert response.asset_analysis is None
