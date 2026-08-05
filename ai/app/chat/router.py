import logging

from fastapi import APIRouter, HTTPException
from groq import GroqError

from app.chat.schemas import (
    ChatRequest,
    ChatResponse,
    SummarizeConversationRequest,
    SummarizeConversationResponse,
)
from app.chat.service import ChatService
from app.chat.summary_service import summarize_conversation
from app.clients.groq_client import create_groq_client

logger = logging.getLogger("wallo_ai")
router = APIRouter(prefix="/api/chat", tags=["chat"])


@router.post("", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    try:
        return ChatService(create_groq_client()).chat(request)
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except GroqError as error:
        logger.exception("Groq API request failed")
        raise HTTPException(status_code=502, detail="Groq AI 응답을 생성하지 못했습니다.") from error


@router.post("/summarize", response_model=SummarizeConversationResponse)
def summarize(request: SummarizeConversationRequest) -> SummarizeConversationResponse:
    try:
        summary = summarize_conversation(create_groq_client(), request)
        return SummarizeConversationResponse(summary=summary)
    except RuntimeError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
    except GroqError as error:
        logger.exception("Groq conversation summarization failed")
        raise HTTPException(status_code=502, detail="대화 요약을 생성하지 못했습니다.") from error
