import logging

from fastapi import APIRouter, HTTPException
from groq import GroqError

from .schemas import MissionGenerateRequest, MissionGenerateResponse
from .service import InvalidMissionResponseError, generate_missions_with_config


logger = logging.getLogger("uvicorn.error")
router = APIRouter(prefix="/api/missions", tags=["missions"])


@router.post("/generate", response_model=MissionGenerateResponse)
def generate_personalized_missions(
    request: MissionGenerateRequest,
) -> MissionGenerateResponse:
    try:
        return generate_missions_with_config(request)
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail="AI configuration is unavailable") from error
    except GroqError as error:
        raise HTTPException(status_code=502, detail="Mission AI request failed") from error
    except InvalidMissionResponseError as error:
        logger.error("invalid mission generation response: %s", error)
        raise HTTPException(status_code=502, detail="Mission AI response is invalid") from error

