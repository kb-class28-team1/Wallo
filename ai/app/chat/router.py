import logging

from fastapi import APIRouter, Header, HTTPException
from groq import GroqError, RateLimitError

from app.chat.schemas import (
    ChatRequest,
    ChatResponse,
    SummarizeConversationRequest,
    SummarizeConversationResponse,
)
from app.chat.service import ChatService
from app.chat.summary_service import summarize_conversation
from app.clients.groq_client import create_groq_client
from app.core.ai_timing import request_id_context

logger = logging.getLogger("wallo_ai")
router = APIRouter(prefix="/api/chat", tags=["chat"])

RATE_LIMIT_MESSAGE = (
    "현재 AI 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."
)


def raise_rate_limit(error: RateLimitError, operation: str) -> None:
    logger.warning("Groq rate limit reached while %s", operation)
    retry_after = error.response.headers.get("retry-after")
    headers = {"Retry-After": retry_after} if retry_after else None
    raise HTTPException(
        status_code=429,
        detail=RATE_LIMIT_MESSAGE,
        headers=headers,
    ) from error


@router.post("", response_model=ChatResponse)
def chat(
    request: ChatRequest,
    x_request_id: str | None = Header(default=None, alias="X-Request-Id"),
) -> ChatResponse:
    with request_id_context(x_request_id):
        try:
            return ChatService(create_groq_client()).chat(request)
        except RuntimeError as error:
            raise HTTPException(status_code=503, detail=str(error)) from error
        except RateLimitError as error:
            raise_rate_limit(error, "generating chat response")
        except GroqError as error:
            logger.exception("Groq API request failed")
            raise HTTPException(status_code=502, detail="Groq AI 응답을 생성하지 못했습니다.") from error


@router.post("/summarize", response_model=SummarizeConversationResponse)
def summarize(
    request: SummarizeConversationRequest,
    x_request_id: str | None = Header(default=None, alias="X-Request-Id"),
) -> SummarizeConversationResponse:
    with request_id_context(x_request_id):
        try:
            summary = summarize_conversation(create_groq_client(), request)
            return SummarizeConversationResponse(summary=summary)
        except RuntimeError as error:
            raise HTTPException(status_code=503, detail=str(error)) from error
        except RateLimitError as error:
            raise_rate_limit(error, "summarizing conversation")
        except GroqError as error:
            logger.exception("Groq conversation summarization failed")
            raise HTTPException(status_code=502, detail="대화 요약을 생성하지 못했습니다.") from error
