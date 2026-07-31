"""POST /api/reports/generate: news_report(AI 가공 결과) 생성.

요청/응답 모델과 검증, mock 모드, 실제 OpenAI structured output 호출을 모두 담당한다.
OpenAI 클라이언트는 generate_financial_report()에 파라미터로 주입되므로, 테스트에서는
실제 SDK 대신 client.responses.parse(...)만 흉내내는 Fake 객체로 대체해 네트워크 호출 없이
검증할 수 있다.
"""

import logging
import os

from fastapi import APIRouter, HTTPException
from openai import APIConnectionError, APIStatusError, OpenAIError
from pydantic import BaseModel, ValidationError, ValidationInfo, field_validator

from app.openai_client import create_openai_client, get_openai_api_key, get_report_model
from app.prompt import FINANCIAL_REPORT_INSTRUCTIONS, build_report_input

logger = logging.getLogger("wallo_ai")

SUMMARY_MAX_LENGTH = 800
OTHER_FIELD_MAX_LENGTH = 1200

router = APIRouter(prefix="/api")


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

    @field_validator("summary", "cause", "socialImpact", "userImpact", "responseStrategy")
    @classmethod
    def validate_field(cls, value: str, info: ValidationInfo) -> str:
        if value is None or not value.strip():
            raise ValueError(f"{info.field_name} 값이 비어 있습니다.")

        stripped = value.strip()
        max_length = SUMMARY_MAX_LENGTH if info.field_name == "summary" else OTHER_FIELD_MAX_LENGTH
        if len(stripped) > max_length:
            raise ValueError(f"{info.field_name} 길이가 {max_length}자를 초과했습니다.")
        if stripped.startswith("```"):
            raise ValueError(f"{info.field_name}에 마크다운 코드 블록을 포함할 수 없습니다.")
        if stripped.startswith("{") and stripped.endswith("}"):
            raise ValueError(f"{info.field_name}에 JSON이 다시 감싸져 있습니다.")

        return stripped


def is_mock_enabled() -> bool:
    return os.getenv("AI_REPORT_MOCK_ENABLED", "false").strip().lower() == "true"


def build_mock_response(request: NewsReportGenerateRequest) -> NewsReportGenerateResponse:
    """개발용 mock 응답. AI_REPORT_MOCK_ENABLED=true일 때만 사용된다."""
    return NewsReportGenerateResponse(
        summary=f"[MOCK] {request.title} 핵심 요약입니다.",
        cause="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        socialImpact="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        userImpact="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        responseStrategy="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
    )


def generate_financial_report(client, request: NewsReportGenerateRequest, model: str) -> NewsReportGenerateResponse:
    """OpenAI Responses API의 구조화 출력(parse)으로 리포트를 생성하고 Pydantic으로 검증한다.

    client는 openai.OpenAI 인스턴스를 기대하지만 client.responses.parse(...)만 호출하므로,
    테스트에서는 같은 인터페이스를 가진 Fake 객체로 대체할 수 있다.
    """
    report_input = build_report_input(
        title=request.title,
        category=request.category,
        source=request.source,
        published_at=request.publishedAt,
        content=request.content,
    )

    try:
        response = client.responses.parse(
            model=model,
            instructions=FINANCIAL_REPORT_INSTRUCTIONS,
            input=report_input,
            text_format=NewsReportGenerateResponse,
        )
    except APIConnectionError as error:
        logger.error(
            "AI 서버 연결 실패 - newsId: %s, 단계: request, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=502, detail="AI 서버에 연결할 수 없습니다.") from error
    except APIStatusError as error:
        logger.error(
            "AI 서버 오류 응답 - newsId: %s, 단계: request, 예외: %s, status: %s",
            request.newsId, type(error).__name__, error.status_code,
        )
        raise HTTPException(status_code=502, detail="AI 서버가 오류를 반환했습니다.") from error
    except ValidationError as error:
        logger.error(
            "AI 응답 검증 실패 - newsId: %s, 단계: parse, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=502, detail="AI 응답 형식이 올바르지 않습니다.") from error
    except OpenAIError as error:
        logger.error(
            "AI 서버 호출 실패 - newsId: %s, 단계: request, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=502, detail="AI 서버 호출에 실패했습니다.") from error

    parsed = response.output_parsed
    if parsed is None:
        logger.warning(
            "AI 응답을 파싱하지 못했습니다(거절 또는 형식 불일치) - newsId: %s", request.newsId,
        )
        raise HTTPException(status_code=502, detail="AI 응답을 생성하지 못했습니다.")

    return parsed


@router.post("/reports/generate", response_model=NewsReportGenerateResponse)
def generate_report(request: NewsReportGenerateRequest) -> NewsReportGenerateResponse:
    """실제 OpenAI(Responses API 구조화 출력)로 금융 리포트를 생성한다.

    AI_REPORT_MOCK_ENABLED=true일 때만 개발용 더미 응답([MOCK] 표시)을 반환하고,
    그 외에는 항상 실제 OpenAI를 호출한다. 실패 시 mock으로 자동 대체하지 않는다.
    """
    if is_mock_enabled():
        return build_mock_response(request)

    api_key = get_openai_api_key()
    if not api_key:
        logger.error("OPENAI_API_KEY가 설정되지 않았습니다 - newsId: %s", request.newsId)
        raise HTTPException(status_code=503, detail="AI 서버 설정이 완료되지 않았습니다.")

    client = create_openai_client(api_key)

    try:
        return generate_financial_report(client, request, get_report_model())
    except HTTPException:
        raise
    except Exception as error:
        logger.error(
            "리포트 생성 중 예상하지 못한 오류 - newsId: %s, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=500, detail="리포트 생성 중 오류가 발생했습니다.") from error
