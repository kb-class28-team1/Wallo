from app.agents.financial.tools.pending import build_tool_schema, pending_result

NAME = "analyze_assets"
SCHEMA = build_tool_schema(NAME, "사용자의 예금, 투자, 부채 등 보유 자산 현황을 분석한다.")
execute = pending_result
