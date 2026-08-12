import logging
import os

from groq import Groq, GroqError
from pydantic import ValidationError

from app.clients.groq_client import create_groq_client
from app.core.config import get_groq_model

from .prompts import MISSION_GENERATION_INSTRUCTIONS, build_mission_input
from .schemas import (
    GeneratedMission,
    MissionBatchResponse,
    MissionGenerateRequest,
    MissionGenerateResponse,
)


logger = logging.getLogger("uvicorn.error")
DEFAULT_MISSION_MAX_COMPLETION_TOKENS = 3000
MISSION_ITEM_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "title": {"type": "string"},
        "description": {"type": "string"},
        "category": {"type": "string"},
        "rewardPoint": {"type": "integer", "const": 10},
        "verificationType": {
            "type": "string",
            "enum": ["MEDIA_AI", "TRANSACTION", "HYBRID", "SELF_CHECK", "MANUAL"],
        },
        "verificationRule": {
            "anyOf": [
                {
                    "type": "object",
                    "additionalProperties": False,
                    "properties": {"description": {"type": "string"}},
                    "required": ["description"],
                },
                {"type": "null"},
            ],
        },
        "evidenceGuide": {"anyOf": [{"type": "string"}, {"type": "null"}]},
    },
    "required": [
        "title", "description", "category", "rewardPoint",
        "verificationType", "verificationRule", "evidenceGuide",
    ],
}


def mission_response_schema(requested_count: int) -> dict:
    max_items = min(12, requested_count + 2)
    return {
    "name": "mission_generation",
    "strict": True,
    "schema": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "missions": {
                "type": "array",
                "minItems": 1,
                "maxItems": max_items,
                "items": MISSION_ITEM_SCHEMA,
            },
            "promptVersion": {"type": "string"},
        },
        "required": ["missions", "promptVersion"],
    },
    }


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
        generated: list[GeneratedMission] = []
        title_keys: set[str] = set()
        for batch_number in (1, 2, 3, 4):
            requested_count = min(10, 20 - len(generated))
            if requested_count <= 0:
                break
            options = {
                "response_format": {
                    "type": "json_schema",
                    "json_schema": mission_response_schema(requested_count),
                },
                "max_completion_tokens": get_mission_max_completion_tokens(),
            }
            if model.startswith("openai/gpt-oss-"):
                options["reasoning_effort"] = "low"
            response = client.chat.completions.create(
                model=model,
                messages=[
                    {"role": "system", "content": MISSION_GENERATION_INSTRUCTIONS},
                    {"role": "user", "content": build_mission_input(
                        request,
                        batch_number,
                        [mission.title for mission in generated],
                        requested_count,
                    )},
                ],
                **options,
            )
            if not response.choices or not response.choices[0].message.content:
                raise ValueError("mission response is empty")
            batch = MissionBatchResponse.model_validate_json(
                response.choices[0].message.content
            )
            for mission in batch.missions:
                key = "".join(mission.title.lower().split())
                if key not in title_keys:
                    title_keys.add(key)
                    generated.append(mission)
                if len(generated) == 20:
                    break
        if len(generated) != 20:
            raise ValueError("mission batches must produce exactly 20 unique missions")
        return MissionGenerateResponse(
            missions=generated,
            promptVersion="personalized-mission-v1",
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
