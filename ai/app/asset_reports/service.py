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


class InvalidConsumptionInsightResponseError(ValueError):
    """Groq 응답이 소비 리포트 응답 계약을 벗어난 경우."""


def _build_response_format() -> dict[str, object]:
    return {
        "type": "json_schema",
        "json_schema": {
            "name": "consumption_insight",
            "strict": True,
            "schema": {
                "type": "object",
                "properties": {
                    "reportTitle": {"type": "string"},
                    "reportContent": {"type": "string"},
                },
                "required": ["reportTitle", "reportContent"],
                "additionalProperties": False,
            },
        },
    }


def _call_groq(client: Groq, request: ConsumptionInsightGenerateRequest, model: str):
    try:
        return client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": CONSUMPTION_INSIGHT_INSTRUCTIONS},
                {"role": "user", "content": build_consumption_insight_input(request)},
            ],
            response_format=_build_response_format(),
            max_completion_tokens=1000,
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
