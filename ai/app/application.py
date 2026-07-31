import logging
import os

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from openai import OpenAI, OpenAIError
from pydantic import BaseModel, Field

logger = logging.getLogger("wallo_ai")
logging.basicConfig(level=logging.INFO)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)


class ChatResponse(BaseModel):
    answer: str


class NewsReportGenerateRequest(BaseModel):
    newsId: int
    title: str
    content: str
    category: str
    source: str
    publishedAt: str


class NewsReportGenerateResponse(BaseModel):
    summary: str
    cause: str
    socialImpact: str
    userImpact: str
    responseStrategy: str


app = FastAPI(title="Wallo AI Server")


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """422 발생 시 어떤 필드가 왜 실패했는지 서버 콘솔에 남긴다 (요청 본문 전체는 남기지 않는다)."""
    logger.warning(
        "요청 검증 실패 - path: %s, errors: %s",
        request.url.path,
        exc.errors(),
    )
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


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


@app.post("/api/reports/generate", response_model=NewsReportGenerateResponse)
def generate_report(request: NewsReportGenerateRequest) -> NewsReportGenerateResponse:
    """개발용 mock 엔드포인트.

    아직 이 엔드포인트는 실제 LLM과 연결되어 있지 않다. 지금은 Java 쪽의 저장 흐름
    (news_report INSERT, 중복 방지 등)을 검증할 수 있도록 고정된 더미 응답만 반환한다.
    실제 LLM 연동은 /api/chat과 동일한 패턴(OpenAI client + 프롬프트)으로 이후 별도
    작업에서 붙이면 된다 — 금융 리포트용 프롬프트 설계는 이번 작업 범위가 아니다.
    """
    return NewsReportGenerateResponse(
        summary=f"[MOCK] {request.title} 핵심 요약입니다.",
        cause="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        socialImpact="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        userImpact="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        responseStrategy="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
    )
