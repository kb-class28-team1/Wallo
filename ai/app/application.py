import logging
import math

from fastapi import FastAPI, Request
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.chat.router import router as chat_router
from app.category.router import router as category_router
from app.asset_reports.router import router as asset_reports_router
from app.core.ai_guard import ApplicationGuardError
from app.health.router import router as health_router
from app.reports.router import router as financial_report_router
from app.missions.router import router as mission_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s:%(name)s:%(message)s",
)
logger = logging.getLogger("wallo_ai")


def _retry_after_header_seconds(error: ApplicationGuardError) -> str:
    try:
        retry_after_seconds = float(getattr(error, "retry_after_seconds", 1.0))
        if not math.isfinite(retry_after_seconds):
            return "1"
        return str(max(1, math.ceil(retry_after_seconds)))
    except (TypeError, ValueError, OverflowError):
        return "1"


def create_app() -> FastAPI:
    app = FastAPI(title="Wallo AI Server")
    app.include_router(health_router)
    app.include_router(chat_router)
    app.include_router(category_router)
    app.include_router(asset_reports_router)
    app.include_router(financial_report_router)
    app.include_router(mission_router)

    @app.exception_handler(ApplicationGuardError)
    async def application_guard_exception_handler(
        request: Request,
        exc: ApplicationGuardError,
    ):
        retry_after = _retry_after_header_seconds(exc)
        error_code = getattr(exc, "error_code", "AI_GUARD_REJECTED")
        logger.warning(
            "[AI_GUARD] status=rejected httpStatus=429 path=%s "
            "reason=%s retryAfter=%s",
            request.url.path,
            type(exc).__name__,
            retry_after,
        )
        return JSONResponse(
            status_code=429,
            headers={"Retry-After": retry_after},
            content={
                "detail": str(exc),
                "errorCode": error_code,
                "retryable": True,
            },
        )

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError):
        logger.warning("요청 검증 실패 - path: %s, errors: %s", request.url.path, exc.errors())
        return JSONResponse(
            status_code=422,
            content={"detail": jsonable_encoder(exc.errors())},
        )

    return app


app = create_app()
