"""대화 컨텍스트를 모델 입력 예산 안으로 제한하는 유틸리티."""

from dataclasses import dataclass
import json
from math import ceil
from typing import Any


# Groq 응답의 실제 토큰 수는 모델별 tokenizer가 결정하므로, 의존성을 추가하지
# 않고도 안전하게 제한할 수 있도록 보수적인 추정치를 사용한다.
HISTORY_TOKEN_BUDGET = 1_200
SUMMARY_TOKEN_BUDGET = 600
MESSAGE_OVERHEAD_TOKENS = 4
ASCII_CHARS_PER_TOKEN = 4
TRUNCATION_MARKER = "\n[earlier context omitted]"


@dataclass(frozen=True)
class BoundedConversationContext:
    history: list[dict[str, str]]
    summary: str | None
    source_history_count: int
    history_dropped_count: int
    history_estimated_tokens: int
    summary_estimated_tokens: int
    history_content_truncated: bool
    summary_truncated: bool


def estimate_text_tokens(text: str) -> int:
    """문자열의 토큰 수를 보수적으로 추정한다.

    한국어·한자 등 비ASCII 문자는 문자당 1토큰, ASCII 문자는 4문자당
    1토큰으로 계산한다. 실제 사용량을 대체하는 값이 아니라 입력 예산을
    초과하지 않도록 선별하기 위한 추정치다.
    """
    if not text:
        return 0

    non_ascii_count = sum(not character.isascii() for character in text)
    ascii_count = len(text) - non_ascii_count
    return max(1, non_ascii_count + ceil(ascii_count / ASCII_CHARS_PER_TOKEN))


def _serialize_content(content: Any) -> str:
    if isinstance(content, str):
        return content
    try:
        return json.dumps(content, ensure_ascii=False, default=str)
    except (TypeError, ValueError):
        return str(content)


def estimate_message_tokens(message: dict[str, Any]) -> int:
    return MESSAGE_OVERHEAD_TOKENS + estimate_text_tokens(
        _serialize_content(message.get("content", ""))
    )


def _normalize_history_message(message: dict[str, Any]) -> dict[str, str]:
    role = message.get("role")
    normalized_role = role if role in {"user", "assistant"} else "user"
    return {
        "role": normalized_role,
        "content": _serialize_content(message.get("content", "")),
    }


def _longest_prefix_within_budget(text: str, token_budget: int) -> str:
    if token_budget <= 0:
        return ""
    if estimate_text_tokens(text) <= token_budget:
        return text

    low = 0
    high = len(text)
    while low < high:
        middle = (low + high + 1) // 2
        if estimate_text_tokens(text[:middle]) <= token_budget:
            low = middle
        else:
            high = middle - 1
    return text[:low]


def truncate_text(text: str, token_budget: int) -> tuple[str, bool]:
    """텍스트를 예산 안으로 줄이고, 생략 여부를 함께 반환한다."""
    if estimate_text_tokens(text) <= token_budget:
        return text, False

    marker_tokens = estimate_text_tokens(TRUNCATION_MARKER)
    if marker_tokens >= token_budget:
        return _longest_prefix_within_budget(text, token_budget), True

    prefix = _longest_prefix_within_budget(text, token_budget - marker_tokens).rstrip()
    return prefix + TRUNCATION_MARKER, True


def select_recent_history(
    history: list[dict[str, Any]] | None,
    token_budget: int = HISTORY_TOKEN_BUDGET,
) -> tuple[list[dict[str, str]], bool]:
    """최근 메시지의 연속된 suffix를 토큰 예산 안에서 선택한다."""
    if token_budget <= MESSAGE_OVERHEAD_TOKENS:
        raise ValueError("token_budget must be greater than message overhead")

    normalized_history = [
        _normalize_history_message(message)
        for message in (history or [])
    ]
    selected_reversed: list[dict[str, str]] = []
    used_tokens = 0
    content_truncated = False

    for message in reversed(normalized_history):
        message_tokens = estimate_message_tokens(message)
        remaining_tokens = token_budget - used_tokens
        if message_tokens <= remaining_tokens:
            selected_reversed.append(message)
            used_tokens += message_tokens
            continue

        # 가장 최근 메시지 하나가 예산보다 커도 통째로 버리지 않고 축약한다.
        if not selected_reversed:
            content, content_was_truncated = truncate_text(
                message["content"],
                remaining_tokens - MESSAGE_OVERHEAD_TOKENS,
            )
            selected_reversed.append({
                "role": message["role"],
                "content": content,
            })
            content_truncated = content_was_truncated
        # 그보다 오래된 메시지는 연속성을 유지하기 위해 더 이상 추가하지 않는다.
        break

    return list(reversed(selected_reversed)), content_truncated


def build_bounded_context(
    history: list[dict[str, Any]] | None,
    summary: str | None,
    *,
    history_token_budget: int = HISTORY_TOKEN_BUDGET,
    summary_token_budget: int = SUMMARY_TOKEN_BUDGET,
) -> BoundedConversationContext:
    """이력과 장기 요약을 각각 고정 예산으로 제한한다."""
    source_history = history or []
    bounded_history, history_content_truncated = select_recent_history(
        source_history,
        history_token_budget,
    )

    normalized_summary = summary.strip() if isinstance(summary, str) else ""
    bounded_summary = None
    summary_truncated = False
    if normalized_summary:
        bounded_summary, summary_truncated = truncate_text(
            normalized_summary,
            summary_token_budget,
        )

    return BoundedConversationContext(
        history=bounded_history,
        summary=bounded_summary,
        source_history_count=len(source_history),
        history_dropped_count=len(source_history) - len(bounded_history),
        history_estimated_tokens=sum(
            estimate_message_tokens(message) for message in bounded_history
        ),
        summary_estimated_tokens=estimate_text_tokens(bounded_summary or ""),
        history_content_truncated=history_content_truncated,
        summary_truncated=summary_truncated,
    )
