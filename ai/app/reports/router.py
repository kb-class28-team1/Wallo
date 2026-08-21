"""공통 금융 리포트와 사용자별 맞춤 분석 생성 API."""
import json
import logging
import os
from typing import Any, Literal

from fastapi import APIRouter, HTTPException
from groq import GroqError
from pydantic import BaseModel, ValidationError, field_validator

from app.clients.groq_client import Groq
from app.core.ai_guard import ApplicationGuardError
from app.core.ai_timing import current_request_id, timed_groq_completion
from app.reports.prompts import COMMON_REPORT_INSTRUCTIONS, PERSONALIZATION_INSTRUCTIONS, build_common_report_input, build_personalization_input

logger = logging.getLogger("wallo_ai")
router = APIRouter(prefix="/api")
REPORT_MAX_COMPLETION_TOKENS = 1500
JSON_VALIDATION_MAX_RETRIES = 1


class NewsReportGenerateRequest(BaseModel):
    newsId: int
    title: str
    content: str
    category: str
    source: str
    publishedAt: str
    generationType: Literal["COMMON", "PERSONALIZED"] = "COMMON"
    userProfile: dict[str, Any] | None = None


class NewsReportGenerateResponse(BaseModel):
    summary: list[str] | None = None
    eventDescription: str | None = None
    cause: str | None = None
    socialImpact: str | None = None
    userImpact: str | None = None
    responseStrategy: str | None = None

    @field_validator("eventDescription", "cause", "socialImpact", "userImpact", "responseStrategy")
    @classmethod
    def normalize_text(cls, value: str | None) -> str | None:
        return " ".join(value.split()) if value is not None else None


COMMON_SCHEMA = {"type": "object", "properties": {"summary": {"type": "array", "items": {"type": "string"}, "minItems": 2, "maxItems": 5}, "eventDescription": {"type": "string"}, "cause": {"type": "string"}, "socialImpact": {"type": "string"}}, "required": ["summary", "eventDescription", "cause", "socialImpact"], "additionalProperties": False}
PERSONALIZED_SCHEMA = {"type": "object", "properties": {"userImpact": {"type": "string"}, "responseStrategy": {"type": "string"}}, "required": ["userImpact", "responseStrategy"], "additionalProperties": False}


def is_mock_enabled() -> bool:
    return os.getenv("AI_REPORT_MOCK_ENABLED", "false").strip().lower() == "true"


def build_mock_response(request: NewsReportGenerateRequest) -> NewsReportGenerateResponse:
    if request.generationType == "PERSONALIZED":
        nickname = (request.userProfile or {}).get("nickname", "사용자")
        return NewsReportGenerateResponse(userImpact=f"[MOCK] {nickname}님은 맞춤 영향이 있어요.", responseStrategy="[MOCK] 현재 자산을 확인하세요.")
    return NewsReportGenerateResponse(summary=[f"[MOCK] {request.title} 핵심 요약 1", "[MOCK] 핵심 요약 2"], eventDescription="[MOCK] 사건 설명입니다.", cause="[MOCK] 원인입니다.", socialImpact="[MOCK] 사회 영향입니다.")


def _error_code(error: GroqError) -> str | None:
    body = getattr(error, "body", None)
    error_body = body.get("error") if isinstance(body, dict) else None
    return error_body.get("code") if isinstance(error_body, dict) else None


def get_report_model() -> str:
    return os.getenv("GROQ_REPORT_MODEL") or os.getenv("GROQ_MODEL", "openai/gpt-oss-20b")


def generate_financial_report(client: Groq, request: NewsReportGenerateRequest, model: str) -> NewsReportGenerateResponse:
    personalized = request.generationType == "PERSONALIZED"
    if personalized and not request.userProfile:
        raise HTTPException(status_code=422, detail="맞춤 분석에 사용자 프로필이 필요합니다.")
    instructions = PERSONALIZATION_INSTRUCTIONS if personalized else COMMON_REPORT_INSTRUCTIONS
    report_input = (build_personalization_input(request.title, request.category, request.source, request.publishedAt, request.content, json.dumps(request.userProfile, ensure_ascii=False, separators=(",", ":"))) if personalized else build_common_report_input(request.title, request.category, request.source, request.publishedAt, request.content))
    schema = PERSONALIZED_SCHEMA if personalized else COMMON_SCHEMA
    reasoning = {"reasoning_format": "hidden", "reasoning_effort": "low"} if model.startswith("openai/gpt-oss-") else {}
    retries = 0
    report_request_id = current_request_id() or f"report-news-{request.newsId}"
    while True:
        try:
            with timed_groq_completion(client, operation="report.personalize" if personalized else "report.generate", model=model, requested_completion_tokens=REPORT_MAX_COMPLETION_TOKENS, request_id=report_request_id) as timing:
                response = timing.create(messages=[{"role": "system", "content": instructions}, {"role": "user", "content": report_input}], response_format={"type": "json_schema", "json_schema": {"name": "financial_report", "strict": True, "schema": schema}}, max_completion_tokens=REPORT_MAX_COMPLETION_TOKENS, **reasoning)
            break
        except GroqError as error:
            if _error_code(error) == "json_validate_failed" and retries < JSON_VALIDATION_MAX_RETRIES:
                retries += 1
                continue
            logger.error("Groq 리포트 생성 실패 - newsId: %s", request.newsId, exc_info=True)
            raise HTTPException(status_code=502, detail="Groq AI 서버 호출에 실패했습니다.") from error
    content = response.choices[0].message.content if response.choices else None
    try:
        result = NewsReportGenerateResponse.model_validate(json.loads(content or ""))
    except (json.JSONDecodeError, ValidationError) as error:
        raise HTTPException(status_code=502, detail="AI 응답 형식이 올바르지 않습니다.") from error
    required = [result.userImpact, result.responseStrategy] if personalized else [result.summary, result.eventDescription, result.cause, result.socialImpact]
    if any(value is None or value == "" or value == [] for value in required):
        raise HTTPException(status_code=502, detail="AI 응답에 필수 값이 없습니다.")
    return result


@router.post("/reports/generate", response_model=NewsReportGenerateResponse)
def generate_report(request: NewsReportGenerateRequest) -> NewsReportGenerateResponse:
    if is_mock_enabled():
        return build_mock_response(request)
    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        raise HTTPException(status_code=503, detail="AI 서버 설정이 완료되지 않았습니다.")
    client = Groq(api_key=api_key, max_retries=0)
    try:
        return generate_financial_report(client, request, get_report_model())
    except ApplicationGuardError:
        raise
    except HTTPException:
        raise
    except Exception as error:
        logger.error(
            "리포트 생성 중 예상하지 못한 오류 - newsId: %s, 예외: %s",
            request.newsId, type(error).__name__,
        )
        raise HTTPException(status_code=500, detail="리포트 생성 중 오류가 발생했습니다.") from error
