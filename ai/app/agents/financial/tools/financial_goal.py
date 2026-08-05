from app.agents.financial.tools.pending import build_tool_schema, pending_result

NAME = "set_financial_goal"
SCHEMA = build_tool_schema(NAME, "저축, 투자, 부채 상환 등 금융 목표를 설정하거나 수정한다.")
execute = pending_result
