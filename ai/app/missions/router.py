import logging

from fastapi import APIRouter, HTTPException
from groq import BadRequestError, GroqError, RateLimitError

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
    except RateLimitError as error:
        retry_after = error.response.headers.get("retry-after")
        headers = {"Retry-After": retry_after} if retry_after else None
        logger.warning("Groq mission rate limit exceeded; retry_after=%s", retry_after)
        raise HTTPException(
            status_code=429,
            detail="Mission AI rate limit exceeded. Please retry later.",
            headers=headers,
        ) from error
    except BadRequestError as error:
        logger.error("Groq rejected the mission response schema: %s", error)
        raise HTTPException(
            status_code=502,
            detail="Mission AI could not produce 20 valid missions.",
        ) from error
    except GroqError as error:
        raise HTTPException(status_code=502, detail="Mission AI request failed") from error
    except InvalidMissionResponseError as error:
        logger.error("invalid mission generation response: %s", error)
        raise HTTPException(status_code=502, detail="Mission AI response is invalid") from error
