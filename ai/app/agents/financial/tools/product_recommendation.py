from app.agents.financial.tools.pending import build_tool_schema, pending_result

NAME = "recommend_financial_products"
SCHEMA = build_tool_schema(NAME, "예금, 적금, 카드, 대출, 투자 등 금융 상품을 탐색하거나 비교한다.")
execute = pending_result
