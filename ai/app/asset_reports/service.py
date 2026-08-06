import json
import logging

from groq import Groq, GroqError
from pydantic import ValidationError

from app.clients.groq_client import create_groq_client
from app.core.config import get_groq_model

from .prompts import CONSUMPTION_INSIGHT_INSTRUCTIONS, build_consumption_insight_input
from .schemas import (
    ConsumptionInsightGenerateRequest,
    ConsumptionInsightGenerateResponse,
)


logger = logging.getLogger("uvicorn.error")
CONSUMPTION_INSIGHT_MAX_COMPLETION_TOKENS = 2000


class InvalidConsumptionInsightResponseError(ValueError):
    """Groq 응답이 소비 리포트 응답 계약을 벗어난 경우."""


def _build_response_format() -> dict[str, str]:
    # gpt-oss-20b에서 json_schema 강제 응답은 Groq의 사전 JSON 검증 오류를
    # 발생시킬 수 있으므로, 모델 공통 지원 형식인 json_object를 사용한다.
    # 응답 필드와 길이는 _parse_response의 Pydantic 검증으로 보장한다.
    return {"type": "json_object"}


def _build_completion_options(model: str) -> dict:
    options = {
        "response_format": _build_response_format(),
        "max_completion_tokens": CONSUMPTION_INSIGHT_MAX_COMPLETION_TOKENS,
    }
    if model.startswith("openai/gpt-oss-"):
        options["reasoning_effort"] = "low"
    return options


def _call_groq(client: Groq, request: ConsumptionInsightGenerateRequest, model: str):
    try:
        return client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": CONSUMPTION_INSIGHT_INSTRUCTIONS},
                {"role": "user", "content": build_consumption_insight_input(request)},
            ],
            **_build_completion_options(model),
        )
    except GroqError as error:
        status_code = getattr(error, "status_code", "unknown")
        logger.exception(
            "Groq consumption insight request failed status=%s errorType=%s",
            status_code,
            type(error).__name__,
        )
        raise


def _parse_response(response) -> ConsumptionInsightGenerateResponse:
    try:
        if not response.choices:
            raise ValueError("Groq consumption insight response has no choices")

        content = response.choices[0].message.content
        if not content:
            raise ValueError("Groq consumption insight response has no content")

        return ConsumptionInsightGenerateResponse.model_validate_json(content)
    except (json.JSONDecodeError, ValidationError, ValueError, AttributeError, IndexError) as error:
        logger.error(
            "Groq consumption insight response validation failed: %s: %s",
            type(error).__name__,
            error,
        )
        raise InvalidConsumptionInsightResponseError from error


def generate_consumption_insight(
    client: Groq,
    request: ConsumptionInsightGenerateRequest,
    model: str,
) -> ConsumptionInsightGenerateResponse:
    response = _call_groq(client, request, model)
    return _parse_response(response)


def generate_consumption_insight_with_config(
    request: ConsumptionInsightGenerateRequest,
) -> ConsumptionInsightGenerateResponse:
    return generate_consumption_insight(
        create_groq_client(),
        request,
        get_groq_model(),
    )
