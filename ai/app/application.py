import json
import logging
import os
from collections.abc import Callable
from functools import lru_cache
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from dotenv import load_dotenv
from groq import Groq, GroqError
from pydantic import BaseModel, ConfigDict, Field


load_dotenv()
# Uvicorn의 기본 로그 핸들러를 사용해 Tool Calling 결과가 서버 터미널에 보이게 한다.
logger = logging.getLogger("uvicorn.error")

MODEL = os.getenv("GROQ_MODEL", "openai/gpt-oss-20b")
SYSTEM_PROMPT = """
당신은 친절한 한국어 금융 AI 어시스턴트입니다.
사용자의 요청이 제공된 금융 도구의 설명과 명확히 일치하면 해당 도구를 호출하세요.
일치하지 않거나 일반적인 대화라면 도구를 호출하지 말고 직접 답변하세요.
답변은 핵심부터 말하고 기본적으로 3~5문장, 500자 이내로 간결하게 작성하세요.
불필요한 서론, 반복 설명, 과도한 목록은 생략하세요.
사용자가 상세한 설명이나 보고서를 명시적으로 요청한 경우에만 필요한 만큼 길게 답변하세요.
도구가 아직 실제 데이터와 연결되지 않았다는 결과를 받으면, 완료한 것처럼 꾸미지 말고
어떤 기능이 선택되었으며 추후 어떤 데이터 연동이 필요한지 간결하게 안내하세요.
""".strip()

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1)
    generate_title: bool = Field(default=False, alias="generateTitle")

from app.financial_report import router as financial_report_router

logger = logging.getLogger("wallo_ai")
logging.basicConfig(level=logging.INFO)
class ChatResponse(BaseModel):
    answer: str
    title: str | None = None


class DemoAssetAnalysisRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    profile_id: int = Field(gt=0, alias="profileId")
    question: str = Field(
        default="현재 자산 상태를 분석하고 우선 실행할 행동을 알려줘.",
        min_length=1,
    )


class DemoProfileSummary(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    profile_id: int = Field(alias="profileId")
    title: str
    total_assets_krw: int | None = Field(alias="totalAssetsKrw")
    monthly_net_income_krw: int | None = Field(alias="monthlyNetIncomeKrw")


class DemoAssetAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    profile_id: int = Field(alias="profileId")
    title: str
    answer: str


DATA_FILE = (
        Path(__file__).resolve().parents[1]
        / "data"
        / "processed"
        / "money_log_agent_inputs.json"
)


@lru_cache(maxsize=1)
def load_demo_profiles() -> dict[int, dict[str, Any]]:
    """정제된 머니로그 사례를 가상 사용자 프로필로 읽는다."""
    try:
        rows = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        logger.exception("Failed to load demo asset profiles")
        raise RuntimeError("가상 사용자 데이터를 읽지 못했습니다.") from error

    profiles: dict[int, dict[str, Any]] = {}
    for row in rows:
        profile = row.get("asset_analysis_input")
        if isinstance(profile, dict) and isinstance(profile.get("profile_id"), int):
            profiles[profile["profile_id"]] = profile
    return profiles


def list_demo_profiles() -> list[DemoProfileSummary]:
    summaries = []
    for profile_id, profile in sorted(load_demo_profiles().items()):
        source = profile.get("source") or {}
        assets = profile.get("assets") or {}
        income = profile.get("income") or {}
        summaries.append(
            DemoProfileSummary(
                profileId=profile_id,
                title=source.get("title") or f"가상 사용자 {profile_id}",
                totalAssetsKrw=assets.get("total_assets_krw"),
                monthlyNetIncomeKrw=income.get("monthly_net_income_krw"),
            )
        )
    return summaries


def build_demo_asset_facts(profile: dict[str, Any]) -> dict[str, Any]:
    """LLM의 산술 오류를 줄이기 위해 확정 가능한 지표를 코드로 계산한다."""
    income = profile.get("income") or {}
    assets = profile.get("assets") or {}
    cashflow = profile.get("cashflow") or {}
    debts = profile.get("debts") or []

    monthly_income = income.get("monthly_net_income_krw")
    monthly_saving = cashflow.get("monthly_saving_total_krw")
    monthly_expense = cashflow.get("monthly_total_expense_krw")
    total_assets = assets.get("total_assets_krw")
    asset_item_sum = sum(
        item.get("amount_krw") or 0
        for item in assets.get("items") or []
        if isinstance(item, dict)
    )
    total_debt = sum(
        debt.get("amount_krw") or 0
        for debt in debts
        if isinstance(debt, dict)
    )

    return {
        "monthly_net_income_krw": monthly_income,
        "monthly_saving_krw": monthly_saving,
        "monthly_expense_krw": monthly_expense,
        "annual_saving_krw": monthly_saving * 12 if monthly_saving is not None else None,
        "saving_rate_percent": (
            round(monthly_saving / monthly_income * 100, 1)
            if monthly_saving is not None and monthly_income
            else None
        ),
        "total_assets_krw": total_assets,
        "listed_asset_items_sum_krw": asset_item_sum,
        "asset_detail_unexplained_gap_krw": (
            total_assets - asset_item_sum if total_assets is not None else None
        ),
        "total_debt_krw": total_debt,
    }


def compact_demo_profile(profile: dict[str, Any]) -> dict[str, Any]:
    """LLM 입력에서 중복 원문·추출 근거를 제거해 출력 토큰을 확보한다."""
    excluded_keys = {"raw_user_content", "source"}

    def compact(value: Any) -> Any:
        if isinstance(value, dict):
            return {
                key: compact(item)
                for key, item in value.items()
                if key not in excluded_keys
                and not key.endswith("_raw")
                and not key.endswith("_evidence")
                and key != "raw_text"
            }
        if isinstance(value, list):
            return [compact(item) for item in value]
        return value

    return compact(profile)


def format_demo_asset_facts(facts: dict[str, Any]) -> str:
    def won(value: int | None) -> str:
        return "정보 없음" if value is None else f"{value:,}원"

    saving_rate = facts.get("saving_rate_percent")
    rate_text = "정보 없음" if saving_rate is None else f"{saving_rate}%"
    return "\n".join(
        [
            "## 코드로 계산한 핵심 수치",
            f"- 월 순소득: {won(facts.get('monthly_net_income_krw'))}",
            f"- 월 저축액: {won(facts.get('monthly_saving_krw'))}",
            f"- 월 지출액: {won(facts.get('monthly_expense_krw'))}",
            f"- 연 저축액: {won(facts.get('annual_saving_krw'))}",
            f"- 저축률: {rate_text}",
            f"- 총자산: {won(facts.get('total_assets_krw'))}",
            f"- 세부 자산 항목 합계: {won(facts.get('listed_asset_items_sum_krw'))}",
            "- 총자산과 세부 항목의 미기재 차액: "
            + won(facts.get("asset_detail_unexplained_gap_krw")),
            f"- 총부채: {won(facts.get('total_debt_krw'))}",
        ]
    )


def generate_demo_asset_analysis(
    client: Groq,
    profile: dict[str, Any],
    question: str,
) -> str:
    """전문가 답안을 제외한 가상 사용자 정보만으로 자산 분석을 생성한다."""
    calculated_facts = build_demo_asset_facts(profile)
    compact_profile = compact_demo_profile(profile)
    completion = client.chat.completions.create(
        model=MODEL,
        messages=[
            {
                "role": "system",
                "content": (
                    "당신은 한국어 개인재무 자산분석가입니다. 제공된 가상 사용자의 수치만 "
                    "근거로 분석하고, 없는 정보는 추측하지 마세요. 산술 계산은 직접 다시 하지 "
                    "말고 '코드로 계산한 핵심 지표'를 그대로 사용하세요. 핵심 수치는 시스템이 "
                    "별도로 출력하므로 답변 본문에는 금액이나 비율 숫자를 다시 쓰지 마세요. "
                    "asset_detail_unexplained_gap_krw는 총자산과 "
                    "세부 항목 합계의 차이일 뿐이므로 자산 종류를 추정하거나 분류하지 마세요. 금액 간 "
                    "불일치가 있으면 명시하고 원문보다 구조화된 값을 우선 사용하세요. "
                    "답변은 반드시 ① 한줄 진단 ② 강점 ③ 위험 신호 ④ 우선 행동 3가지 "
                    "⑤ 추가로 필요한 정보 순서로 작성하세요. 투자 수익을 보장하거나 특정 "
                    "금융상품의 매수를 단정하지 마세요."
                ),
            },
            {
                "role": "user",
                "content": (
                    f"질문: {question}\n\n"
                    "코드로 계산한 핵심 지표:\n"
                    + json.dumps(calculated_facts, ensure_ascii=False, indent=2)
                    + "\n\n"
                    "가상 사용자 금융 데이터:\n"
                    + json.dumps(compact_profile, ensure_ascii=False, indent=2)
                ),
            },
        ],
        reasoning_effort="low",
        max_completion_tokens=4000,
    )
    analysis = completion.choices[0].message.content or "자산 분석 결과를 생성하지 못했습니다."
    return format_demo_asset_facts(calculated_facts) + "\n\n" + analysis


def _tool(name: str, description: str) -> dict[str, Any]:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": {
                    "request": {
                        "type": "string",
                        "description": "도구로 처리할 사용자의 원래 요청",
                    }
                },
                "required": ["request"],
                "additionalProperties": False,
            },
        },
    }


TOOLS = [
    _tool(
        "analyze_assets",
        "사용자의 예금, 투자, 부채 등 보유 자산 현황을 분석하고 진단할 때 사용한다.",
    ),
    _tool(
        "coach_spending",
        "사용자의 지출 내역, 소비 습관, 예산을 분석해 소비 개선 코칭을 제공할 때 사용한다.",
    ),
    _tool(
        "set_financial_goal",
        "저축, 투자, 부채 상환 등 구체적인 금융 목표를 새로 설정하거나 수정할 때 사용한다.",
    ),
    _tool(
        "create_goal_roadmap",
        "이미 정한 금융 목표를 달성하기 위한 단계별 일정과 실행 로드맵을 만들 때 사용한다.",
    ),
    _tool(
        "recommend_financial_products",
        "예금, 적금, 카드, 대출, 투자 등 금융 상품을 탐색하거나 비교할 때 사용한다.",
    ),
    _tool(
        "generate_financial_report",
        "사용자의 금융 상태나 기간별 자산·소비 내용을 종합한 금융 리포트를 생성할 때 사용한다.",
    ),
]

TITLE_TOOL = {
    "type": "function",
    "function": {
        "name": "generate_conversation_title",
        "description": "첫 사용자 메시지와 첫 AI 답변을 요약해 채팅방 제목을 생성한다.",
        "parameters": {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "핵심 주제와 목적이 드러나는 8~20자의 한국어 명사형 제목",
                }
            },
            "required": ["title"],
            "additionalProperties": False,
        },
    },
}


def _pending_tool(tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    """실제 도메인 서비스가 구현될 때 교체할 도구 실행 경계."""
    return {
        "status": "pending_integration",
        "tool": tool_name,
        "request": arguments.get("request", ""),
        "message": "의도에 맞는 도구가 선택되었습니다. 실제 금융 데이터 서비스 연결이 필요합니다.",
    }


TOOL_HANDLERS: dict[str, Callable[[str, dict[str, Any]], dict[str, Any]]] = {
    tool["function"]["name"]: _pending_tool for tool in TOOLS
}


def _parse_arguments(raw_arguments: str) -> dict[str, Any]:
    try:
        arguments = json.loads(raw_arguments or "{}")
    except json.JSONDecodeError:
        return {"request": raw_arguments}
    return arguments if isinstance(arguments, dict) else {"request": str(arguments)}


def generate_answer(client: Groq, user_message: str) -> str:
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_message},
    ]
    completion = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=TOOLS,
        tool_choice="auto",
        max_completion_tokens=500,
    )
    assistant_message = completion.choices[0].message

    if not assistant_message.tool_calls:
        logger.info("[AI ROUTING] direct_response (tool not called)")
        return assistant_message.content or "답변을 생성하지 못했습니다."

    # gpt-oss-20b는 병렬 로컬 도구 호출을 지원하지 않으므로 첫 호출만 처리한다.
    tool_call = assistant_message.tool_calls[0]
    tool_name = tool_call.function.name
    logger.info("[AI TOOL] selected=%s", tool_name)
    handler = TOOL_HANDLERS.get(tool_name)
    if handler is None:
        tool_result = {"status": "error", "message": f"지원하지 않는 도구입니다: {tool_name}"}
    else:
        arguments = _parse_arguments(tool_call.function.arguments)
        tool_result = handler(tool_name, arguments)
    logger.info(
        "[AI TOOL RESULT] tool=%s status=%s",
        tool_name,
        tool_result.get("status", "unknown"),
    )

    messages.append(
        {
            "role": "assistant",
            "content": assistant_message.content,
            "tool_calls": [tool_call.model_dump(exclude_none=True)],
        }
    )
    messages.append(
        {
            "role": "tool",
            "tool_call_id": tool_call.id,
            "content": json.dumps(tool_result, ensure_ascii=False),
        }
    )
    final_completion = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        max_completion_tokens=500,
    )
    return final_completion.choices[0].message.content or "도구 호출 결과를 정리하지 못했습니다."


def generate_conversation_title(
    client: Groq,
    user_message: str,
    assistant_answer: str,
) -> str | None:
    try:
        logger.info("[TITLE TOOL] requested=generate_conversation_title")
        completion = client.chat.completions.create(
            model=MODEL,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "첫 대화의 핵심 주제와 사용자의 목적을 제목으로 요약하세요. "
                        "제목은 8~20자의 한국어 명사형으로 작성하고 따옴표, 마침표, 이모지를 쓰지 마세요. "
                        "반드시 제공된 generate_conversation_title 도구를 호출하세요."
                    ),
                },
                {
                    "role": "user",
                    "content": (
                        f"첫 사용자 메시지:\n{user_message}\n\n"
                        f"첫 AI 답변:\n{assistant_answer}"
                    ),
                },
            ],
            tools=[TITLE_TOOL],
            tool_choice={
                "type": "function",
                "function": {"name": "generate_conversation_title"},
            },
            reasoning_effort="low",
            max_completion_tokens=500,
        )
        tool_calls = completion.choices[0].message.tool_calls
        if not tool_calls:
            logger.warning("Groq did not call the conversation title tool")
            return None

        arguments = _parse_arguments(tool_calls[0].function.arguments)
        title = str(arguments.get("title", "")).strip(" \t\r\n\"'.")
        normalized_title = title[:30] if title else None
        logger.info(
            "[TITLE TOOL RESULT] status=%s title=%s",
            "success" if normalized_title else "empty",
            normalized_title or "(none)",
        )
        return normalized_title
    except GroqError:
        logger.exception("Groq title generation failed")
        return None


app = FastAPI(title="Wallo AI Server")

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


@app.get(
    "/api/demo/asset-profiles",
    response_model=list[DemoProfileSummary],
)
def demo_asset_profiles() -> list[DemoProfileSummary]:
    try:
        return list_demo_profiles()
    except RuntimeError as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@app.post(
    "/api/demo/asset-analysis",
    response_model=DemoAssetAnalysisResponse,
)
def demo_asset_analysis(
    request: DemoAssetAnalysisRequest,
) -> DemoAssetAnalysisResponse:
    try:
        profile = load_demo_profiles().get(request.profile_id)
        if profile is None:
            raise HTTPException(
                status_code=404,
                detail=f"가상 사용자 프로필을 찾을 수 없습니다: {request.profile_id}",
            )

        api_key = os.getenv("GROQ_API_KEY")
        if not api_key:
            raise HTTPException(status_code=503, detail="GROQ_API_KEY가 설정되지 않았습니다.")

        answer = generate_demo_asset_analysis(
            Groq(api_key=api_key),
            profile,
            request.question,
        )
        source = profile.get("source") or {}
        return DemoAssetAnalysisResponse(
            profileId=request.profile_id,
            title=source.get("title") or f"가상 사용자 {request.profile_id}",
            answer=answer,
        )
    except GroqError as error:
        logger.exception("Groq demo asset analysis failed")
        raise HTTPException(
            status_code=502,
            detail="Groq AI 자산분석 결과를 생성하지 못했습니다.",
        ) from error
    except RuntimeError as error:
        raise HTTPException(status_code=500, detail=str(error)) from error


@app.post("/api/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> ChatResponse:
    try:
        api_key = os.getenv("GROQ_API_KEY")
        if not api_key:
            raise HTTPException(status_code=503, detail="GROQ_API_KEY가 설정되지 않았습니다.")
        client = Groq(api_key=api_key)
        answer = generate_answer(client, request.message)
        title = (
            generate_conversation_title(client, request.message, answer)
            if request.generate_title
            else None
        )
        return ChatResponse(answer=answer, title=title)
    except GroqError as error:
        logger.exception("Groq API request failed")
        raise HTTPException(
            status_code=502,
            detail="Groq AI 응답을 생성하지 못했습니다.",
        ) from error
