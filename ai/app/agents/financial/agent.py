import json
import logging
from typing import Any

from groq import Groq

from app.agents.financial.asset_analysis_cache import (
    build_cache_key,
    cache_answer,
    get_cached_answer,
)
from app.agents.financial.prompts import SYSTEM_PROMPT
from app.agents.financial.tools.registry import TOOL_SCHEMAS, execute_tool
from app.agents.goal.context import FinancialContext
from app.core.config import get_groq_model
from app.core.ai_timing import timed_groq_completion
from app.agents.financial.consumption_models import ConsumptionContext
from app.agents.financial.spending_intent import (
    build_spending_arguments,
    is_spending_request,
)

logger = logging.getLogger("wallo_ai")
ASSET_ANALYSIS_TOOL = "analyze_assets"
PRODUCT_RECOMMENDATION_TOOL = "recommend_financial_products"
SPENDING_ANALYSIS_TOOL = "coach_spending"
DEFAULT_FINAL_COMPLETION_TOKENS = 500
ASSET_ANALYSIS_FINAL_COMPLETION_TOKENS = 1600
PRODUCT_RECOMMENDATION_FINAL_COMPLETION_TOKENS = 1000
SPENDING_ANALYSIS_FINAL_COMPLETION_TOKENS = 1600
ASSET_ANALYSIS_JSON_INSTRUCTION = """
analyze_assets 도구가 성공한 경우 최종 답변은 JSON 객체 하나만 반환하세요.
마크다운, 코드 블록, JSON 앞뒤의 설명은 사용하지 마세요. 금액과 비율은 도구 결과의
calculatedMetrics를 그대로 사용하고 다시 계산하지 마세요. 입력에 없는 목표·기간·위험선호도는
추측하지 말고 조건부 표현이나 additionalInfo에 기록하세요.

반드시 다음 구조를 사용하세요.
{
  "direction": {
    "headline": "현재 자산이 나아갈 방향을 한 문장으로 요약",
    "currentStage": "현재 가장 우선할 재무 단계",
    "reasons": ["방향을 판단한 수치 근거"],
    "keep": "현재 유지할 점과 이유",
    "firstChange": "가장 먼저 바꿀 한 가지와 이유",
    "threeMonthDirection": "앞으로 3개월 동안 실행할 방향과 점검 기준",
    "oneYearDirection": "앞으로 1년 동안 유지·조정할 방향과 점검 기준",
    "riskSignals": ["주의할 위험 신호"],
    "additionalInfo": ["더 정확한 방향 설정에 필요한 정보"]
  }
}
""".strip()


def parse_tool_arguments(raw_arguments: str) -> dict[str, Any]:
    try:
        arguments = json.loads(raw_arguments or "{}")
    except json.JSONDecodeError:
        return {"request": raw_arguments}
    return arguments if isinstance(arguments, dict) else {"request": str(arguments)}


def parse_asset_direction(content: str | None) -> dict[str, Any] | None:
    """자산분석 최종 응답에서 direction 객체를 추출한다."""
    if not content or not content.strip():
        return None

    candidate = content.strip()
    if candidate.startswith("```"):
        lines = candidate.splitlines()
        if lines and lines[0].strip().startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        candidate = "\n".join(lines).strip()

    try:
        payload = json.loads(candidate)
    except json.JSONDecodeError:
        start = candidate.find("{")
        end = candidate.rfind("}")
        if start < 0 or end <= start:
            return {"summary": content.strip()}
        try:
            payload = json.loads(candidate[start:end + 1])
        except json.JSONDecodeError:
            return {"summary": content.strip()}

    if not isinstance(payload, dict):
        return {"summary": content.strip()}

    direction = payload.get("direction", payload)
    if isinstance(direction, dict):
        return direction
    if isinstance(direction, str) and direction.strip():
        return {"summary": direction.strip()}
    return {"summary": content.strip()}


def attach_asset_direction(
    tool_data: dict[str, Any],
    answer: str | None,
) -> None:
    direction = parse_asset_direction(answer)
    if direction is not None:
        tool_data["direction"] = direction


class FinancialAgent:
    def __init__(self, client: Groq, model: str | None = None):
        self.client = client
        self.model = model or get_groq_model()
        self.selected_tool: str | None = None
        self.selected_tool_result: dict[str, Any] | None = None

    def run(
        self,
        user_message: str,
        history: list[dict[str, str]] | None = None,
        summary: str | None = None,
        financial_context: FinancialContext | None = None,
        consumption_context: ConsumptionContext | None = None,
        previous_consumption_period: dict[str, Any] | None = None,
    ) -> str:
        self.selected_tool = None
        self.selected_tool_result = None
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
        ]
        if summary and summary.strip():
            messages.append({
                "role": "system",
                "content": (
                    "다음은 이 채팅방의 오래된 대화를 누적 요약한 장기 기억입니다. "
                    "현재 질문과 관련 있을 때만 활용하고, 최근 대화와 충돌하면 최근 대화를 우선하세요.\n\n"
                    + summary.strip()
                ),
            })
        messages.extend(history or [])
        messages.append({"role": "user", "content": user_message})
        if is_spending_request(user_message, previous_consumption_period):
            arguments = build_spending_arguments(
                user_message, previous_consumption_period
            )
            tool_result = execute_tool(
                SPENDING_ANALYSIS_TOOL, arguments, consumption_context
            )
            self.selected_tool = SPENDING_ANALYSIS_TOOL
            if tool_result.status == "success" and isinstance(tool_result.data, dict):
                self.selected_tool_result = tool_result.data
            messages.insert(len(messages) - 1, {
                "role": "system",
                "content": (
                    "다음은 coach_spending 도구가 계산한 결과입니다. 수치를 다시 계산하거나 "
                    "추측하지 말고 사용자의 질문에 맞춰 설명하세요.\n"
                    + json.dumps(tool_result.to_dict(), ensure_ascii=False)
                ),
            })
            with timed_groq_completion(
                self.client,
                operation="chat.consumption.final",
                model=self.model,
                requested_completion_tokens=SPENDING_ANALYSIS_FINAL_COMPLETION_TOKENS,
            ) as timing:
                final_completion = timing.create(
                    messages=messages,
                    max_completion_tokens=SPENDING_ANALYSIS_FINAL_COMPLETION_TOKENS,
                )
                return (
                    final_completion.choices[0].message.content
                    or "소비분석 결과를 정리하지 못했습니다."
                )
        with timed_groq_completion(
            self.client,
            operation="chat.route",
            model=self.model,
            requested_completion_tokens=500,
        ) as timing:
            completion = timing.create(
                messages=messages,
                tools=TOOL_SCHEMAS,
                tool_choice="auto",
                max_completion_tokens=500,
            )
        assistant_message = completion.choices[0].message
        if not assistant_message.tool_calls:
            logger.info("[AI ROUTING] direct_response")
            return assistant_message.content or "답변을 생성하지 못했습니다."

        tool_call = assistant_message.tool_calls[0]
        self.selected_tool = tool_call.function.name
        if self.selected_tool == "set_financial_goal":
            logger.info("[AI ROUTING] goal_interview")
            return "목표 설정을 시작할게요."
        tool_result = execute_tool(
            tool_call.function.name,
            parse_tool_arguments(tool_call.function.arguments),
            consumption_context,
        )
        logger.info("[AI TOOL] selected=%s status=%s", tool_call.function.name, tool_result.status)
        if tool_result.status == "success" and isinstance(tool_result.data, dict):
            self.selected_tool_result = tool_result.data
        asset_cache_key = None
        cached_answer = None
        if (
            self.selected_tool == ASSET_ANALYSIS_TOOL
            and tool_result.status == "success"
            and isinstance(tool_result.data, dict)
        ):
            asset_cache_key = build_cache_key(tool_result.data, self.model)
            cached_answer = get_cached_answer(asset_cache_key)
            if cached_answer is not None:
                attach_asset_direction(tool_result.data, cached_answer)
                logger.info(
                    "[AI ASSET CACHE] hit profileId=%s",
                    tool_result.data.get("profileId"),
                )
                return cached_answer
        messages.extend([
            {
                "role": "assistant",
                "content": assistant_message.content,
                "tool_calls": [tool_call.model_dump(exclude_none=True)],
            },
            {
                "role": "tool",
                "tool_call_id": tool_call.id,
                "content": json.dumps(tool_result.to_dict(), ensure_ascii=False),
            },
        ])
        if self.selected_tool == ASSET_ANALYSIS_TOOL:
            messages.insert(1, {
                "role": "system",
                "content": ASSET_ANALYSIS_JSON_INSTRUCTION,
            })
        final_options: dict[str, Any] = {
            "model": self.model,
            "messages": messages,
            "max_completion_tokens": DEFAULT_FINAL_COMPLETION_TOKENS,
        }
        if self.selected_tool == ASSET_ANALYSIS_TOOL:
            final_options.update({
                "reasoning_effort": "low",
                "max_completion_tokens": ASSET_ANALYSIS_FINAL_COMPLETION_TOKENS,
                "response_format": {"type": "json_object"},
            })
        elif self.selected_tool == PRODUCT_RECOMMENDATION_TOOL:
            final_options.update({
                "reasoning_effort": "low",
                "max_completion_tokens": (
                    PRODUCT_RECOMMENDATION_FINAL_COMPLETION_TOKENS
                ),
            })
        elif self.selected_tool == SPENDING_ANALYSIS_TOOL:
            final_options.update({
                "max_completion_tokens": SPENDING_ANALYSIS_FINAL_COMPLETION_TOKENS,
            })
        with timed_groq_completion(
            self.client,
            operation="chat.final",
            model=self.model,
            requested_completion_tokens=final_options.get("max_completion_tokens"),
        ) as timing:
            final_completion = timing.create(**{
                key: value
                for key, value in final_options.items()
                if key != "model"
            })
        answer = final_completion.choices[0].message.content or "도구 호출 결과를 정리하지 못했습니다."
        if self.selected_tool == ASSET_ANALYSIS_TOOL:
            if isinstance(tool_result.data, dict):
                attach_asset_direction(tool_result.data, answer)
            cache_answer(asset_cache_key, answer)
        return answer


def generate_answer(client: Groq, user_message: str) -> str:
    """기존 호출부와 테스트를 위한 얇은 호환 함수."""
    return FinancialAgent(client).run(user_message)
