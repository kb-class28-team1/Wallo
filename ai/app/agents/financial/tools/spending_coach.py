from __future__ import annotations

from collections import Counter, defaultdict
from calendar import monthrange
from datetime import date, timedelta
from statistics import median
from typing import Any
import re

from app.agents.base import ToolResult
from app.agents.financial.consumption_models import ConsumptionContext, ConsumptionTransaction

NAME = "coach_spending"
SCHEMA = {
    "type": "function",
    "function": {
        "name": NAME,
        "description": "사용자의 실제 지출을 주간, 월간 또는 지정 기간으로 분석한다.",
        "parameters": {
            "type": "object",
            "properties": {
                "request": {"type": "string", "description": "사용자의 원래 요청"},
                "periodType": {"type": "string", "enum": ["WEEKLY", "MONTHLY", "CUSTOM"]},
                "referenceDate": {"type": "string", "format": "date", "description": "주간/월간 기준일. 생략 시 오늘"},
                "periodOffset": {
                    "type": "integer",
                    "minimum": -120,
                    "maximum": 0,
                    "default": 0,
                    "description": "현재 기간은 0, 지난달/지난주는 -1, 전전 기간은 -2",
                },
                "targetYear": {
                    "type": "integer",
                    "minimum": 2000,
                    "maximum": 2100,
                    "description": "사용자가 연도를 명시했을 때만 전달",
                },
                "targetMonth": {
                    "type": "integer",
                    "minimum": 1,
                    "maximum": 12,
                    "description": "사용자가 특정 월을 말했을 때 전달. 연도 생략 시 현재 연도 사용",
                },
                "startDate": {"type": "string", "format": "date", "description": "CUSTOM 시작일"},
                "endDate": {"type": "string", "format": "date", "description": "CUSTOM 종료일"},
                "comparisonStartDate": {"type": "string", "format": "date"},
                "comparisonEndDate": {"type": "string", "format": "date"},
                "periodLabel": {"type": "string"},
                "focus": {
                    "type": "string",
                    "enum": ["OVERVIEW", "PATTERN", "BUDGET", "CATEGORY", "CHANGE", "SUBSCRIPTION", "IMPROVEMENT", "SIGNALS"],
                },
            },
            "required": ["request", "periodType"],
        },
    },
}

CONTROLLABLE = {"CAFE", "DELIVERY", "SHOPPING", "CULTURE", "FOOD", "DINING"}
NON_PRAISE = {"HOUSING", "HEALTH", "EDUCATION", "LOAN_REPAYMENT"}


def _shift_month(reference: date, offset: int) -> date:
    month_index = reference.year * 12 + reference.month - 1 + offset
    year, zero_based_month = divmod(month_index, 12)
    return date(year, zero_based_month + 1, 1)


def _period_label(kind: str, start: date, end: date, today: date) -> str:
    if kind == "MONTHLY":
        if start == today.replace(day=1):
            return "이번 달"
        if start == _shift_month(today, -1):
            return "지난달"
        return f"{start.year}년 {start.month}월"
    if kind == "WEEKLY":
        current_start = today - timedelta(days=today.weekday())
        if start == current_start:
            return "이번 주"
        if start == current_start - timedelta(weeks=1):
            return "지난주"
        return f"{start.isoformat()}~{end.isoformat()}"
    return "지정 기간"


def _period(
    arguments: dict[str, Any], today: date
) -> tuple[date, date, date, date, str, int]:
    kind = str(arguments.get("periodType", "MONTHLY")).upper()
    offset = int(arguments.get("periodOffset", 0))
    if offset > 0 or offset < -120:
        raise ValueError("periodOffset은 -120부터 0까지만 사용할 수 있습니다.")
    request = str(arguments.get("request", ""))
    requested_month = re.search(
        r"(?:(?P<year>20\d{2})\s*년\s*)?(?P<month>1[0-2]|0?[1-9])\s*월(?:달)?",
        request,
    )
    if requested_month:
        # 사용자가 연도를 말하지 않았다면 모델이 추측한 연도를 사용하지 않고
        # 서버의 현재 연도를 적용한다.
        requested_year = requested_month.group("year")
        year = int(requested_year) if requested_year else today.year
        reference = date(year, int(requested_month.group("month")), 1)
        kind = "MONTHLY"
        offset = 0
    elif arguments.get("targetMonth") is not None:
        year = int(arguments.get("targetYear") or today.year)
        reference = date(year, int(arguments["targetMonth"]), 1)
        kind = "MONTHLY"
        offset = 0
    else:
        reference = date.fromisoformat(
            arguments.get("referenceDate") or today.isoformat()
        )
    if kind == "CUSTOM":
        if offset != 0:
            raise ValueError("CUSTOM 기간에는 periodOffset을 사용할 수 없습니다.")
        start = date.fromisoformat(arguments["startDate"])
        end = date.fromisoformat(arguments["endDate"])
        if end < start:
            raise ValueError("종료일은 시작일보다 빠를 수 없습니다.")
        if arguments.get("comparisonStartDate") and arguments.get("comparisonEndDate"):
            comparison_start = date.fromisoformat(arguments["comparisonStartDate"])
            comparison_end = date.fromisoformat(arguments["comparisonEndDate"])
            if comparison_end < comparison_start:
                raise ValueError("비교 종료일은 비교 시작일보다 빠를 수 없습니다.")
            return start, end, comparison_start, comparison_end, kind, offset
        days = (end - start).days + 1
        return (start, end, start - timedelta(days=days),
                start - timedelta(days=1), kind, offset)
    if kind == "WEEKLY":
        reference += timedelta(weeks=offset)
        start = reference - timedelta(days=reference.weekday())
        end = reference if start <= today <= start + timedelta(days=6) else start + timedelta(days=6)
        comparison_start = start - timedelta(days=7)
        return (start, end, comparison_start,
                comparison_start + (end - start), kind, offset)
    start = _shift_month(reference, offset)
    next_month = (start.replace(day=28) + timedelta(days=4)).replace(day=1)
    month_end = next_month - timedelta(days=1)
    end = today if start <= today <= month_end else month_end
    previous_end = start - timedelta(days=1)
    return (start, end, previous_end.replace(day=1), previous_end,
            "MONTHLY", offset)


def _in(items: list[ConsumptionTransaction], start: date, end: date) -> list[ConsumptionTransaction]:
    return [item for item in items if start <= item.date <= end and item.amount > 0]


def _by_category(items: list[ConsumptionTransaction]) -> dict[str, dict[str, int]]:
    result: dict[str, dict[str, int]] = defaultdict(lambda: {"amount": 0, "count": 0, "max": 0})
    for item in items:
        row = result[item.category]
        row["amount"] += item.amount
        row["count"] += 1
        row["max"] = max(row["max"], item.amount)
    return dict(result)


def _change(current: int, previous: int) -> dict[str, Any]:
    difference = current - previous
    return {"currentAmount": current, "previousAmount": previous, "changeAmount": difference,
            "changeRatePercent": round(difference / previous * 100, 1) if previous else None}


def _repeating_patterns(items: list[ConsumptionTransaction], end: date) -> dict[str, Any]:
    # 진행 중인 주의 일부 데이터가 습관 판정에 섞이지 않도록 분석 종료일
    # 이전에 완료된 월요일~일요일 4개 주만 사용한다.
    completed_end = end if end.weekday() == 6 else end - timedelta(days=end.weekday() + 1)
    recent_start = completed_end - timedelta(days=27)
    recent = _in(items, recent_start, completed_end)
    week_starts = [recent_start + timedelta(weeks=index) for index in range(4)]
    weekday_amounts: dict[int, int] = defaultdict(int)
    weekday_counts: Counter[int] = Counter()
    week_day_amounts: dict[tuple[date, int], int] = defaultdict(int)
    for tx in recent:
        weekday_amounts[tx.date.weekday()] += tx.amount
        weekday_counts[tx.date.weekday()] += 1
        week_start = tx.date - timedelta(days=tx.date.weekday())
        week_day_amounts[(week_start, tx.date.weekday())] += tx.amount
    # 각 완료 주에서 총지출이 가장 큰 요일을 하나만 고른 뒤, 같은 요일이
    # 4주 중 3주 이상 1위를 차지했을 때만 반복 요일 신호로 제공한다.
    weekly_winners: Counter[int] = Counter()
    for week in week_starts:
        winner = max(
            range(7),
            key=lambda day: (week_day_amounts[(week, day)], -day),
        )
        if week_day_amounts[(week, winner)] > 0:
            weekly_winners[winner] += 1
    weekday_candidates = [
        day for day, repeated in weekly_winners.items()
        if repeated >= 3 and weekday_counts[day] >= 3
    ]
    weekday_candidates.sort(
        key=lambda day: (-weekly_winners[day], -weekday_amounts[day], day)
    )
    weekday_signals = []
    if weekday_candidates:
        day = weekday_candidates[0]
        weekday_signals.append({
            "weekday": day,
            "weeks": weekly_winners[day],
            "transactionCount": weekday_counts[day],
        })

    weekend = [tx for tx in recent if tx.date.weekday() >= 5]
    weekday = [tx for tx in recent if tx.date.weekday() < 5]
    weekend_avg = sum(tx.amount for tx in weekend) / 8
    weekday_avg = sum(tx.amount for tx in weekday) / 20

    slots = {"DAWN": (0, 5), "MORNING": (6, 11), "AFTERNOON": (12, 17), "EVENING": (18, 23)}
    slot_result = []
    weekly_totals: dict[date, int] = defaultdict(int)
    weekly_slots: dict[tuple[date, str], int] = defaultdict(int)
    for tx in recent:
        week = tx.date - timedelta(days=tx.date.weekday())
        weekly_totals[week] += tx.amount
        for slot, (low, high) in slots.items():
            if low <= tx.time.hour <= high:
                weekly_slots[(week, slot)] += tx.amount
    for slot in slots:
        slot_txs = [tx for tx in recent if slots[slot][0] <= tx.time.hour <= slots[slot][1]]
        repeated = sum(1 for week in week_starts
                       if weekly_totals[week]
                       and weekly_slots[(week, slot)] / weekly_totals[week] >= .4)
        amount = sum(tx.amount for tx in slot_txs)
        dominant = bool(amount and max((tx.amount for tx in slot_txs), default=0) / amount >= .7)
        if repeated >= 3 and len(slot_txs) >= 3 and not dominant:
            slot_result.append({"timeSlot": slot, "weeks": repeated, "transactionCount": len(slot_txs)})
    return {"weekdayHabits": weekday_signals,
            "weekendConcentration": {"detected": len(weekend) >= 3 and weekend_avg >= weekday_avg * 1.3,
                                     "weekendDailyAverage": round(weekend_avg), "weekdayDailyAverage": round(weekday_avg)},
            "timeSlotHabits": slot_result}


def _recurring(items: list[ConsumptionTransaction]) -> list[dict[str, Any]]:
    merchants: dict[str, list[ConsumptionTransaction]] = defaultdict(list)
    for tx in items:
        key = "".join((tx.merchant_name or "").lower().split())
        if key:
            merchants[key].append(tx)
    result = []
    for merchant, txs in merchants.items():
        txs.sort(key=lambda tx: tx.date)
        if len(txs) < 3:
            continue
        intervals = [(b.date - a.date).days for a, b in zip(txs, txs[1:])]
        typical = median(tx.amount for tx in txs)
        if all(25 <= gap <= 35 for gap in intervals[-2:]) and all(abs(tx.amount - typical) <= typical * .1 for tx in txs[-3:]):
            result.append({"merchant": txs[-1].merchant_name, "count": len(txs), "typicalAmount": round(typical),
                           "confirmationRequired": True})
    return result


def _two_month_improvement(items: list[ConsumptionTransaction], end: date) -> bool:
    month_starts = []
    cursor = end.replace(day=1)
    if end.day < monthrange(end.year, end.month)[1]:
        cursor = (cursor - timedelta(days=1)).replace(day=1)
    for _ in range(3):
        month_starts.append(cursor)
        cursor = (cursor - timedelta(days=1)).replace(day=1)
    totals = []
    for start in reversed(month_starts):
        finish = date(start.year, start.month, monthrange(start.year, start.month)[1])
        totals.append(sum(tx.amount for tx in _in(items, start, finish)
                          if tx.category in CONTROLLABLE))
    return all(previous > current for previous, current in zip(totals, totals[1:]))


def analyze(context: ConsumptionContext, arguments: dict[str, Any], today: date | None = None) -> dict[str, Any]:
    today = today or date.today()
    start, end, previous_start, previous_end, kind, offset = _period(
        arguments, today
    )
    current = _in(context.transactions, start, end)
    previous = _in(context.transactions, previous_start, previous_end)
    current_total = sum(tx.amount for tx in current)
    previous_total = sum(tx.amount for tx in previous)
    # 반복 소비는 금액 규모보다 발생 횟수가 핵심이므로 총액 하한을 두지 않는다.
    sufficient = len(current) >= 3
    base = {"periodType": kind, "periodOffset": offset,
            "periodLabel": arguments.get("periodLabel") or _period_label(kind, start, end, today),
            "analysisPeriod": {"start": str(start), "end": str(end)},
            "comparisonPeriod": {"start": str(previous_start), "end": str(previous_end)},
            "dataSufficiency": {"sufficient": sufficient, "transactionCount": len(current), "totalExpense": current_total}}
    if not sufficient:
        base["message"] = (
            f"현재 분석 기간에는 지출 거래가 {len(current)}건 있어요. "
            "소비패턴을 판단하려면 최소 3건이 필요해요. "
            "내역이 조금 더 쌓이면 분석해드릴게요."
        )

    current_categories, previous_categories = _by_category(current), _by_category(previous)
    total_change = _change(current_total, previous_total)
    total_change["warning"] = previous_total > 0 and total_change["changeRatePercent"] >= 20 and total_change["changeAmount"] >= 50_000
    surges, new_spending, one_off = [], [], []
    for category, row in current_categories.items():
        old = previous_categories.get(category, {"amount": 0, "count": 0})
        change = _change(row["amount"], old["amount"])
        if old["amount"] and row["amount"] > old["amount"] and ((change["changeRatePercent"] >= 30 and change["changeAmount"] >= 30_000) or change["changeAmount"] >= 100_000):
            surges.append({"category": category, **change})
        if not old["amount"] and (row["amount"] >= 50_000 or row["count"] >= 3):
            new_spending.append({"category": category, "amount": row["amount"], "count": row["count"]})
        recent_before = [tx.amount for tx in context.transactions
                         if tx.date < start and tx.category == category and tx.amount > 0]
        median_signal = bool(recent_before and row["max"] >= median(recent_before) * 3
                             and row["max"] - median(recent_before) >= 50_000)
        if row["max"] / row["amount"] >= .7 or median_signal:
            one_off.append({"category": category, "maxTransactionAmount": row["max"], "categoryAmount": row["amount"],
                            "sharePercent": round(row["max"] / row["amount"] * 100, 1),
                            "medianOutlier": median_signal, "limitAutomaticReductionMission": True})
    surges.sort(key=lambda x: (-x["changeAmount"], -x["changeRatePercent"], x["category"]))
    repeating_categories = [{"category": category, **row} for category, row in current_categories.items() if row["count"] >= 3]
    improvements = []
    for category in CONTROLLABLE:
        now = current_categories.get(category, {"amount": 0})["amount"]
        old = previous_categories.get(category, {"amount": 0})["amount"]
        if old and old - now >= 30_000 and (old - now) / old >= .2 and category not in NON_PRAISE:
            improvements.append({"category": category, **_change(now, old)})
    budget = None
    target_month = start.strftime("%Y-%m")
    matching_budget = next(
        (item for item in context.budgets if item.target_month == target_month),
        None,
    )
    if matching_budget is None and context.budget and context.budget.target_month == target_month:
        matching_budget = context.budget
    same_month_period = start.year == end.year and start.month == end.month and start.day == 1
    if matching_budget and same_month_period:
        usage = current_total / matching_budget.total_amount * 100
        progress = end.day / monthrange(end.year, end.month)[1] * 100
        gap = usage - progress
        status = "OVER" if usage > 100 else "DANGER" if gap >= 20 else "CAUTION" if gap >= 10 else "NORMAL"
        budget = {"budgetAmount": matching_budget.total_amount, "usageRatePercent": round(usage, 1),
                  "monthProgressPercent": round(progress, 1), "gapPercentagePoints": round(gap, 1), "status": status}
    category_overview = []
    for category in sorted(set(current_categories) | set(previous_categories)):
        now = current_categories.get(category, {"amount": 0, "count": 0, "max": 0})
        old = previous_categories.get(category, {"amount": 0, "count": 0, "max": 0})
        category_overview.append({
            "category": category,
            "transactionCount": now["count"],
            "maxTransactionAmount": now["max"],
            **_change(now["amount"], old["amount"]),
        })
    category_overview.sort(key=lambda row: (-row["currentAmount"], row["category"]))

    patterns = _repeating_patterns(context.transactions, end)
    recurring_candidates = _recurring([tx for tx in context.transactions if tx.date <= end])
    continuous = _two_month_improvement(context.transactions, end)

    def evaluation(code: str, detected: bool, evaluated: bool = True, **metrics: Any) -> dict[str, Any]:
        return {
            "code": code,
            "evaluated": evaluated,
            "detected": detected if evaluated else False,
            "reasonCode": "DETECTED" if detected else "NOT_DETECTED" if evaluated else "INSUFFICIENT_DATA",
            "metrics": metrics,
        }

    pattern_evaluated = sufficient
    budget_evaluated = budget is not None
    criteria = [
        evaluation("DATA_SUFFICIENCY", sufficient, True,
                   transactionCount=len(current), minimumTransactionCount=3),
        evaluation("TOTAL_CHANGE", bool(total_change["warning"]), True,
                   currentAmount=current_total, previousAmount=previous_total,
                   changeAmount=total_change["changeAmount"],
                   changeRatePercent=total_change["changeRatePercent"]),
        evaluation("CATEGORY_SURGE", bool(surges), True, detectedCount=len(surges)),
        evaluation("NEW_SPENDING", bool(new_spending), True, detectedCount=len(new_spending)),
        evaluation("ONE_OFF_HIGH_SPENDING", bool(one_off), True, detectedCount=len(one_off)),
        evaluation("CATEGORY_REPEAT", bool(repeating_categories), pattern_evaluated,
                   detectedCount=len(repeating_categories)),
        evaluation("BUDGET_STATUS", budget is not None and budget["status"] != "NORMAL",
                   budget_evaluated, status=budget["status"] if budget else None),
        evaluation("WEEKDAY_REPEAT", bool(patterns["weekdayHabits"]), pattern_evaluated,
                   detectedCount=len(patterns["weekdayHabits"])),
        evaluation("WEEKEND_CONCENTRATION", bool(patterns["weekendConcentration"]["detected"]),
                   pattern_evaluated,
                   weekdayDailyAverage=patterns["weekendConcentration"]["weekdayDailyAverage"],
                   weekendDailyAverage=patterns["weekendConcentration"]["weekendDailyAverage"]),
        evaluation("TIME_SLOT_REPEAT", bool(patterns["timeSlotHabits"]), pattern_evaluated,
                   detectedCount=len(patterns["timeSlotHabits"])),
        evaluation("RECURRING_PAYMENT", bool(recurring_candidates), pattern_evaluated,
                   detectedCount=len(recurring_candidates)),
        evaluation("POSITIVE_IMPROVEMENT", bool(improvements) or continuous, True,
                   detectedCount=len(improvements), twoConsecutiveMonthsDecreased=continuous),
    ]

    base.update({"focus": arguments.get("focus") or "OVERVIEW",
                 "totalChange": total_change, "categoryOverview": category_overview,
                 "criteriaEvaluations": criteria,
                 "categorySurges": surges[:3], "newSpending": new_spending,
                 "oneOffHighSpending": one_off, "repeatingCategories": repeating_categories,
                 "budgetStatus": budget, "patterns": patterns,
                 "recurringPaymentCandidates": recurring_candidates,
                 "positiveImprovements": improvements,
                 "continuousImprovement": {"twoConsecutiveMonthsDecreased":
                                             continuous}})
    return base


def execute(tool_name: str, arguments: dict[str, Any], context: ConsumptionContext | None = None) -> ToolResult:
    if context is None:
        return ToolResult(status="error", tool=tool_name, message="소비 거래 컨텍스트가 전달되지 않았습니다.")
    try:
        result = analyze(context, arguments)
    except (KeyError, ValueError) as error:
        return ToolResult(status="error", tool=tool_name, message=f"분석 기간이 올바르지 않습니다: {error}")
    result["request"] = arguments.get("request", "")
    result["analysisInstructions"] = [
        "계산 결과를 재계산하거나 없는 사실을 추론하지 않는다.",
        "요청에 맞춰 잘한 점, 주의할 점, 소비 요약을 수치의 근거와 함께 설명한다.",
        "신호가 없는 항목을 억지로 문제로 만들지 않고 단발성 고액 소비에는 감축 미션을 제안하지 않는다.",
    ]
    return ToolResult(status="success", tool=tool_name, data=result)
