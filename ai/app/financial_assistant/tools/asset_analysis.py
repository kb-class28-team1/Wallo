from __future__ import annotations

from typing import TYPE_CHECKING, Any

from app.financial_assistant.tool_result import ToolResult
from app.financial_assistant.tools.pending import build_tool_schema

if TYPE_CHECKING:
    from app.chat.schemas import AssetAnalysisContext

NAME = "analyze_assets"
SCHEMA = build_tool_schema(
    NAME,
    "사용자의 예금, 투자, 부채, 소득과 현금흐름을 분석하고 실행 방안을 제시한다.",
)


def build_metrics(context: AssetAnalysisContext) -> dict[str, Any]:
    return {
        "monthlyNetIncomeKrw": context.monthly_income,
        "monthlySavingKrw": context.monthly_saving,
        "monthlyExpenseKrw": context.monthly_expense,
        "monthlySurplusKrw": context.monthly_saving,
        "annualSavingKrw": context.monthly_saving * 12,
        "savingRatePercent": context.saving_rate_percent,
        "totalAssetsKrw": context.total_assets,
        "totalDebtKrw": context.total_debt,
        "netAssetsKrw": context.net_assets,
    }


def build_profile(context: AssetAnalysisContext) -> dict[str, Any]:
    asset_items = [
        {
            "name": item.category,
            "category": item.category,
            "amount_krw": item.amount,
            "share_percent_approx": item.share_percent,
        }
        for item in context.asset_composition
    ]
    profile: dict[str, Any] = {
        "income": {
            "current_monthly_net_income_krw": context.monthly_income,
        },
        "cashflow": {
            "monthly_saving_krw": context.monthly_saving,
            "monthly_expense_krw": context.monthly_expense,
        },
        "assets": {
            "total_assets_krw": context.total_assets,
            "items": asset_items,
        },
        "total_debt_krw": context.total_debt,
    }
    if context.as_of:
        profile["as_of"] = context.as_of
        profile["data_quality_notes"] = [
            f"연동 DB 자산 데이터 기준 시각: {context.as_of}",
        ]
    return profile


def _empty_data(arguments: dict[str, Any]) -> dict[str, Any]:
    return {
        "dataMode": "database",
        "request": arguments.get("request", ""),
        "calculatedMetrics": {},
        "profile": {},
    }


def execute(
    tool_name: str,
    arguments: dict[str, Any],
    asset_analysis_context: AssetAnalysisContext | None = None,
) -> ToolResult:
    if asset_analysis_context is None:
        return ToolResult(
            status="needs_input",
            tool=tool_name,
            data=_empty_data(arguments),
            message="백엔드 자산분석 컨텍스트가 필요합니다.",
        )

    return ToolResult(
        status="success",
        tool=tool_name,
        data={
            "dataMode": "database",
            "request": arguments.get("request", ""),
            "calculatedMetrics": build_metrics(asset_analysis_context),
            "profile": build_profile(asset_analysis_context),
        },
        message="백엔드 데이터베이스 자산분석 컨텍스트를 사용했습니다.",
    )
