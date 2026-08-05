import logging

from fastapi import APIRouter, HTTPException
from groq import GroqError

from .schemas import (
    ConsumptionInsightGenerateRequest,
    ConsumptionInsightGenerateResponse,
)
from .service import (
    InvalidConsumptionInsightResponseError,
    generate_consumption_insight_with_config,
)


logger = logging.getLogger("uvicorn.error")
router = APIRouter(prefix="/api/asset-reports/insights", tags=["asset-reports"])


def _handle_generation_error(error: Exception) -> None:
    if isinstance(error, RuntimeError):
        raise HTTPException(
            status_code=503,
            detail="GROQ_API_KEY가 설정되지 않았습니다.",
        ) from error

    if isinstance(error, GroqError):
        status_code = getattr(error, "status_code", "unknown")
        raise HTTPException(
            status_code=502,
            detail=f"Groq consumption insight generation failed (status={status_code})",
        ) from error

    if isinstance(error, InvalidConsumptionInsightResponseError):
        raise HTTPException(
            status_code=502,
            detail="Groq AI consumption insight response is invalid",
        ) from error

    raise error


@router.post("/generate", response_model=ConsumptionInsightGenerateResponse)
def generate_consumption_insight_report(
    request: ConsumptionInsightGenerateRequest,
) -> ConsumptionInsightGenerateResponse:
    try:
        return generate_consumption_insight_with_config(request)
    except (RuntimeError, GroqError, InvalidConsumptionInsightResponseError) as error:
        logger.error("Consumption insight generation request failed: %s", error)
        _handle_generation_error(error)
