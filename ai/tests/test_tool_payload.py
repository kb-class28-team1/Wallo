import json
from types import SimpleNamespace
from unittest.mock import Mock, patch

from app.financial_assistant.tool_result import ToolResult
from app.financial_assistant.agent import FinancialAgent, PRODUCT_RECOMMENDATION_TOOL
from app.financial_assistant.tool_payload import compact_tool_result_for_prompt


def test_asset_payload_keeps_database_metrics_and_profile_shape():
    result = ToolResult(
        status="success",
        tool="analyze_assets",
        data={
            "dataMode": "database",
            "request": "analyze assets",
            "calculatedMetrics": {"netAssetsKrw": 27_000_000},
            "profile": {
                "profile_id": 7,
                "nickname": "sample",
                "selection_reason": "x" * 1000,
                "income": {"current_monthly_net_income_krw": 5_000_000},
                "cashflow": {"monthly_saving_krw": 1_000_000},
                "assets": {"total_assets_krw": 30_000_000, "items": []},
                "total_debt_krw": 3_000_000,
            },
            "analysisInstructions": ["drop this"],
        },
    )

    compact = compact_tool_result_for_prompt(result)
    compact_data = compact["data"]

    assert compact_data["calculatedMetrics"] == {"netAssetsKrw": 27_000_000}
    assert compact_data["profile"]["nickname"] == "sample"
    assert "selection_reason" not in compact_data["profile"]
    assert "analysisInstructions" not in compact_data
    assert "analysisInstructions" in result.data


def test_product_payload_limits_candidates_and_removes_codes_and_instructions():
    products = [
        {
            "productName": f"product-{index}",
            "productCode": f"secret-{index}",
            "companyName": "Wallo Bank",
            "baseRatePercent": 3.0,
            "preferentialConditions": "condition " * 200,
        }
        for index in range(7)
    ]
    result = ToolResult(
        status="success",
        tool="recommend_financial_products",
        data={
            "productType": "deposit",
            "termMonths": 12,
            "products": products,
            "instructions": ["drop this"],
        },
    )

    compact = compact_tool_result_for_prompt(result)
    compact_products = compact["data"]["products"]

    assert len(compact_products) == 3
    assert compact_products[0]["productName"] == "product-0"
    assert "productCode" not in compact_products[0]
    assert len(compact_products[0]["preferentialConditions"]) <= 403
    assert "instructions" not in compact["data"]


def test_spending_payload_keeps_summary_signals_and_caps_repeated_lists():
    result = ToolResult(
        status="success",
        tool="coach_spending",
        data={
            "totalChange": {"changeAmount": 100_000},
            "categoryOverview": [{"category": str(index), "amount": index} for index in range(10)],
            "criteriaEvaluations": [{"code": str(index)} for index in range(10)],
            "patterns": {
                "weekdayHabits": [{"weekday": index} for index in range(10)],
                "weekendConcentration": {"detected": True},
                "timeSlotHabits": [{"timeSlot": str(index)} for index in range(10)],
            },
            "analysisInstructions": ["drop this"],
        },
    )

    compact = compact_tool_result_for_prompt(result)
    compact_data = compact["data"]

    assert compact_data["totalChange"] == {"changeAmount": 100_000}
    assert len(compact_data["categoryOverview"]) == 5
    assert "criteriaEvaluations" not in compact_data
    assert len(compact_data["patterns"]["weekdayHabits"]) == 5
    assert len(compact_data["patterns"]["timeSlotHabits"]) == 5
    assert "analysisInstructions" not in compact_data


def test_error_payload_preserves_message_and_data():
    result = ToolResult(
        status="needs_input",
        tool="recommend_financial_products",
        data={"missingFields": ["termMonths"]},
        message="Please provide the term.",
    )

    assert compact_tool_result_for_prompt(result) == {
        "status": "needs_input",
        "tool": "recommend_financial_products",
        "message": "Please provide the term.",
        "data": {"missingFields": ["termMonths"]},
    }


def test_financial_agent_uses_compact_payload_but_keeps_full_selected_result():
    tool_call = SimpleNamespace(
        id="call-product",
        function=SimpleNamespace(
            name=PRODUCT_RECOMMENDATION_TOOL,
            arguments=json.dumps({"request": "recommend a product"}),
        ),
        model_dump=Mock(return_value={
            "id": "call-product",
            "type": "function",
            "function": {"name": PRODUCT_RECOMMENDATION_TOOL, "arguments": "{}"},
        }),
    )
    full_data = {
        "products": [{
            "productName": "Safe Product",
            "productCode": "internal-code",
            "preferentialConditions": "condition " * 200,
        }],
        "instructions": ["do not send"],
    }
    client = Mock()
    client.chat.completions.create.side_effect = [
        SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
            content=None,
            tool_calls=[tool_call],
        ))]),
        SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
            content="final answer",
        ))]),
    ]

    with patch(
        "app.financial_assistant.agent.execute_tool",
        return_value=ToolResult(
            status="success",
            tool=PRODUCT_RECOMMENDATION_TOOL,
            data=full_data,
        ),
    ):
        agent = FinancialAgent(client, model="test-model")
        assert agent.run("recommend a product") == "final answer"

    tool_message = client.chat.completions.create.call_args_list[1].kwargs["messages"][-1]
    prompt_payload = json.loads(tool_message["content"])
    assert "productCode" not in prompt_payload["data"]["products"][0]
    assert "instructions" not in prompt_payload["data"]
    assert agent.selected_tool_result == full_data
