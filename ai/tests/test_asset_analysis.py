from unittest.mock import Mock, patch

from app.agents.financial.tools.asset_analysis import execute as execute_asset_analysis
from app.chat.schemas import AssetAnalysisContext, ChatRequest
from app.chat.service import ChatService


def _asset_analysis_context() -> AssetAnalysisContext:
    return AssetAnalysisContext.model_validate({
        "totalAssets": 182_600_000,
        "totalDebt": 12_600_000,
        "netAssets": 170_000_000,
        "monthlyIncome": 5_500_000,
        "monthlyExpense": 2_000_000,
        "monthlySaving": 3_500_000,
        "savingRatePercent": 63.6,
        "assetComposition": [
            {
                "category": "DEPOSIT",
                "amount": 120_000_000,
                "sharePercent": 65.7,
            },
        ],
        "asOf": "2026-08-20T12:00:00+09:00",
    })


def test_asset_analysis_returns_database_values_from_backend_context():
    result = execute_asset_analysis(
        "analyze_assets",
        {"request": "analyze my assets"},
        _asset_analysis_context(),
    )

    assert result.status == "success"
    assert result.data["dataMode"] == "database"
    assert result.data["calculatedMetrics"] == {
        "monthlyNetIncomeKrw": 5_500_000,
        "monthlySavingKrw": 3_500_000,
        "monthlyExpenseKrw": 2_000_000,
        "monthlySurplusKrw": 3_500_000,
        "annualSavingKrw": 42_000_000,
        "savingRatePercent": 63.6,
        "totalAssetsKrw": 182_600_000,
        "totalDebtKrw": 12_600_000,
        "netAssetsKrw": 170_000_000,
    }
    assert result.data["profile"]["assets"]["total_assets_krw"] == 182_600_000
    assert result.data["profile"]["total_debt_krw"] == 12_600_000
    assert result.data["profile"]["as_of"] == "2026-08-20T12:00:00+09:00"


def test_asset_analysis_requires_backend_context_instead_of_demo_data():
    result = execute_asset_analysis(
        "analyze_assets",
        {"request": "analyze my assets"},
    )

    assert result.status == "needs_input"
    assert result.data == {
        "dataMode": "database",
        "request": "analyze my assets",
        "calculatedMetrics": {},
        "profile": {},
    }


def test_chat_exposes_selected_asset_analysis_as_structured_response():
    client = Mock()
    asset_analysis = {
        "dataMode": "database",
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
            ChatRequest(
                message="analyze my assets",
                asset_analysis_context=_asset_analysis_context(),
            )
        )

    assert response.asset_analysis == asset_analysis
    assert response.consumption_analysis is None
    assert response.model_dump(by_alias=True)["assetAnalysis"] == asset_analysis
    financial_agent.run.assert_called_once()
    assert financial_agent.run.call_args.kwargs["asset_analysis_context"] == (
        _asset_analysis_context()
    )


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
