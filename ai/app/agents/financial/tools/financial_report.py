from app.agents.financial.tools.pending import build_tool_schema, pending_result

NAME = "generate_financial_report"
SCHEMA = build_tool_schema(NAME, "사용자의 자산과 소비를 종합한 개인 금융 리포트를 생성한다.")
execute = pending_result
