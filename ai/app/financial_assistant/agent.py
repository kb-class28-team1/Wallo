from __future__ import annotations

import json
import logging
from typing import Any, TYPE_CHECKING

from groq import Groq

from app.financial_assistant.asset_analysis_cache import (
    build_cache_key,
    cache_answer,
    get_cached_answer,
)
from app.financial_assistant.prompts import SYSTEM_PROMPT
from app.financial_assistant.tools.registry import (
    execute_tool,
    select_route_tool_schemas,
)
from app.financial_assistant.history import (
    BoundedConversationContext,
    HISTORY_TOKEN_BUDGET,
    SUMMARY_TOKEN_BUDGET,
    build_bounded_context,
)
from app.financial_assistant.tool_payload import compact_tool_result_for_prompt
from app.goals.interview.context import FinancialContext
from app.core.config import get_groq_model
from app.core.ai_timing import current_request_id, timed_groq_completion
from app.financial_assistant.consumption_models import ConsumptionContext
from app.financial_assistant.spending_intent import (
    build_spending_arguments,
    is_spending_request,
)

if TYPE_CHECKING:
    from app.chat.schemas import AssetAnalysisContext

logger = logging.getLogger("wallo_ai")
ASSET_ANALYSIS_TOOL = "analyze_assets"
PRODUCT_RECOMMENDATION_TOOL = "recommend_financial_products"
SPENDING_ANALYSIS_TOOL = "coach_spending"
# Direct replies only need a short answer; tool selection keeps the existing
# budget because the baseline included a tool-call response that ended by length.
ROUTE_DIRECT_COMPLETION_TOKENS = 256
ROUTE_TOOL_COMPLETION_TOKENS = 500
DEFAULT_FINAL_COMPLETION_TOKENS = 400
ASSET_ANALYSIS_FINAL_COMPLETION_TOKENS = 1000
PRODUCT_RECOMMENDATION_FINAL_COMPLETION_TOKENS = 1000
SPENDING_ANALYSIS_FINAL_COMPLETION_TOKENS = 1000
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


def _message_content_chars(messages: list[dict[str, Any]] | None) -> int:
    total = 0
    for message in messages or []:
        content = message.get("content") if isinstance(message, dict) else message
        if isinstance(content, str):
            total += len(content)
        else:
            try:
                total += len(json.dumps(content, ensure_ascii=False, default=str))
            except (TypeError, ValueError):
                total += len(str(content))
    return total


def _log_financial_context(
    *,
    stage: str,
    history: list[dict[str, Any]],
    summary: str | None,
    user_message: str,
    message_count: int,
    tool_name: str | None = None,
    tool_status: str | None = None,
    tool_result: dict[str, Any] | None = None,
    tool_result_json: str | None = None,
    tool_result_original_json: str | None = None,
    context: BoundedConversationContext | None = None,
) -> None:
    data = tool_result.get("data") if isinstance(tool_result, dict) else None
    products = data.get("products") if isinstance(data, dict) else None
    product_count = len(products) if isinstance(products, list) else 0
    tool_result_chars = len(tool_result_json) if tool_result_json is not None else 0
    tool_result_bytes = (
        len(tool_result_json.encode("utf-8"))
        if tool_result_json is not None
        else 0
    )
    tool_result_original_chars = (
        len(tool_result_original_json)
        if tool_result_original_json is not None
        else tool_result_chars
    )
    tool_result_original_bytes = (
        len(tool_result_original_json.encode("utf-8"))
        if tool_result_original_json is not None
        else tool_result_bytes
    )
    tool_result_reduction_percent = (
        round((1 - tool_result_bytes / tool_result_original_bytes) * 100, 1)
        if tool_result_original_bytes
        else 0.0
    )
    history_input_count = context.source_history_count if context else len(history)
    history_dropped_count = context.history_dropped_count if context else 0
    history_content_truncated = (
        context.history_content_truncated if context else False
    )
    summary_estimated_tokens = context.summary_estimated_tokens if context else 0
    summary_truncated = context.summary_truncated if context else False
    logger.info(
        "[AI_CONTEXT] stage=%s requestId=%s historyCount=%d "
        "historyInputCount=%d historyDroppedCount=%d "
        "historyContentChars=%d historyEstimatedTokens=%d "
        "historyBudgetTokens=%d historyContentTruncated=%s "
        "summaryChars=%d summaryEstimatedTokens=%d summaryBudgetTokens=%d "
        "summaryTruncated=%s userMessageChars=%d "
        "messageCount=%d tool=%s toolStatus=%s toolResultChars=%d "
        "toolResultBytes=%d toolResultOriginalChars=%d "
        "toolResultOriginalBytes=%d toolResultReductionPercent=%.1f "
        "productCount=%d",
        stage,
        current_request_id(),
        len(history),
        history_input_count,
        history_dropped_count,
        _message_content_chars(history),
        context.history_estimated_tokens if context else 0,
        HISTORY_TOKEN_BUDGET,
        history_content_truncated,
        len(summary.strip()) if isinstance(summary, str) else 0,
        summary_estimated_tokens,
        SUMMARY_TOKEN_BUDGET,
        summary_truncated,
        len(user_message),
        message_count,
        tool_name,
        tool_status,
        tool_result_chars,
        tool_result_bytes,
        tool_result_original_chars,
        tool_result_original_bytes,
        tool_result_reduction_percent,
        product_count,
    )


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
        asset_analysis_context: AssetAnalysisContext | None = None,
    ) -> str:
        self.selected_tool = None
        self.selected_tool_result = None
        context = build_bounded_context(history, summary)
        history = context.history
        summary = context.summary
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
        _log_financial_context(
            stage="route",
            history=history or [],
            summary=summary,
            user_message=user_message,
            message_count=len(messages),
            context=context,
        )
        if is_spending_request(user_message, previous_consumption_period):
            arguments = build_spending_arguments(
                user_message, previous_consumption_period
            )
            tool_result = execute_tool(
                SPENDING_ANALYSIS_TOOL,
                arguments,
                consumption_context=consumption_context,
                asset_analysis_context=asset_analysis_context,
            )
            self.selected_tool = SPENDING_ANALYSIS_TOOL
            if tool_result.status == "success" and isinstance(tool_result.data, dict):
                self.selected_tool_result = tool_result.data
            tool_payload = compact_tool_result_for_prompt(tool_result)
            messages.insert(len(messages) - 1, {
                "role": "system",
                "content": (
                    "다음은 coach_spending 도구가 계산한 결과입니다. 수치를 다시 계산하거나 "
                    "추측하지 말고 사용자의 질문에 맞춰 설명하세요.\n"
                    + json.dumps(tool_payload, ensure_ascii=False)
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
        route_tools = select_route_tool_schemas(user_message)
        route_completion_tokens = (
            ROUTE_TOOL_COMPLETION_TOKENS
            if route_tools
            else ROUTE_DIRECT_COMPLETION_TOKENS
        )
        route_options: dict[str, Any] = {
            "messages": messages,
            "reasoning_effort": "low",
            "max_completion_tokens": route_completion_tokens,
        }
        if route_tools:
            route_options.update({
                "tools": route_tools,
                "tool_choice": "auto",
            })
        with timed_groq_completion(
            self.client,
            operation="chat.route",
            model=self.model,
            requested_completion_tokens=route_completion_tokens,
        ) as timing:
            completion = timing.create(**route_options)
        assistant_message = completion.choices[0].message
        if not assistant_message.tool_calls:
            logger.info("[AI ROUTING] direct_response")
            return assistant_message.content or "답변을 생성하지 못했습니다."

        tool_call = assistant_message.tool_calls[0]
        self.selected_tool = tool_call.function.name
        if self.selected_tool == "set_financial_goal":
            logger.info("[AI ROUTING] goal_interview")
            return "목표 설정을 시작할게요."
        tool_arguments = parse_tool_arguments(tool_call.function.arguments)
        if tool_call.function.name == PRODUCT_RECOMMENDATION_TOOL:
            # 상품 유형·기간·금액은 모델이 구조화한 인자를 사용하되,
            # 가입대상 판별용 request는 사용자 원문을 보존한다.
            tool_arguments["request"] = user_message
        tool_result = execute_tool(
            tool_call.function.name,
            tool_arguments,
            consumption_context=consumption_context,
            asset_analysis_context=asset_analysis_context,
        )
        logger.info("[AI TOOL] selected=%s status=%s", tool_call.function.name, tool_result.status)
        tool_result_original_content = json.dumps(
            tool_result.to_dict(),
            ensure_ascii=False,
        )
        tool_payload = compact_tool_result_for_prompt(tool_result)
        tool_content = json.dumps(tool_payload, ensure_ascii=False)
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
                    "[AI ASSET CACHE] hit dataMode=%s",
                    tool_result.data.get("dataMode"),
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
                "content": tool_content,
            },
        ])
        _log_financial_context(
            stage="final",
            history=history or [],
            summary=summary,
            user_message=user_message,
            message_count=len(messages),
            tool_name=tool_result.tool,
            tool_status=tool_result.status,
            tool_result=tool_payload,
            tool_result_json=tool_content,
            tool_result_original_json=tool_result_original_content,
            context=context,
        )
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


def generate_answer(
    client: Groq,
    user_message: str,
    asset_analysis_context: AssetAnalysisContext | None = None,
) -> str:
    """기존 호출부와 테스트를 위한 얇은 호환 함수."""
    return FinancialAgent(client).run(
        user_message,
        asset_analysis_context=asset_analysis_context,
    )
