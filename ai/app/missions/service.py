import logging
import os

from groq import Groq, GroqError
from pydantic import ValidationError

from app.clients.groq_client import create_groq_client
from app.core.config import get_groq_model

from .prompts import MISSION_GENERATION_INSTRUCTIONS, build_mission_input
from .schemas import MissionGenerateRequest, MissionGenerateResponse


logger = logging.getLogger("uvicorn.error")
DEFAULT_MISSION_MAX_COMPLETION_TOKENS = 4500


def get_mission_max_completion_tokens() -> int:
    raw_value = os.getenv(
        "MISSION_MAX_COMPLETION_TOKENS",
        str(DEFAULT_MISSION_MAX_COMPLETION_TOKENS),
    )
    try:
        value = int(raw_value)
    except ValueError:
        logger.warning(
            "invalid MISSION_MAX_COMPLETION_TOKENS=%s; using %s",
            raw_value,
            DEFAULT_MISSION_MAX_COMPLETION_TOKENS,
        )
        return DEFAULT_MISSION_MAX_COMPLETION_TOKENS
    return min(max(value, 1000), 6000)


class InvalidMissionResponseError(ValueError):
    pass


def generate_missions(
    client: Groq, request: MissionGenerateRequest, model: str
) -> MissionGenerateResponse:
    try:
        options = {
            "response_format": {"type": "json_object"},
            "max_completion_tokens": get_mission_max_completion_tokens(),
        }
        if model.startswith("openai/gpt-oss-"):
            options["reasoning_effort"] = "low"
        response = client.chat.completions.create(
            model=model,
            messages=[
                {"role": "system", "content": MISSION_GENERATION_INSTRUCTIONS},
                {"role": "user", "content": build_mission_input(request)},
            ],
            **options,
        )
        if not response.choices or not response.choices[0].message.content:
            raise ValueError("mission response is empty")
        return MissionGenerateResponse.model_validate_json(
            response.choices[0].message.content
        )
    except GroqError as error:
        logger.error("Groq mission generation failed: %s", error)
        raise
    except (ValidationError, ValueError, AttributeError, IndexError) as error:
        logger.error("mission response validation failed: %s", error)
        raise InvalidMissionResponseError from error


def generate_missions_with_config(
    request: MissionGenerateRequest,
) -> MissionGenerateResponse:
    return generate_missions(create_groq_client(), request, get_groq_model())
