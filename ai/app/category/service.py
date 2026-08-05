import json
import logging

from groq import GroqError
from pydantic import ValidationError

from app.clients.groq_client import create_groq_client
from app.core.config import get_groq_model

from .prompts import (
    build_batch_system_prompt,
    build_batch_user_prompt,
    build_single_system_prompt,
    build_single_user_prompt,
)
from .schemas import (
    CategoryClassificationBatchRequest,
    CategoryClassificationBatchResponse,
    CategoryClassificationRequest,
    CategoryClassificationResponse,
)


logger = logging.getLogger("uvicorn.error")


class InvalidCategoryResponseError(ValueError):
    """Raised when Groq returns a response outside the category contract."""


def _build_response_format() -> dict[str, str]:
    # json_object mode is supported by every Groq chat model used here. The
    # Pydantic models below still enforce the category and confidence contract.
    return {"type": "json_object"}


def _call_groq(messages: list[dict[str, str]], max_completion_tokens: int):
    try:
        return create_groq_client().chat.completions.create(
            model=get_groq_model(),
            messages=messages,
            response_format=_build_response_format(),
            max_completion_tokens=max_completion_tokens,
        )
    except RuntimeError:
        logger.error("GROQ_API_KEY is not configured for category classification")
        raise
    except GroqError as error:
        status_code = getattr(error, "status_code", "unknown")
        logger.exception(
            "Groq category classification request failed status=%s errorType=%s",
            status_code,
            type(error).__name__,
        )
        raise


def _parse_response(response, response_model):
    try:
        if not response.choices:
            raise ValueError("Groq category response has no choices")

        content = response.choices[0].message.content
        if not content:
            raise ValueError("Groq category response has no content")

        return response_model.model_validate_json(content)
    except (json.JSONDecodeError, ValidationError, ValueError, AttributeError, IndexError) as error:
        logger.error(
            "Groq category classification response validation failed: %s: %s",
            type(error).__name__,
            error,
        )
        raise InvalidCategoryResponseError from error


def classify_category(request: CategoryClassificationRequest) -> CategoryClassificationResponse:
    transaction = json.dumps(
        {
            "merchantName": request.merchantName,
            "merchantSector": request.merchantSector or "",
            "amount": request.amount,
        },
        ensure_ascii=False,
    )

    response = _call_groq(
        messages=[
            {"role": "system", "content": build_single_system_prompt()},
            {"role": "user", "content": build_single_user_prompt(transaction)},
        ],
        max_completion_tokens=256,
    )
    return _parse_response(response, CategoryClassificationResponse)


def classify_category_batch(
    request: CategoryClassificationBatchRequest,
) -> CategoryClassificationBatchResponse:
    transactions = json.dumps(
        [
            {
                "merchantName": item.merchantName,
                "merchantSector": item.merchantSector or "",
                "amount": item.amount,
            }
            for item in request.items
        ],
        ensure_ascii=False,
    )

    response = _call_groq(
        messages=[
            {"role": "system", "content": build_batch_system_prompt()},
            {"role": "user", "content": build_batch_user_prompt(transactions)},
        ],
        max_completion_tokens=256 * len(request.items),
    )
    parsed = _parse_response(response, CategoryClassificationBatchResponse)

    if len(parsed.results) != len(request.items):
        error = ValueError("Groq category batch response has an invalid result count")
        logger.error("Groq category classification response validation failed: %s", error)
        raise InvalidCategoryResponseError from error

    return parsed
