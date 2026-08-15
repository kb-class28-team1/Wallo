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
    retry_count: int | None = None,
    rate_limited: bool | None = None,
    fallback_used: bool = False,
    fallback_reason: str | None = None,
    failure_reason: str | None = None,
    success: bool | None = None,
) -> None:
    """Log non-sensitive timing and token metadata for one Groq completion."""
    usage = getattr(completion, "usage", None)
    choices = getattr(completion, "choices", None) or []
    first_choice = choices[0] if choices else None

    LOGGER.info(
        "[AI_TIMING] operation=%s model=%s elapsedMs=%d "
        "promptTokens=%s completionTokens=%s totalTokens=%s "
        "requestedCompletionTokens=%s finishReason=%s responseReceived=%s "
        "retryCount=%s rateLimited=%s fallbackUsed=%s fallbackReason=%s "
        "failureReason=%s success=%s",
        operation,
        model,
        round((perf_counter() - started_at) * 1000),
        _usage_value(usage, "prompt_tokens"),
        _usage_value(usage, "completion_tokens"),
        _usage_value(usage, "total_tokens"),
        requested_completion_tokens,
        getattr(first_choice, "finish_reason", None),
        completion is not None,
        retry_count,
        rate_limited,
        fallback_used,
        fallback_reason,
        failure_reason,
        success,
    )


def reset_groq_retry_tracking(client: Any) -> None:
    reset = getattr(client, "reset_retry_tracking", None)
    if not callable(reset):
        return
    try:
        reset()
    except Exception:
        LOGGER.debug("Groq retry tracking reset failed", exc_info=True)


def get_groq_retry_count(client: Any) -> int | None:
    getter = getattr(client, "get_retry_count", None)
    if not callable(getter):
        return None
    try:
        value = getter()
    except Exception:
        LOGGER.debug("Groq retry count read failed", exc_info=True)
        return None
    return value if isinstance(value, int) and not isinstance(value, bool) else None


def was_groq_rate_limited(client: Any) -> bool | None:
    getter = getattr(client, "was_rate_limited", None)
    if not callable(getter):
        return None
    try:
        value = getter()
    except Exception:
        LOGGER.debug("Groq rate-limit state read failed", exc_info=True)
        return None
    return value if isinstance(value, bool) else None


def _usage_value(usage: Any, key: str) -> Any:
    if usage is None:
        return None
    if isinstance(usage, dict):
        return usage.get(key)
    return getattr(usage, key, None)
