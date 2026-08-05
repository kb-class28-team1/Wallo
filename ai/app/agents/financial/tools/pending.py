from typing import Any

from app.agents.base import ToolResult


def build_tool_schema(name: str, description: str) -> dict[str, Any]:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": {
                    "request": {
                        "type": "string",
                        "description": "도구로 처리할 사용자의 원래 요청",
                    }
                },
                "required": ["request"],
                "additionalProperties": False,
            },
        },
    }


def pending_result(tool_name: str, arguments: dict[str, Any]) -> ToolResult:
    return ToolResult(
        status="pending_integration",
        tool=tool_name,
        data={"request": arguments.get("request", "")},
        message="도구가 선택되었습니다. 실제 금융 데이터 서비스 연결이 필요합니다.",
    )
