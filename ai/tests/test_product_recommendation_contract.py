from unittest.mock import Mock, patch

from app.agents.financial.agent import PRODUCT_RECOMMENDATION_TOOL
from app.chat.schemas import ChatRequest
from app.chat.service import ChatService


def test_chat_exposes_successful_product_recommendation_with_alias():
    recommendation = {
        "dataMode": "finlife_csv",
        "productType": "deposit",
        "termMonths": 12,
        "amountKrw": 1_000_000,
        "products": [
            {
                "ranking": 1,
                "companyName": "Wallo Bank",
                "productName": "Safe Deposit",
                "baseRatePercent": 2.5,
                "preferentialRatePercent": 3.1,
            }
        ],
    }
    financial_agent = Mock()
    financial_agent.run.return_value = "I recommend this product because it matches your conditions."
    financial_agent.selected_tool = PRODUCT_RECOMMENDATION_TOOL
    financial_agent.selected_tool_result = recommendation

    with patch("app.chat.service.FinancialAgent", return_value=financial_agent):
        response = ChatService(Mock()).chat(
            ChatRequest(message="Recommend a 12-month deposit")
        )

    assert response.product_recommendation == recommendation
    assert response.model_dump(by_alias=True)["productRecommendation"] == recommendation
    assert response.consumption_analysis is None
    assert response.asset_analysis is None


def test_chat_keeps_text_answer_when_product_recommendation_needs_more_input():
    financial_agent = Mock()
    financial_agent.run.return_value = "Please tell me the term and amount."
    financial_agent.selected_tool = PRODUCT_RECOMMENDATION_TOOL
    financial_agent.selected_tool_result = None

    with patch("app.chat.service.FinancialAgent", return_value=financial_agent):
        response = ChatService(Mock()).chat(
            ChatRequest(message="Recommend a financial product")
        )

    assert response.product_recommendation is None
    assert response.answer == "Please tell me the term and amount."
    assert response.model_dump(by_alias=True)["productRecommendation"] is None


def test_chat_keeps_text_answer_when_no_product_matches():
    financial_agent = Mock()
    financial_agent.run.return_value = "No product matches those conditions."
    financial_agent.selected_tool = PRODUCT_RECOMMENDATION_TOOL
    financial_agent.selected_tool_result = None

    with patch("app.chat.service.FinancialAgent", return_value=financial_agent):
        response = ChatService(Mock()).chat(
            ChatRequest(message="Recommend a product that does not exist")
        )

    assert response.product_recommendation is None
    assert response.answer == "No product matches those conditions."
    assert response.model_dump(by_alias=True)["productRecommendation"] is None
