from app.agents.financial.tools.pending import build_tool_schema, pending_result

NAME = "create_goal_roadmap"
SCHEMA = build_tool_schema(NAME, "금융 목표 달성을 위한 단계별 일정과 실행 로드맵을 만든다.")
execute = pending_result
