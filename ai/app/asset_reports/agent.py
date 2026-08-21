import json
import logging

from groq import Groq, GroqError
from pydantic import ValidationError

from app.asset_reports.prompts import (
    CONSUMPTION_INSIGHT_INSTRUCTIONS,
    build_consumption_insight_input,
)
from app.asset_reports.schemas import (
    ConsumptionInsightGenerateRequest,
    ConsumptionInsightGenerateResponse,
)
from app.core.ai_timing import timed_groq_completion
from app.core.config import get_groq_model


logger = logging.getLogger("uvicorn.error")
CONSUMPTION_INSIGHT_MAX_COMPLETION_TOKENS = 2000


class InvalidConsumptionInsightResponseError(ValueError):
    """Raised when Groq returns a response outside the insight contract."""


class ConsumptionInsightAgent:
    """집계 소비 데이터를 한 번의 LLM 호출로 문구화하는 전문 Agent."""

    def __init__(self, client: Groq, model: str | None = None):
        self.client = client
        self.model = model or get_groq_model()

    def generate(
        self,
        request: ConsumptionInsightGenerateRequest,
    ) -> ConsumptionInsightGenerateResponse:
        response = self._call_groq(request)
        return self._parse_response(response)

    def _call_groq(self, request: ConsumptionInsightGenerateRequest):
        options = self._build_completion_options()
        with timed_groq_completion(
            self.client,
            operation="consumption.insight",
            model=self.model,
            requested_completion_tokens=options["max_completion_tokens"],
        ) as timing:
            try:
                return timing.create(
                    messages=[
                        {
                            "role": "system",
                            "content": CONSUMPTION_INSIGHT_INSTRUCTIONS,
                        },
                        {
                            "role": "user",
                            "content": build_consumption_insight_input(request),
                        },
                    ],
                    **options,
                )
            except GroqError as error:
                status_code = getattr(error, "status_code", "unknown")
                logger.error(
                    "Groq consumption insight request failed status=%s errorType=%s",
                    status_code,
                    type(error).__name__,
                )
                raise

    def _build_completion_options(self) -> dict:
        options = {
            "response_format": self._build_response_format(),
            "max_completion_tokens": CONSUMPTION_INSIGHT_MAX_COMPLETION_TOKENS,
        }
        if self.model.startswith("openai/gpt-oss-"):
            options["reasoning_effort"] = "low"
        return options

    @staticmethod
    def _build_response_format() -> dict[str, str]:
        # gpt-oss-20b에서 json_schema 강제 응답은 Groq의 사전 JSON 검증 오류를
        # 발생시킬 수 있으므로, 모델 공통 지원 형식인 json_object를 사용한다.
        # 응답 필드와 길이는 Pydantic 검증으로 보장한다.
        return {"type": "json_object"}

    @staticmethod
    def _parse_response(response) -> ConsumptionInsightGenerateResponse:
        try:
            if not response.choices:
                raise ValueError("Groq consumption insight response has no choices")

            content = response.choices[0].message.content
            if not content:
                raise ValueError("Groq consumption insight response has no content")

            return ConsumptionInsightGenerateResponse.model_validate_json(content)
        except (
            json.JSONDecodeError,
            ValidationError,
            ValueError,
            AttributeError,
            IndexError,
        ) as error:
            logger.error(
                "Groq consumption insight response validation failed: %s: %s",
                type(error).__name__,
                error,
            )
            raise InvalidConsumptionInsightResponseError from error
