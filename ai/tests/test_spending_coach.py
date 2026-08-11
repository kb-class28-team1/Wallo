from datetime import date, time

from app.agents.financial.tools.spending_coach import analyze
from app.agents.financial.agent import FinancialAgent
from app.agents.financial.consumption_models import ConsumptionContext, ConsumptionTransaction
from types import SimpleNamespace
from unittest.mock import Mock
import json


def tx(day: str, amount: int, category: str = "CAFE") -> ConsumptionTransaction:
    return ConsumptionTransaction(date=date.fromisoformat(day), time=time(9), category=category, amount=amount)


def test_weekly_current_period_compares_same_elapsed_days():
    context = ConsumptionContext(transactions=[
        tx("2026-08-03", 30_000), tx("2026-08-04", 30_000), tx("2026-08-05", 30_000),
        tx("2026-08-10", 50_000), tx("2026-08-11", 50_000), tx("2026-08-12", 50_000),
    ])
    result = analyze(context, {"periodType": "WEEKLY", "referenceDate": "2026-08-12"}, date(2026, 8, 12))
    assert result["analysisPeriod"] == {"start": "2026-08-10", "end": "2026-08-12"}
    assert result["comparisonPeriod"] == {"start": "2026-08-03", "end": "2026-08-05"}
    assert result["totalChange"]["changeAmount"] == 60_000
    assert result["totalChange"]["warning"] is True


def test_insufficient_data_does_not_emit_patterns():
    context = ConsumptionContext(transactions=[tx("2026-08-10", 10_000), tx("2026-08-11", 10_000)])
    result = analyze(context, {"periodType": "WEEKLY"}, date(2026, 8, 12))
    assert result["dataSufficiency"]["sufficient"] is False
    assert "patterns" not in result


def test_category_surges_are_limited_and_sorted():
    transactions = []
    for category, previous, current in [("FOOD", 50_000, 200_000), ("CAFE", 40_000, 100_000),
                                         ("SHOPPING", 50_000, 160_000), ("CULTURE", 30_000, 80_000)]:
        transactions.extend([tx("2026-07-01", previous // 3, category), tx("2026-07-02", previous // 3, category),
                             tx("2026-07-03", previous - 2 * (previous // 3), category),
                             tx("2026-08-01", current // 3, category), tx("2026-08-02", current // 3, category),
                             tx("2026-08-03", current - 2 * (current // 3), category)])
    result = analyze(ConsumptionContext(transactions=transactions),
                     {"periodType": "MONTHLY", "referenceDate": "2026-08-01"}, date(2026, 8, 31))
    assert [row["category"] for row in result["categorySurges"]] == ["FOOD", "SHOPPING", "CAFE"]


def test_spending_analysis_uses_expanded_final_output_tokens():
    client = Mock()
    tool_call = SimpleNamespace(
        id="spending-call",
        function=SimpleNamespace(name="coach_spending", arguments=json.dumps({
            "request": "이번 달 소비를 분석해줘", "periodType": "MONTHLY"
        })),
        model_dump=Mock(return_value={"id": "spending-call", "type": "function"}),
    )
    client.chat.completions.create.side_effect = [
        SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=None, tool_calls=[tool_call]))]),
        SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content="소비 분석 결과"))]),
    ]

    agent = FinancialAgent(client)
    answer = agent.run(
        "이번 달 소비를 분석해줘",
        consumption_context=ConsumptionContext(transactions=[]),
    )

    assert answer == "소비 분석 결과"
    assert agent.selected_tool_result is not None
    assert agent.selected_tool_result["dataSufficiency"]["sufficient"] is False
    assert client.chat.completions.create.call_args_list[1].kwargs["max_completion_tokens"] == 1600
