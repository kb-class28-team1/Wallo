import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from app.agents.base import ToolResult
from app.agents.financial.tools.pending import build_tool_schema
from app.core.config import get_demo_asset_profile_id

NAME = "analyze_assets"
SCHEMA = build_tool_schema(
    NAME,
    "사용자의 예금, 투자, 부채, 소득과 현금흐름을 분석하고 실행 방안을 제시한다.",
)

DATA_FILE = (
    Path(__file__).resolve().parents[4]
    / "data"
    / "processed"
    / "selected_asset_profiles.json"
)


@lru_cache(maxsize=1)
def load_selected_profiles() -> dict[int, dict[str, Any]]:
    try:
        payload = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError("선별 자산 프로필 JSON을 읽지 못했습니다.") from error

    profiles = payload.get("profiles")
    if not isinstance(profiles, list):
        raise RuntimeError("선별 자산 프로필 JSON 형식이 올바르지 않습니다.")
    return {
        profile["profile_id"]: profile
        for profile in profiles
        if isinstance(profile, dict) and isinstance(profile.get("profile_id"), int)
    }


def build_metrics(profile: dict[str, Any]) -> dict[str, Any]:
    income = profile.get("income") or {}
    cashflow = profile.get("cashflow") or {}
    assets = profile.get("assets") or {}
    monthly_income = income.get("current_monthly_net_income_krw")
    monthly_saving = cashflow.get("monthly_saving_krw")
    monthly_expense = cashflow.get("monthly_expense_krw")
    total_assets = assets.get("total_assets_krw")
    total_debt = profile.get("total_debt_krw")

    return {
        "monthlyNetIncomeKrw": monthly_income,
        "monthlySavingKrw": monthly_saving,
        "monthlyExpenseKrw": monthly_expense,
        "monthlySurplusKrw": (
            monthly_income - monthly_expense
            if monthly_income is not None and monthly_expense is not None
            else None
        ),
        "annualSavingKrw": (
            monthly_saving * 12 if monthly_saving is not None else None
        ),
        "savingRatePercent": (
            round(monthly_saving / monthly_income * 100, 1)
            if monthly_saving is not None and monthly_income
            else None
        ),
        "totalAssetsKrw": total_assets,
        "totalDebtKrw": total_debt,
        "netAssetsKrw": (
            total_assets - total_debt
            if total_assets is not None and total_debt is not None
            else None
        ),
    }


def execute(tool_name: str, arguments: dict[str, Any]) -> ToolResult:
    try:
        profile_id = get_demo_asset_profile_id()
        profile = load_selected_profiles().get(profile_id)
    except RuntimeError as error:
        return ToolResult(status="error", tool=tool_name, message=str(error))

    if profile is None:
        return ToolResult(
            status="error",
            tool=tool_name,
            message=f"선별 자산 프로필을 찾을 수 없습니다: {profile_id}",
        )

    return ToolResult(
        status="success",
        tool=tool_name,
        data={
            "dataMode": "demo_json",
            "profileId": profile_id,
            "request": arguments.get("request", ""),
            "calculatedMetrics": build_metrics(profile),
            "profile": profile,
            "analysisInstructions": [
                "calculatedMetrics의 수치를 그대로 사용하고 다시 계산하지 않는다.",
                "입력에 없는 금융정보는 추측하지 않는다.",
                "현재 상태, 강점, 위험 신호, 우선 행동 3가지를 한국어로 설명한다.",
                "테스트용 가상 사용자 데이터라는 사실을 답변 마지막에 알린다.",
            ],
        },
        message="선별된 가상 사용자 JSON을 불러왔습니다.",
    )
