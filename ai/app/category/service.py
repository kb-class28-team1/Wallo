from app.agents.category.agent import (
    CategoryAgent,
    InvalidCategoryResponseError,
)
from app.clients.groq_client import create_groq_client
from app.core.config import get_groq_model

from .schemas import (
    CategoryClassificationBatchRequest,
    CategoryClassificationBatchResponse,
    CategoryClassificationRequest,
    CategoryClassificationResponse,
)


def classify_category(request: CategoryClassificationRequest) -> CategoryClassificationResponse:
    return CategoryAgent(
        create_groq_client(),
        model=get_groq_model(),
    ).classify(request)


def classify_category_batch(
    request: CategoryClassificationBatchRequest,
) -> CategoryClassificationBatchResponse:
    return CategoryAgent(
        create_groq_client(),
        model=get_groq_model(),
    ).classify_batch(request)
