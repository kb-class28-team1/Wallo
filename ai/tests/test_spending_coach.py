from datetime import date, time

from app.financial_assistant.tools.spending_coach import analyze
from app.financial_assistant.spending_intent import (
    build_spending_arguments,
    is_spending_request,
)
from app.financial_assistant.agent import FinancialAgent
from app.financial_assistant.consumption_models import ConsumptionBudget, ConsumptionContext, ConsumptionTransaction
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


def test_insufficient_data_keeps_calculable_results_and_limits_pattern_judgment():
    context = ConsumptionContext(transactions=[tx("2026-08-10", 10_000), tx("2026-08-11", 10_000)])
    result = analyze(context, {"periodType": "WEEKLY"}, date(2026, 8, 12))
    assert result["dataSufficiency"]["sufficient"] is False
    assert result["message"] == (
        "현재 분석 기간에는 지출 거래가 2건 있어요. "
        "소비패턴을 판단하려면 최소 3건이 필요해요. "
        "내역이 조금 더 쌓이면 분석해드릴게요."
    )
    assert "totalChange" in result
    assert "categoryOverview" in result
    assert "patterns" in result
    evaluations = {item["code"]: item for item in result["criteriaEvaluations"]}
    assert evaluations["CATEGORY_REPEAT"]["evaluated"] is False
    assert evaluations["WEEKDAY_REPEAT"]["evaluated"] is False


def test_three_small_transactions_are_enough_for_pattern_analysis():
    context = ConsumptionContext(transactions=[
        tx("2026-08-10", 4_500),
        tx("2026-08-11", 5_000),
        tx("2026-08-12", 5_500),
    ])

    result = analyze(context, {"periodType": "WEEKLY"}, date(2026, 8, 12))

    assert result["dataSufficiency"]["sufficient"] is True
    assert result["dataSufficiency"]["transactionCount"] == 3
    assert result["dataSufficiency"]["totalExpense"] == 15_000
    assert "patterns" in result


def test_previous_month_offset_analyzes_full_previous_month():
    context = ConsumptionContext(transactions=[
        tx("2026-06-01", 10_000), tx("2026-06-02", 10_000), tx("2026-06-03", 10_000),
        tx("2026-07-02", 100_000), tx("2026-07-15", 150_000), tx("2026-07-29", 158_500),
        tx("2026-08-01", 50_000), tx("2026-08-02", 50_000), tx("2026-08-03", 42_500),
    ])

    result = analyze(
        context,
        {"periodType": "MONTHLY", "periodOffset": -1},
        date(2026, 8, 11),
    )

    assert result["analysisPeriod"] == {"start": "2026-07-01", "end": "2026-07-31"}
    assert result["comparisonPeriod"] == {"start": "2026-06-01", "end": "2026-06-30"}
    assert result["dataSufficiency"]["transactionCount"] == 3
    assert result["dataSufficiency"]["totalExpense"] == 408_500
    assert result["periodOffset"] == -1
    assert result["periodLabel"] == "지난달"


def test_previous_week_offset_analyzes_completed_previous_week():
    context = ConsumptionContext(transactions=[
        tx("2026-07-27", 10_000), tx("2026-07-28", 10_000), tx("2026-07-29", 10_000),
        tx("2026-08-03", 20_000), tx("2026-08-05", 20_000), tx("2026-08-09", 20_000),
    ])

    result = analyze(
        context,
        {"periodType": "WEEKLY", "periodOffset": -1},
        date(2026, 8, 12),
    )

    assert result["analysisPeriod"] == {"start": "2026-08-03", "end": "2026-08-09"}
    assert result["comparisonPeriod"] == {"start": "2026-07-27", "end": "2026-08-02"}
    assert result["dataSufficiency"]["totalExpense"] == 60_000
    assert result["periodLabel"] == "지난주"


def test_month_without_year_uses_current_year_even_if_model_dates_are_wrong():
    context = ConsumptionContext(transactions=[
        tx("2026-07-02", 100_000), tx("2026-07-15", 150_000), tx("2026-07-29", 158_500),
    ])

    result = analyze(
        context,
        {
            "request": "7월달 소비 내역을 분석해봐",
            "periodType": "CUSTOM",
            "startDate": "2023-07-01",
            "endDate": "2023-07-31",
        },
        date(2026, 8, 11),
    )

    assert result["periodType"] == "MONTHLY"
    assert result["analysisPeriod"] == {"start": "2026-07-01", "end": "2026-07-31"}
    assert result["dataSufficiency"]["totalExpense"] == 408_500
    assert result["periodLabel"] == "지난달"


def test_month_with_year_keeps_explicit_year():
    context = ConsumptionContext(transactions=[
        tx("2025-07-01", 10_000), tx("2025-07-02", 10_000), tx("2025-07-03", 10_000),
    ])

    result = analyze(
        context,
        {"request": "2025년 7월 소비를 분석해줘", "periodType": "MONTHLY"},
        date(2026, 8, 11),
    )

    assert result["analysisPeriod"] == {"start": "2025-07-01", "end": "2025-07-31"}
    assert result["periodLabel"] == "2025년 7월"


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


def test_repeating_patterns_use_exactly_four_completed_weeks():
    transactions = [
        ConsumptionTransaction(
            date=date.fromisoformat(day),
            time=time(20),
            category="CAFE",
            amount=40_000,
        )
        for day in [
            "2026-07-13", "2026-07-20", "2026-07-27", "2026-08-03",
            # 분석 종료일(화요일)이 속한 진행 중인 주의 거래는 제외되어야 한다.
            "2026-08-10",
        ]
    ]

    result = analyze(
        ConsumptionContext(transactions=transactions),
        {"periodType": "MONTHLY"},
        date(2026, 8, 11),
    )

    patterns = result["patterns"]
    monday = next(item for item in patterns["weekdayHabits"] if item["weekday"] == 0)
    evening = next(item for item in patterns["timeSlotHabits"] if item["timeSlot"] == "EVENING")
    assert monday["weeks"] == 4
    assert evening["weeks"] == 4
    assert all(item["weeks"] <= 4 for item in patterns["weekdayHabits"])
    assert all(item["weeks"] <= 4 for item in patterns["timeSlotHabits"])


def test_weekday_pattern_only_returns_the_day_ranked_first_in_three_weeks():
    transactions = []
    for thursday, friday, saturday in [
        ("2026-07-16", "2026-07-17", "2026-07-18"),
        ("2026-07-23", "2026-07-24", "2026-07-25"),
        ("2026-07-30", "2026-07-31", "2026-08-01"),
    ]:
        transactions.extend([
            tx(thursday, 60_000, "CULTURE"),
            tx(friday, 70_000, "CAFE"),
            tx(saturday, 150_000, "DELIVERY"),
        ])

    result = analyze(
        ConsumptionContext(transactions=transactions),
        {"periodType": "MONTHLY"},
        date(2026, 8, 11),
    )

    assert result["patterns"]["weekdayHabits"] == [{
        "weekday": 5,
        "weeks": 3,
        "transactionCount": 3,
    }]


def test_category_overview_is_sorted_by_current_amount_descending():
    result = analyze(
        ConsumptionContext(transactions=[
            tx("2026-08-03", 10_000, "CAFE"),
            tx("2026-08-04", 50_000, "FOOD"),
            tx("2026-08-05", 30_000, "CULTURE"),
        ]),
        {"periodType": "MONTHLY"},
        date(2026, 8, 11),
    )

    assert [item["category"] for item in result["categoryOverview"]] == [
        "FOOD", "CULTURE", "CAFE",
    ]


def test_spending_analysis_uses_expanded_final_output_tokens():
    client = Mock()
    client.chat.completions.create.return_value = SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content="소비 분석 결과"))]
    )

    agent = FinancialAgent(client)
    answer = agent.run(
        "이번 달 소비를 분석해줘",
        consumption_context=ConsumptionContext(transactions=[]),
    )

    assert answer == "소비 분석 결과"
    assert agent.selected_tool_result is not None
    assert agent.selected_tool_result["dataSufficiency"]["sufficient"] is False
    assert client.chat.completions.create.call_count == 1
    assert client.chat.completions.create.call_args.kwargs["max_completion_tokens"] == 1000


def test_spending_intent_covers_indirect_consumption_questions():
    assert is_spending_request("내 소비패턴에 습관 같은 거 있어?") is True
    assert is_spending_request("나 요즘 잘 아끼고 있어?") is True
    assert is_spending_request("어디에 가장 많이 썼어?") is True
    assert is_spending_request("어디에 가장 많이 썼어?", {
        "startDate": "2026-07-01",
    }) is True
    assert is_spending_request("적금과 예금의 차이가 뭐야?") is False


def test_follow_up_inherits_exact_previous_analysis_period():
    arguments = build_spending_arguments("그 기간에 주말 소비는 어땠어?", {
        "type": "MONTHLY",
        "label": "지난달",
        "startDate": "2026-07-01",
        "endDate": "2026-07-31",
        "compareStart": "2026-06-01",
        "compareEnd": "2026-06-30",
    })

    assert arguments == {
        "request": "그 기간에 주말 소비는 어땠어?",
        "focus": "PATTERN",
        "periodType": "CUSTOM",
        "startDate": "2026-07-01",
        "endDate": "2026-07-31",
        "comparisonStartDate": "2026-06-01",
        "comparisonEndDate": "2026-06-30",
        "periodLabel": "지난달",
    }


def test_explicit_period_overrides_inherited_period():
    arguments = build_spending_arguments("이번 달 소비는 어때?", {
        "type": "MONTHLY",
        "label": "지난달",
        "startDate": "2026-07-01",
        "endDate": "2026-07-31",
        "compareStart": "2026-06-01",
        "compareEnd": "2026-06-30",
    })

    assert arguments["periodType"] == "MONTHLY"
    assert arguments["periodOffset"] == 0
    assert "startDate" not in arguments


def test_budget_analysis_selects_budget_for_inherited_analysis_month():
    context = ConsumptionContext(
        transactions=[
            tx("2026-07-02", 100_000),
            tx("2026-07-15", 100_000),
            tx("2026-07-29", 100_000),
        ],
        budgets=[
            ConsumptionBudget(targetMonth="2026-07", totalAmount=500_000),
            ConsumptionBudget(targetMonth="2026-08", totalAmount=900_000),
        ],
    )

    result = analyze(context, {
        "request": "예산 안에서 썼어?",
        "focus": "BUDGET",
        "periodType": "CUSTOM",
        "startDate": "2026-07-01",
        "endDate": "2026-07-31",
        "comparisonStartDate": "2026-06-01",
        "comparisonEndDate": "2026-06-30",
        "periodLabel": "지난달",
    }, date(2026, 8, 11))

    assert result["focus"] == "BUDGET"
    assert result["budgetStatus"]["budgetAmount"] == 500_000
    assert result["budgetStatus"]["usageRatePercent"] == 60.0
