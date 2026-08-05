"""POST /api/chat: 일반 채팅 기능."""

from fastapi import APIRouter, HTTPException
from openai import OpenAIError
from pydantic import BaseModel, Field

from app.openai_client import create_openai_client, get_chat_model
from app.prompt import CHAT_INSTRUCTIONS

router = APIRouter(prefix="/api")


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)


class ChatResponse(BaseModel):
    answer: str


def call_chat_completion(client, request: ChatRequest, model: str) -> ChatResponse:
    """실제 OpenAI 호출부. client는 client.responses.create(...)만 호출하므로,
    테스트에서는 같은 인터페이스를 가진 Fake 객체로 대체할 수 있다."""
    try:
        response = client.responses.create(
            model=model,
            instructions=CHAT_INSTRUCTIONS,
            input=request.message,
        )
        return ChatResponse(answer=response.output_text)
    except OpenAIError as error:
        raise HTTPException(
            status_code=502,
            detail="AI 응답을 생성하지 못했습니다.",
        ) from error


@router.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    # create_openai_client()도 (키가 없으면) OpenAIError를 던질 수 있어 함께 감싼다 —
    # call_chat_completion() 내부는 이미 자체적으로 OpenAIError를 처리하므로 여기서 이중으로 잡히지 않는다.
    try:
        client = create_openai_client()
    except OpenAIError as error:
        raise HTTPException(
            status_code=502,
            detail="AI 응답을 생성하지 못했습니다.",
        ) from error

    return call_chat_completion(client, request, get_chat_model())
