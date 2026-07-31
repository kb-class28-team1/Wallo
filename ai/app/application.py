"""FastAPI 앱 생성과 router 등록만 담당한다.

AI 프롬프트, OpenAI 호출, 비즈니스 로직은 각 기능 모듈(chat.py, financial_report.py)에 있다.
"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.chat import router as chat_router
from app.financial_report import router as financial_report_router

logger = logging.getLogger("wallo_ai")
logging.basicConfig(level=logging.INFO)

app = FastAPI(title="Wallo AI Server")

app.include_router(chat_router)
app.include_router(financial_report_router)


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
