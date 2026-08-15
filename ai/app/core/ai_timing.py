import logging
from time import perf_counter
from typing import Any


LOGGER = logging.getLogger("wallo_ai")


class GroqCompletionTimer:
    """공통 Groq completion 호출·결과 측정 컨텍스트."""

    def __init__(
        self,
        client: Any,
        operation: str,
        model: str,
        requested_completion_tokens: int | None = None,
    ):
        self.client = client
        self.operation = operation
        self.model = model
        self.requested_completion_tokens = requested_completion_tokens
        self.started_at: float | None = None
        self.completion: Any = None
        self.failure_reason: str | None = None
        self.fallback_used = False
        self.fallback_reason: str | None = None
        self.success = False

    def __enter__(self) -> "GroqCompletionTimer":
        reset_groq_retry_tracking(self.client)
        self.started_at = start_timer()
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> bool:
        if exc_type is not None:
            self.success = False
            if self.failure_reason is None:
                self.failure_reason = exc_type.__name__
        log_groq_completion_timing(
            operation=self.operation,
            model=self.model,
            started_at=self.started_at or start_timer(),
            completion=self.completion,
            requested_completion_tokens=self.requested_completion_tokens,
            retry_count=get_groq_retry_count(self.client),
            rate_limited=was_groq_rate_limited(self.client),
            fallback_used=self.fallback_used,
            fallback_reason=self.fallback_reason,
            failure_reason=self.failure_reason,
            success=self.success,
        )
        return False

    def create(self, **request_options: Any) -> Any:
        """공통 측정 범위 안에서 Groq completion을 한 번 호출한다."""
        if self.requested_completion_tokens is None:
            requested = request_options.get("max_completion_tokens")
            if isinstance(requested, int) and not isinstance(requested, bool):
                self.requested_completion_tokens = requested
        try:
            self.completion = self.client.chat.completions.create(
                model=self.model,
                **request_options,
            )
            self.success = True
            return self.completion
        except Exception as error:
            self.failure_reason = type(error).__name__
            raise

    def mark_fallback(self, reason: str) -> None:
        self.fallback_used = True
        self.fallback_reason = reason
        self.success = True


def timed_groq_completion(
    client: Any,
    *,
    operation: str,
    model: str,
    requested_completion_tokens: int | None = None,
) -> GroqCompletionTimer:
    return GroqCompletionTimer(
        client,
        operation,
        model,
        requested_completion_tokens,
    )


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
