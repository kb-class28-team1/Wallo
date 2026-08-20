from collections.abc import Callable
import re
from typing import Any

from app.agents.base import ToolResult
from app.agents.financial.consumption_models import ConsumptionContext
from app.agents.financial.tools import (
    asset_analysis,
    financial_goal,
    financial_report,
    goal_roadmap,
    product_recommendation,
    spending_coach,
)

TOOL_MODULES = (
    asset_analysis,
    spending_coach,
    financial_goal,
    goal_roadmap,
    product_recommendation,
    financial_report,
)

TOOL_SCHEMAS = [module.SCHEMA for module in TOOL_MODULES]
TOOL_SCHEMAS_BY_NAME = {
    module.NAME: module.SCHEMA for module in TOOL_MODULES
}
TOOL_HANDLERS: dict[str, Callable[[str, dict[str, Any]], ToolResult]] = {
    module.NAME: module.execute for module in TOOL_MODULES
}

_ROUTE_TOOL_GROUPS = (
    (
        (
            "자산",
            "순자산",
            "부채",
            "재무",
            "리포트",
            "보고서",
            "종합분석",
        ),
        ("analyze_assets", "generate_financial_report"),
    ),
    (
        (
            "예금",
            "적금",
            "저축상품",
            "상품추천",
            "금융상품",
            "금리",
            "가입조건",
            "가입방법",
        ),
        ("recommend_financial_products",),
    ),
    (
        ("목표", "저축", "투자목표", "부채상환"),
        ("set_financial_goal",),
    ),
    (
        ("로드맵", "실행계획", "단계별계획"),
        ("create_goal_roadmap",),
    ),
)
_DIRECT_RESPONSE_TERMS = (
    "안녕",
    "반가워",
    "고마워",
    "감사",
    "도와줘",
    "좋아",
    "응",
    "네",
    "예",
    "뭐할수",
    "무엇을할수",
    "hello",
    "hi",
)


def select_route_tool_schemas(message: str) -> list[dict[str, Any]]:
    """현재 질문에 필요한 최소 도구 스키마를 선택한다."""
    normalized = re.sub(r"\s+", "", message or "").lower()
    selected_names: set[str] = set()
    for keywords, tool_names in _ROUTE_TOOL_GROUPS:
        if any(keyword in normalized for keyword in keywords):
            selected_names.update(tool_names)

    if selected_names:
        return [
            TOOL_SCHEMAS_BY_NAME[module.NAME]
            for module in TOOL_MODULES
            if module.NAME in selected_names
        ]
    if any(term in normalized for term in _DIRECT_RESPONSE_TERMS):
        return []
    return TOOL_SCHEMAS


def execute_tool(
    tool_name: str,
    arguments: dict[str, Any],
    consumption_context: ConsumptionContext | None = None,
    asset_analysis_context: Any | None = None,
) -> ToolResult:
    handler = TOOL_HANDLERS.get(tool_name)
    if handler is None:
        return ToolResult(
            status="error",
            tool=tool_name,
            message=f"지원하지 않는 도구입니다: {tool_name}",
        )
    if tool_name == spending_coach.NAME:
        return spending_coach.execute(tool_name, arguments, consumption_context)
    if tool_name == asset_analysis.NAME:
        return asset_analysis.execute(tool_name, arguments, asset_analysis_context)
    return handler(tool_name, arguments)
