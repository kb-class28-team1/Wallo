import logging
import os

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from openai import OpenAI, OpenAIError
from pydantic import BaseModel, Field

from app.financial_report import (
    NewsReportGenerateRequest,
    NewsReportGenerateResponse,
    build_mock_response,
    generate_financial_report,
    is_mock_enabled,
    resolve_report_model,
)

logger = logging.getLogger("wallo_ai")
logging.basicConfig(level=logging.INFO)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)


class ChatResponse(BaseModel):
    answer: str


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
    """실제 OpenAI(Responses API 구조화 출력)로 금융 리포트를 생성한다.

    AI_REPORT_MOCK_ENABLED=true일 때만 개발용 더미 응답([MOCK] 표시)을 반환하고,
    그 외에는 항상 실제 OpenAI를 호출한다. 실패 시 mock으로 자동 대체하지 않는다.
    """
    if is_mock_enabled():
        return build_mock_response(request)

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        logger.error("OPENAI_API_KEY가 설정되지 않았습니다 - newsId: %s", request.newsId)
        raise HTTPException(status_code=503, detail="AI 서버 설정이 완료되지 않았습니다.")

    client = OpenAI(api_key=api_key)

    try:
        return generate_financial_report(client, request, resolve_report_model())
    except HTTPException:
        raise
    except Exception as error:
        logger.error(
            "리포트 생성 중 예상하지 못한 오류 - newsId: %s, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=500, detail="리포트 생성 중 오류가 발생했습니다.") from error
