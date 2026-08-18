import json
import logging

from groq import Groq, GroqError
from pydantic import BaseModel, ValidationError

from app.category.prompts import (
    build_batch_system_prompt,
    build_batch_user_prompt,
    build_single_system_prompt,
    build_single_user_prompt,
)
from app.category.schemas import (
    CategoryClassificationBatchRequest,
    CategoryClassificationBatchResponse,
    CategoryClassificationRequest,
    CategoryClassificationResponse,
)
from app.core.config import get_groq_model
from app.core.ai_timing import timed_groq_completion


logger = logging.getLogger("uvicorn.error")


class InvalidCategoryResponseError(ValueError):
    """Raised when Groq returns a response outside the category contract."""


class CategoryAgent:
    """거래 카테고리 분류를 한 번의 LLM 호출로 처리하는 전문 Agent."""

    SINGLE_MAX_COMPLETION_TOKENS = 256
    BATCH_MAX_COMPLETION_TOKENS_PER_ITEM = 128

    def __init__(self, client: Groq, model: str | None = None):
        self.client = client
        self.model = model or get_groq_model()

    def classify(
        self,
        request: CategoryClassificationRequest,
    ) -> CategoryClassificationResponse:
        transaction = json.dumps(
            self._transaction_payload(request),
            ensure_ascii=False,
        )
        with timed_groq_completion(
            self.client,
            operation="category.classify",
            model=self.model,
            requested_completion_tokens=self.SINGLE_MAX_COMPLETION_TOKENS,
        ) as timing:
            completion = self._call_groq(
                timing,
                messages=[
                    {"role": "system", "content": build_single_system_prompt()},
                    {"role": "user", "content": build_single_user_prompt(transaction)},
                ],
                max_completion_tokens=self.SINGLE_MAX_COMPLETION_TOKENS,
            )
            return self._parse_response(completion, CategoryClassificationResponse)

    def classify_batch(
        self,
        request: CategoryClassificationBatchRequest,
    ) -> CategoryClassificationBatchResponse:
        requested_completion_tokens = (
            self.BATCH_MAX_COMPLETION_TOKENS_PER_ITEM * len(request.items)
        )
        transactions = json.dumps(
            [self._transaction_payload(item) for item in request.items],
            ensure_ascii=False,
        )
        with timed_groq_completion(
            self.client,
            operation="category.classify_batch",
            model=self.model,
            requested_completion_tokens=requested_completion_tokens,
        ) as timing:
            completion = self._call_groq(
                timing,
                messages=[
                    {"role": "system", "content": build_batch_system_prompt()},
                    {"role": "user", "content": build_batch_user_prompt(transactions)},
                ],
                max_completion_tokens=requested_completion_tokens,
            )
            parsed = self._parse_response(
                completion,
                CategoryClassificationBatchResponse,
                wrap_batch_array=True,
            )

            if len(parsed.results) != len(request.items):
                error = ValueError("Groq category batch response has an invalid result count")
                logger.error("Groq category classification response validation failed: %s", error)
                raise InvalidCategoryResponseError from error

            return parsed

    def _call_groq(
        self,
        timing,
        messages: list[dict[str, str]],
        max_completion_tokens: int,
    ):
        try:
            return timing.create(
                messages=messages,
                response_format=self._build_response_format(),
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

    @staticmethod
    def _transaction_payload(
        request: CategoryClassificationRequest,
    ) -> dict[str, str | int]:
        return {
            "merchantName": request.merchantName,
            "merchantSector": request.merchantSector or "",
            "amount": request.amount,
        }

    @staticmethod
    def _build_response_format() -> dict[str, str]:
        # json_object mode is supported by every Groq chat model used here.
        # Pydantic validation still enforces the category response contract.
        return {"type": "json_object"}

    @staticmethod
    def _parse_response(
        response,
        response_model: type[BaseModel],
        *,
        wrap_batch_array: bool = False,
    ):
        try:
            if not response.choices:
                raise ValueError("Groq category response has no choices")

            content = response.choices[0].message.content
            if not content:
                raise ValueError("Groq category response has no content")

            payload = json.loads(content)
            if wrap_batch_array and isinstance(payload, list):
                payload = {"results": payload}
            return response_model.model_validate(payload)
        except (
            json.JSONDecodeError,
            ValidationError,
            ValueError,
            AttributeError,
            IndexError,
        ) as error:
            logger.error(
                "Groq category classification response validation failed: %s: %s",
                type(error).__name__,
                error,
            )
            raise InvalidCategoryResponseError from error
