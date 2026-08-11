from collections.abc import Callable
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
TOOL_HANDLERS: dict[str, Callable[[str, dict[str, Any]], ToolResult]] = {
    module.NAME: module.execute for module in TOOL_MODULES
}


def execute_tool(tool_name: str, arguments: dict[str, Any],
                 consumption_context: ConsumptionContext | None = None) -> ToolResult:
    handler = TOOL_HANDLERS.get(tool_name)
    if handler is None:
        return ToolResult(
            status="error",
            tool=tool_name,
            message=f"지원하지 않는 도구입니다: {tool_name}",
        )
    if tool_name == spending_coach.NAME:
        return spending_coach.execute(tool_name, arguments, consumption_context)
    return handler(tool_name, arguments)
