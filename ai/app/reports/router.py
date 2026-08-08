"""POST /api/reports/generate: news_report(AI 가공 결과) 생성."""

import json
import logging
import os

from fastapi import APIRouter, HTTPException
from groq import Groq, GroqError
from pydantic import BaseModel, ValidationError, ValidationInfo, field_validator

from app.reports.prompts import FINANCIAL_REPORT_INSTRUCTIONS, build_report_input

logger = logging.getLogger("wallo_ai")

SUMMARY_BULLET_MAX_LENGTH = 200
SUMMARY_MAX_BULLETS = 5
OTHER_FIELD_MAX_LENGTH = 1200

router = APIRouter(prefix="/api")

FINANCIAL_REPORT_JSON_SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {"type": "array", "items": {"type": "string"}},
        "eventDescription": {"type": "string"},
        "cause": {"type": "string"},
        "socialImpact": {"type": "string"},
        "userImpact": {"type": "string"},
        "responseStrategy": {"type": "string"},
    },
    "required": [
        "summary",
        "eventDescription",
        "cause",
        "socialImpact",
        "userImpact",
        "responseStrategy",
    ],
    "additionalProperties": False,
}


class NewsReportGenerateRequest(BaseModel):
    newsId: int
    title: str
    content: str
    category: str
    source: str
    publishedAt: str


class NewsReportGenerateResponse(BaseModel):
    # 화면 상단 "핵심 요약" bullet 목록(2~3개의 짧은 문장). 각 문장이 한 가지 사실만 담는다.
    summary: list[str]
    # "어떤 일이 있었나요?" 섹션에 보여줄, 실제 사건을 조금 더 구체적으로 설명하는 3~4문장 문단.
    eventDescription: str
    cause: str
    socialImpact: str
    userImpact: str
    responseStrategy: str

    @field_validator("eventDescription", "cause", "socialImpact", "userImpact", "responseStrategy")
    @classmethod
    def validate_text_field(cls, value: str, info: ValidationInfo) -> str:
        if value is None or not value.strip():
            raise ValueError(f"{info.field_name} 값이 비어 있습니다.")

        stripped = value.strip()
        if len(stripped) > OTHER_FIELD_MAX_LENGTH:
            raise ValueError(f"{info.field_name} 길이가 {OTHER_FIELD_MAX_LENGTH}자를 초과했습니다.")
        if stripped.startswith("```"):
            raise ValueError(f"{info.field_name}에 마크다운 코드 블록을 포함할 수 없습니다.")
        if stripped.startswith("{") and stripped.endswith("}"):
            raise ValueError(f"{info.field_name}에 JSON이 다시 감싸져 있습니다.")

        return stripped

    @field_validator("summary")
    @classmethod
    def validate_summary(cls, value: list[str]) -> list[str]:
        if not value:
            raise ValueError("summary 값이 비어 있습니다.")
        if len(value) > SUMMARY_MAX_BULLETS:
            raise ValueError(f"summary bullet 개수가 {SUMMARY_MAX_BULLETS}개를 초과했습니다.")

        cleaned: list[str] = []
        for item in value:
            if item is None or not item.strip():
                raise ValueError("summary bullet 값이 비어 있습니다.")

            stripped = item.strip()
            if len(stripped) > SUMMARY_BULLET_MAX_LENGTH:
                raise ValueError(f"summary bullet 길이가 {SUMMARY_BULLET_MAX_LENGTH}자를 초과했습니다.")
            if stripped.startswith("```") or (stripped.startswith("{") and stripped.endswith("}")):
                raise ValueError("summary bullet에 마크다운/JSON을 포함할 수 없습니다.")
            cleaned.append(stripped)

        return cleaned


def is_mock_enabled() -> bool:
    return os.getenv("AI_REPORT_MOCK_ENABLED", "false").strip().lower() == "true"


def build_mock_response(request: NewsReportGenerateRequest) -> NewsReportGenerateResponse:
    """개발용 mock 응답. AI_REPORT_MOCK_ENABLED=true일 때만 사용된다."""
    return NewsReportGenerateResponse(
        summary=[f"[MOCK] {request.title} 핵심 요약 1", "[MOCK] 핵심 요약 2"],
        eventDescription=f"[MOCK] {request.title} 관련 사건 설명입니다. 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        cause="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        socialImpact="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        userImpact="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
        responseStrategy="[MOCK] 아직 실제 LLM과 연결되지 않은 개발용 더미 응답입니다.",
    )


def get_groq_api_key() -> str | None:
    return os.getenv("GROQ_API_KEY")


def get_report_model() -> str:
    return os.getenv("GROQ_REPORT_MODEL") or os.getenv("GROQ_MODEL", "openai/gpt-oss-20b")


def generate_financial_report(client: Groq, request: NewsReportGenerateRequest, model: str) -> NewsReportGenerateResponse:
    """Groq Chat Completions의 JSON 응답을 Pydantic 모델로 검증해 리포트를 생성한다."""
    report_input = build_report_input(
        title=request.title,
        category=request.category,
        source=request.source,
        published_at=request.publishedAt,
        content=request.content,
    )

    reasoning_options = {}
    if model.startswith("openai/gpt-oss-"):
        # GPT-OSS JSON mode requires reasoning output to be hidden or parsed.
        reasoning_options["reasoning_format"] = "hidden"

    try:
        response = client.chat.completions.create(
            model=model,
            messages=[
                {
                    "role": "system",
                    "content": FINANCIAL_REPORT_INSTRUCTIONS,
                },
                {"role": "user", "content": report_input},
            ],
            response_format={
                "type": "json_schema",
                "json_schema": {
                    "name": "financial_report",
                    "strict": True,
                    "schema": FINANCIAL_REPORT_JSON_SCHEMA,
                },
            },
            max_completion_tokens=4000,
            **reasoning_options,
        )
    except GroqError as error:
        logger.error(
            "Groq request details - status: %s, message: %s, body: %s",
            getattr(error, "status_code", None),
            str(error),
            getattr(error, "body", None),
        )
        logger.error(
            "Groq 서버 호출 실패 - newsId: %s, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=502, detail="Groq AI 서버 호출에 실패했습니다.") from error

    content = response.choices[0].message.content if response.choices else None
    if not content:
        logger.error(
            "Groq 응답 내용이 비어 있습니다 - newsId: %s",
            request.newsId,
        )
        raise HTTPException(status_code=502, detail="AI 응답을 생성하지 못했습니다.")

    try:
        parsed = json.loads(content)
        return NewsReportGenerateResponse.model_validate(parsed)
    except (json.JSONDecodeError, ValidationError) as error:
        logger.error(
            "Groq 응답 검증 실패 - newsId: %s, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=502, detail="AI 응답 형식이 올바르지 않습니다.") from error

@router.post("/reports/generate", response_model=NewsReportGenerateResponse)
def generate_report(request: NewsReportGenerateRequest) -> NewsReportGenerateResponse:
    """실제 Groq JSON 응답으로 금융 리포트를 생성한다.

    AI_REPORT_MOCK_ENABLED=true일 때만 개발용 더미 응답([MOCK] 표시)을 반환하고,
    그 외에는 항상 실제 Groq를 호출한다. 실패 시 mock으로 자동 대체하지 않는다.
    """
    if is_mock_enabled():
        return build_mock_response(request)

    api_key = get_groq_api_key()
    if not api_key:
        logger.error("GROQ_API_KEY가 설정되지 않았습니다 - newsId: %s", request.newsId)
        raise HTTPException(status_code=503, detail="AI 서버 설정이 완료되지 않았습니다.")

    client = Groq(api_key=api_key)

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
