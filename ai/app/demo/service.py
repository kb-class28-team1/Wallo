import json
from typing import Any

from groq import Groq

from app.core.config import get_groq_model
from app.demo.repository import load_demo_profiles
from app.demo.schemas import DemoProfileSummary


def list_demo_profiles() -> list[DemoProfileSummary]:
    summaries = []
    for profile_id, profile in sorted(load_demo_profiles().items()):
        source, assets, income = (
            profile.get("source") or {},
            profile.get("assets") or {},
            profile.get("income") or {},
        )
        summaries.append(DemoProfileSummary(
            profileId=profile_id,
            title=source.get("title") or f"가상 사용자 {profile_id}",
            totalAssetsKrw=assets.get("total_assets_krw"),
            monthlyNetIncomeKrw=income.get("monthly_net_income_krw"),
        ))
    return summaries


def build_demo_asset_facts(profile: dict[str, Any]) -> dict[str, Any]:
    income = profile.get("income") or {}
    assets = profile.get("assets") or {}
    cashflow = profile.get("cashflow") or {}
    debts = profile.get("debts") or []
    monthly_income = income.get("monthly_net_income_krw")
    monthly_saving = cashflow.get("monthly_saving_total_krw")
    monthly_expense = cashflow.get("monthly_total_expense_krw")
    total_assets = assets.get("total_assets_krw")
    asset_item_sum = sum(
        item.get("amount_krw") or 0
        for item in assets.get("items") or []
        if isinstance(item, dict)
    )
    total_debt = sum(
        debt.get("amount_krw") or 0 for debt in debts if isinstance(debt, dict)
    )
    return {
        "monthly_net_income_krw": monthly_income,
        "monthly_saving_krw": monthly_saving,
        "monthly_expense_krw": monthly_expense,
        "annual_saving_krw": monthly_saving * 12 if monthly_saving is not None else None,
        "saving_rate_percent": round(monthly_saving / monthly_income * 100, 1)
        if monthly_saving is not None and monthly_income else None,
        "total_assets_krw": total_assets,
        "listed_asset_items_sum_krw": asset_item_sum,
        "asset_detail_unexplained_gap_krw": total_assets - asset_item_sum
        if total_assets is not None else None,
        "total_debt_krw": total_debt,
    }


def compact_demo_profile(profile: dict[str, Any]) -> dict[str, Any]:
    excluded_keys = {"raw_user_content", "source"}

    def compact(value: Any) -> Any:
        if isinstance(value, dict):
            return {
                key: compact(item)
                for key, item in value.items()
                if key not in excluded_keys
                and not key.endswith("_raw")
                and not key.endswith("_evidence")
                and key != "raw_text"
            }
        if isinstance(value, list):
            return [compact(item) for item in value]
        return value
    return compact(profile)


def format_demo_asset_facts(facts: dict[str, Any]) -> str:
    def won(value: int | None) -> str:
        return "정보 없음" if value is None else f"{value:,}원"

    rate = facts.get("saving_rate_percent")
    return "\n".join([
        "## 코드로 계산한 핵심 수치",
        f"- 월 순소득: {won(facts.get('monthly_net_income_krw'))}",
        f"- 월 저축액: {won(facts.get('monthly_saving_krw'))}",
        f"- 월 지출액: {won(facts.get('monthly_expense_krw'))}",
        f"- 연 저축액: {won(facts.get('annual_saving_krw'))}",
        f"- 저축률: {'정보 없음' if rate is None else f'{rate}%'}",
        f"- 총자산: {won(facts.get('total_assets_krw'))}",
        f"- 세부 자산 항목 합계: {won(facts.get('listed_asset_items_sum_krw'))}",
        "- 총자산과 세부 항목의 미기재 차액: " + won(facts.get("asset_detail_unexplained_gap_krw")),
        f"- 총부채: {won(facts.get('total_debt_krw'))}",
    ])


def generate_demo_asset_analysis(client: Groq, profile: dict[str, Any], question: str) -> str:
    facts = build_demo_asset_facts(profile)
    completion = client.chat.completions.create(
        model=get_groq_model(),
        messages=[
            {"role": "system", "content": (
                "당신은 한국어 개인재무 자산분석가입니다. 제공된 수치만 근거로 분석하고 없는 정보는 "
                "추측하지 마세요. 답변은 ① 한줄 진단 ② 강점 ③ 위험 신호 ④ 우선 행동 3가지 "
                "⑤ 추가로 필요한 정보 순서로 작성하세요."
            )},
            {"role": "user", "content": (
                f"질문: {question}\n\n코드로 계산한 핵심 지표:\n"
                + json.dumps(facts, ensure_ascii=False, indent=2)
                + "\n\n가상 사용자 금융 데이터:\n"
                + json.dumps(compact_demo_profile(profile), ensure_ascii=False, indent=2)
            )},
        ],
        reasoning_effort="low",
        max_completion_tokens=4000,
    )
    analysis = completion.choices[0].message.content or "자산 분석 결과를 생성하지 못했습니다."
    return format_demo_asset_facts(facts) + "\n\n" + analysis
