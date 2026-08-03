import json
import logging
import os
from collections.abc import Callable
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


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1)
    generate_title: bool = Field(default=False, alias="generateTitle")


class ChatResponse(BaseModel):
    answer: str
    title: str | None = None


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


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


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
