from app.agents.financial.tools.pending import build_tool_schema, pending_result

NAME = "coach_spending"
SCHEMA = build_tool_schema(NAME, "사용자의 지출 내역과 소비 습관을 분석해 개선 방법을 제안한다.")
execute = pending_result
