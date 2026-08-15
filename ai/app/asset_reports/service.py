from app.agents.asset_reports.agent import (
    CONSUMPTION_INSIGHT_MAX_COMPLETION_TOKENS,
    ConsumptionInsightAgent,
    InvalidConsumptionInsightResponseError,
)
from app.clients.groq_client import create_groq_client
from app.core.config import get_groq_model

from .schemas import (
    ConsumptionInsightGenerateRequest,
    ConsumptionInsightGenerateResponse,
)


def generate_consumption_insight(
    client,
    request: ConsumptionInsightGenerateRequest,
    model: str,
) -> ConsumptionInsightGenerateResponse:
    return ConsumptionInsightAgent(client, model=model).generate(request)


def generate_consumption_insight_with_config(
    request: ConsumptionInsightGenerateRequest,
) -> ConsumptionInsightGenerateResponse:
    return generate_consumption_insight(
        create_groq_client(),
        request,
        get_groq_model(),
    )
