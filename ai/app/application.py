import os

from fastapi import FastAPI, HTTPException
from openai import OpenAI, OpenAIError
from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)


class ChatResponse(BaseModel):
    answer: str


app = FastAPI(title="Wallo AI Server")


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    try:
        client = OpenAI()
        response = client.responses.create(
            model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
            instructions=(
                "당신은 친절한 금융 AI 어시스턴트입니다. "
                "항상 자연스러운 한국어로 답변하세요."
            ),
            input=request.message,
        )
        return ChatResponse(answer=response.output_text)
    except OpenAIError as error:
        raise HTTPException(
            status_code=502,
            detail="AI 응답을 생성하지 못했습니다.",
        ) from error
