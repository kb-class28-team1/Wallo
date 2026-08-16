import logging
from time import perf_counter
from typing import Any


LOGGER = logging.getLogger("wallo_ai")


def start_timer() -> float:
    return perf_counter()


def log_groq_completion_timing(
    operation: str,
    model: str,
    started_at: float,
    completion: Any = None,
    requested_completion_tokens: int | None = None,
) -> None:
    """Log non-sensitive timing and token metadata for one Groq completion."""
    usage = getattr(completion, "usage", None)
    choices = getattr(completion, "choices", None) or []
    first_choice = choices[0] if choices else None

    LOGGER.info(
        "[AI_TIMING] operation=%s model=%s elapsedMs=%d "
        "promptTokens=%s completionTokens=%s totalTokens=%s "
        "requestedCompletionTokens=%s finishReason=%s responseReceived=%s",
        operation,
        model,
        round((perf_counter() - started_at) * 1000),
        _usage_value(usage, "prompt_tokens"),
        _usage_value(usage, "completion_tokens"),
        _usage_value(usage, "total_tokens"),
        requested_completion_tokens,
        getattr(first_choice, "finish_reason", None),
        completion is not None,
    )


def _usage_value(usage: Any, key: str) -> Any:
    if usage is None:
        return None
    if isinstance(usage, dict):
        return usage.get(key)
    return getattr(usage, key, None)
