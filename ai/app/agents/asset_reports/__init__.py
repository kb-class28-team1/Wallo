"""소비 인사이트 생성 전용 Agent."""

from app.agents.asset_reports.agent import (
    ConsumptionInsightAgent,
    InvalidConsumptionInsightResponseError,
)

__all__ = [
    "ConsumptionInsightAgent",
    "InvalidConsumptionInsightResponseError",
]
