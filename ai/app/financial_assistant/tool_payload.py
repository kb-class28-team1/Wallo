"""도구 원본 결과와 모델 전달용 결과를 분리한다."""

from typing import Any

from app.financial_assistant.tool_result import ToolResult


MAX_LIST_ITEMS = 5
PRODUCT_RESULT_LIMIT = 3
MAX_TEXT_CHARS = 400


def _trim_text(value: Any, max_chars: int = MAX_TEXT_CHARS) -> Any:
    if not isinstance(value, str) or len(value) <= max_chars:
        return value
    return value[:max_chars].rstrip() + "..."


def _copy_fields(
    source: dict[str, Any],
    fields: tuple[str, ...],
    *,
    text_limit: int | None = None,
) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for field in fields:
        if field not in source:
            continue
        value = source[field]
        result[field] = (
            _trim_text(value, text_limit)
            if text_limit is not None
            else value
        )
    return result


def _compact_records(
    records: Any,
    fields: tuple[str, ...],
    *,
    limit: int = MAX_LIST_ITEMS,
    text_limit: int | None = MAX_TEXT_CHARS,
) -> list[dict[str, Any]]:
    if not isinstance(records, list):
        return []
    return [
        _copy_fields(record, fields, text_limit=text_limit)
        for record in records[:limit]
        if isinstance(record, dict)
    ]


def _compact_text_list(value: Any, limit: int = MAX_LIST_ITEMS) -> list[Any]:
    if not isinstance(value, list):
        return []
    return [_trim_text(item) for item in value[:limit]]


def _compact_asset_profile(profile: Any) -> Any:
    if not isinstance(profile, dict):
        return profile

    compact = _copy_fields(
        profile,
        (
            "profile_id",
            "nickname",
            "title",
            "employment",
            "housing_type",
            "total_debt_krw",
        ),
        text_limit=MAX_TEXT_CHARS,
    )
    compact["income"] = _copy_fields(
        profile.get("income") or {},
        (
            "annual_gross_income_krw",
            "current_monthly_net_income_krw",
            "current_monthly_income_range_krw",
        ),
    )
    compact["cashflow"] = _copy_fields(
        profile.get("cashflow") or {},
        ("monthly_saving_krw", "monthly_expense_krw"),
    )
    assets = profile.get("assets") or {}
    compact["assets"] = _copy_fields(assets, ("total_assets_krw",))
    compact["assets"]["items"] = _compact_records(
        assets.get("items"),
        (
            "name",
            "category",
            "share_percent_approx",
            "amount_krw_derived_approx",
            "amount_range_krw",
            "amount_krw",
        ),
    )
    compact["data_quality_notes"] = _compact_text_list(
        profile.get("data_quality_notes")
    )
    return compact


def _compact_asset_data(data: dict[str, Any]) -> dict[str, Any]:
    compact = _copy_fields(
        data,
        ("dataMode", "profileId", "request", "calculatedMetrics"),
    )
    if "profile" in data:
        compact["profile"] = _compact_asset_profile(data["profile"])
    return compact


PRODUCT_FIELDS = (
    "financialGroup",
    "companyName",
    "productName",
    "interestCalculation",
    "baseRatePercent",
    "afterTaxRatePercent",
    "preferentialRatePercent",
    "joinWay",
    "joinTarget",
    "preferentialConditions",
    "maturityInterest",
    "maximumLimitKrw",
    "estimatedAfterTaxInterestKrw",
    "estimatedMaturityAmountKrw",
    "savingType",
    "monthlyPaymentKrw",
)


def _compact_product_data(data: dict[str, Any]) -> dict[str, Any]:
    compact = _copy_fields(
        data,
        (
            "request",
            "dataMode",
            "productType",
            "termMonths",
            "amountKrw",
            "amountMeaning",
            "joinPreference",
            "missingFields",
            "searchSummary",
        ),
    )
    if "products" in data:
        products = data["products"]
        compact["products"] = _compact_records(
            products,
            PRODUCT_FIELDS,
            limit=PRODUCT_RESULT_LIMIT,
        )
        if isinstance(products, list) and products and isinstance(products[0], dict):
            for field in ("estimateAssumption", "disclosureMonth", "collectedAt"):
                if field in products[0]:
                    compact[field] = _trim_text(products[0][field])
    return compact


def _compact_patterns(patterns: Any) -> dict[str, Any]:
    if not isinstance(patterns, dict):
        return {}
    compact = _copy_fields(patterns, ("weekendConcentration",))
    compact["weekdayHabits"] = _compact_records(
        patterns.get("weekdayHabits"),
        ("weekday", "weeks", "transactionCount"),
    )
    compact["timeSlotHabits"] = _compact_records(
        patterns.get("timeSlotHabits"),
        ("timeSlot", "weeks", "transactionCount"),
    )
    return compact


def _compact_spending_data(data: dict[str, Any]) -> dict[str, Any]:
    compact = _copy_fields(
        data,
        (
            "request",
            "periodType",
            "periodOffset",
            "periodLabel",
            "analysisPeriod",
            "comparisonPeriod",
            "dataSufficiency",
            "message",
            "totalChange",
            "budgetStatus",
            "continuousImprovement",
        ),
    )
    for field, fields in (
        (
            "categoryOverview",
            (
                "category",
                "transactionCount",
                "maxTransactionAmount",
                "currentAmount",
                "previousAmount",
                "changeAmount",
                "changeRatePercent",
            ),
        ),
        (
            "categorySurges",
            ("category", "currentAmount", "previousAmount", "changeAmount", "changeRatePercent"),
        ),
        (
            "newSpending",
            ("category", "amount", "count"),
        ),
        (
            "oneOffHighSpending",
            ("category", "maxTransactionAmount", "categoryAmount", "sharePercent", "medianOutlier"),
        ),
        (
            "repeatingCategories",
            ("category", "amount", "count", "max"),
        ),
        (
            "recurringPaymentCandidates",
            ("merchant", "count", "typicalAmount", "confirmationRequired"),
        ),
        (
            "positiveImprovements",
            ("category", "currentAmount", "previousAmount", "changeAmount", "changeRatePercent"),
        ),
    ):
        if field in data:
            compact[field] = _compact_records(data[field], fields)
    if "patterns" in data:
        compact["patterns"] = _compact_patterns(data["patterns"])
    return compact


def _compact_data(tool_name: str, data: dict[str, Any]) -> dict[str, Any]:
    if tool_name == "analyze_assets":
        return _compact_asset_data(data)
    if tool_name == "recommend_financial_products":
        return _compact_product_data(data)
    if tool_name == "coach_spending":
        return _compact_spending_data(data)
    return data


def compact_tool_result_for_prompt(result: ToolResult) -> dict[str, Any]:
    """최종 모델에 전달할 도구 결과를 축약한다.

    UI 응답과 캐시에는 사용하지 않고, LLM tool 메시지에만 적용한다.
    오류·추가 입력 상태의 메시지와 데이터는 그대로 보존한다.
    """
    payload: dict[str, Any] = {
        "status": result.status,
        "tool": result.tool,
    }
    if result.message:
        payload["message"] = result.message
    if result.data is not None:
        payload["data"] = (
            _compact_data(result.tool, result.data)
            if isinstance(result.data, dict)
            else result.data
        )
    return payload
