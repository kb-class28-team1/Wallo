import logging

from fastapi import APIRouter, HTTPException
from groq import GroqError

from .schemas import (
    CategoryClassificationBatchRequest,
    CategoryClassificationBatchResponse,
    CategoryClassificationRequest,
    CategoryClassificationResponse,
)
from .service import (
    InvalidCategoryResponseError,
    classify_category as classify_category_service,
    classify_category_batch as classify_category_batch_service,
)


logger = logging.getLogger("uvicorn.error")
router = APIRouter(prefix="/api/category", tags=["category"])


def _handle_category_error(error: Exception, detail: str) -> None:
    if isinstance(error, RuntimeError):
        raise HTTPException(
            status_code=503,
            detail="GROQ_API_KEY가 설정되지 않았습니다.",
        ) from error

    if isinstance(error, GroqError):
        status_code = getattr(error, "status_code", "unknown")
        raise HTTPException(
            status_code=502,
            detail=f"Groq AI category classification failed (status={status_code})",
        ) from error

    if isinstance(error, InvalidCategoryResponseError):
        raise HTTPException(status_code=502, detail=detail) from error

    raise error


@router.post("/classify", response_model=CategoryClassificationResponse)
def classify_category(
    request: CategoryClassificationRequest,
) -> CategoryClassificationResponse:
    try:
        return classify_category_service(request)
    except (RuntimeError, GroqError, InvalidCategoryResponseError) as error:
        logger.error("Category classification request failed: %s", error)
        _handle_category_error(
            error,
            "Groq AI category classification returned an invalid response",
        )


@router.post("/classify/batch", response_model=CategoryClassificationBatchResponse)
def classify_category_batch(
    request: CategoryClassificationBatchRequest,
) -> CategoryClassificationBatchResponse:
    try:
        return classify_category_batch_service(request)
    except (RuntimeError, GroqError, InvalidCategoryResponseError) as error:
        logger.error("Category batch classification request failed: %s", error)
        _handle_category_error(
            error,
            "Groq AI category classification returned an invalid response",
        )
