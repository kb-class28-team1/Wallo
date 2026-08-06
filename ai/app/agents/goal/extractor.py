import logging
from datetime import date

from groq import BadRequestError, Groq
from pydantic import ValidationError

from app.agents.goal.models import GoalDraft, GoalExtraction
from app.agents.goal.prompts import (
    EXTRACTION_SYSTEM_PROMPT,
    build_extraction_user_prompt,
)
from app.core.config import get_groq_model

logger = logging.getLogger("wallo_ai")


class GoalExtractionError(ValueError):
    """목표 정보 추출 응답이 계약을 만족하지 않을 때 발생한다."""


class GoalExtractor:
    MAX_JSON_ATTEMPTS = 2

    def __init__(self, client: Groq, model: str | None = None):
        self.client = client
        self.model = model or get_groq_model()

    def extract(
        self,
        user_message: str,
        draft: GoalDraft,
        reference_date: date,
    ) -> GoalExtraction:
        completion = None
        for attempt in range(1, self.MAX_JSON_ATTEMPTS + 1):
            try:
                completion = self.client.chat.completions.create(
                    model=self.model,
                    messages=[
                        {"role": "system", "content": EXTRACTION_SYSTEM_PROMPT},
                        {
                            "role": "user",
                            "content": build_extraction_user_prompt(
                                user_message,
                                draft,
                                reference_date,
                            ),
                        },
                    ],
                    response_format={"type": "json_object"},
                    temperature=0,
                    max_completion_tokens=500,
                )
                break
            except BadRequestError as error:
                if not self._is_json_validation_error(error):
                    raise
                logger.warning(
                    "goal extraction JSON generation failed (attempt %s/%s)",
                    attempt,
                    self.MAX_JSON_ATTEMPTS,
                )
                if attempt == self.MAX_JSON_ATTEMPTS:
                    raise GoalExtractionError(
                        "목표 정보를 구조화하지 못했습니다."
                    ) from error

        if completion is None:
            raise GoalExtractionError("목표 정보를 구조화하지 못했습니다.")
        try:
            content = completion.choices[0].message.content
            if not content:
                raise ValueError("목표 정보 추출 응답이 비어 있습니다.")
            return GoalExtraction.model_validate_json(content)
        except (AttributeError, IndexError, TypeError, ValueError, ValidationError) as error:
            logger.warning("goal extraction response validation failed: %s", error)
            raise GoalExtractionError("목표 정보를 구조화하지 못했습니다.") from error

    def _is_json_validation_error(self, error: BadRequestError) -> bool:
        body = error.body
        if isinstance(body, dict):
            detail = body.get("error")
            if isinstance(detail, dict):
                return detail.get("code") == "json_validate_failed"
        return "json_validate_failed" in str(error)
