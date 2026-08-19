import json
import logging
import math
import re
import threading
from contextlib import contextmanager
from contextvars import ContextVar
from time import perf_counter
from typing import Any

from app.core.ai_guard import (
    ApplicationAIGuard,
    ApplicationGuardError,
    ApplicationGuardLease,
)
from app.core.config import (
    get_ai_max_in_flight_requests,
    get_ai_queue_enabled,
    get_ai_queue_max_size,
    get_ai_queue_max_wait_seconds,
    get_ai_token_bucket_capacity,
    get_ai_token_bucket_refill_per_minute,
)


LOGGER = logging.getLogger("wallo_ai")
_REQUEST_ID: ContextVar[str | None] = ContextVar(
    "wallo_request_id",
    default=None,
)
_IN_FLIGHT_LOCK = threading.Lock()
_IN_FLIGHT_REQUESTS = 0
_APPLICATION_GUARD_LOCK = threading.Lock()
_APPLICATION_GUARD: ApplicationAIGuard | None = None
_TPM_USAGE_PATTERN = re.compile(
    r"tokens per minute \(TPM\):\s*Limit\s+(?P<limit>\d+),\s*"
    r"Used\s+(?P<used>\d+),\s*Requested\s+(?P<requested>\d+)",
    re.IGNORECASE,
)
_RETRY_AFTER_MESSAGE_PATTERN = re.compile(
    r"try again in\s+(?P<seconds>[0-9]+(?:\.[0-9]+)?)s",
    re.IGNORECASE,
)
_PROVIDER_DURATION_PATTERN = re.compile(
    r"(?P<value>[0-9]+(?:\.[0-9]+)?)(?P<unit>ms|s|m|h)",
    re.IGNORECASE,
)
_PROVIDER_DURATION_FACTORS = {
    "ms": 0.001,
    "s": 1.0,
    "m": 60.0,
    "h": 3600.0,
}


def normalize_request_id(request_id: str | None) -> str | None:
    if not isinstance(request_id, str):
        return None
    normalized = request_id.strip()
    return normalized[:128] or None


@contextmanager
def request_id_context(request_id: str | None):
    token = _REQUEST_ID.set(normalize_request_id(request_id))
    try:
        yield _REQUEST_ID.get()
    finally:
        _REQUEST_ID.reset(token)


def current_request_id() -> str | None:
    return _REQUEST_ID.get()


def get_application_ai_guard() -> ApplicationAIGuard:
    global _APPLICATION_GUARD
    if _APPLICATION_GUARD is None:
        with _APPLICATION_GUARD_LOCK:
            if _APPLICATION_GUARD is None:
                _APPLICATION_GUARD = ApplicationAIGuard(
                    token_capacity=get_ai_token_bucket_capacity(),
                    refill_tokens_per_minute=get_ai_token_bucket_refill_per_minute(),
                    max_in_flight_requests=get_ai_max_in_flight_requests(),
                    queue_enabled=get_ai_queue_enabled(),
                    queue_max_size=get_ai_queue_max_size(),
                    queue_max_wait_seconds=get_ai_queue_max_wait_seconds(),
                )
    return _APPLICATION_GUARD


def reset_application_ai_guard() -> None:
    global _APPLICATION_GUARD
    with _APPLICATION_GUARD_LOCK:
        _APPLICATION_GUARD = None


def _serialized_size(value: Any) -> tuple[int, int]:
    try:
        serialized = json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
            default=str,
        )
    except (TypeError, ValueError):
        serialized = str(value)
    return len(serialized), len(serialized.encode("utf-8"))


def _estimate_text_tokens(text: str) -> int:
    ascii_characters = sum(ord(character) < 128 for character in text)
    non_ascii_characters = len(text) - ascii_characters
    return math.ceil(non_ascii_characters + ascii_characters / 4)


def _estimate_serialized_tokens(value: Any) -> int:
    try:
        serialized = json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
            default=str,
        )
    except (TypeError, ValueError):
        serialized = str(value)
    return _estimate_text_tokens(serialized)


def _content_length(message: Any) -> int:
    if not isinstance(message, dict):
        return _serialized_size(message)[0]
    content = message.get("content")
    if isinstance(content, str):
        return len(content)
    return _serialized_size(content)[0]


def _request_shape(request_options: dict[str, Any]) -> dict[str, int]:
    messages = request_options.get("messages") or []
    tools = request_options.get("tools") or []
    message_chars, message_bytes = _serialized_size(messages)
    tool_chars, tool_bytes = _serialized_size(tools)
    return {
        "message_count": len(messages) if isinstance(messages, list) else 0,
        "message_content_chars": sum(_content_length(message) for message in messages),
        "message_json_chars": message_chars,
        "message_json_bytes": message_bytes,
        "tool_count": len(tools) if isinstance(tools, list) else 0,
        "tool_json_chars": tool_chars,
        "tool_json_bytes": tool_bytes,
    }


def _estimate_prompt_tokens(request_options: dict[str, Any]) -> int:
    messages = request_options.get("messages") or []
    tools = request_options.get("tools") or []
    return max(
        1,
        _estimate_serialized_tokens(messages)
        + _estimate_serialized_tokens(tools),
    )


def _begin_groq_request() -> int:
    global _IN_FLIGHT_REQUESTS
    with _IN_FLIGHT_LOCK:
        _IN_FLIGHT_REQUESTS += 1
        return _IN_FLIGHT_REQUESTS


def _end_groq_request() -> None:
    global _IN_FLIGHT_REQUESTS
    with _IN_FLIGHT_LOCK:
        _IN_FLIGHT_REQUESTS = max(0, _IN_FLIGHT_REQUESTS - 1)


def _header_value(headers: Any, name: str) -> Any:
    if headers is None:
        return None
    try:
        return headers.get(name)
    except AttributeError:
        return None


def _parse_nonnegative_number(value: Any) -> float | None:
    if isinstance(value, bool) or value is None:
        return None
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(parsed) or parsed < 0:
        return None
    return parsed


def _parse_provider_duration(value: Any) -> float | None:
    numeric = _parse_nonnegative_number(value)
    if numeric is not None:
        return numeric
    if not isinstance(value, str):
        return None

    normalized = value.strip().lower()
    if not normalized:
        return None
    matches = list(_PROVIDER_DURATION_PATTERN.finditer(normalized))
    if not matches:
        return None
    compact = re.sub(r"\s+", "", normalized)
    matched_text = "".join(match.group(0) for match in matches)
    if matched_text.lower() != compact:
        return None
    return sum(
        float(match.group("value"))
        * _PROVIDER_DURATION_FACTORS[match.group("unit").lower()]
        for match in matches
    )


def _provider_token_limits(
    headers: Any,
) -> tuple[float, float | None, float | None] | None:
    limit_tokens = _parse_nonnegative_number(
        _header_value(headers, "x-ratelimit-limit-tokens")
    )
    if limit_tokens is None or limit_tokens <= 0:
        return None
    remaining_tokens = _parse_nonnegative_number(
        _header_value(headers, "x-ratelimit-remaining-tokens")
    )
    reset_seconds = _parse_provider_duration(
        _header_value(headers, "x-ratelimit-reset-tokens")
    )
    return limit_tokens, remaining_tokens, reset_seconds


def _synchronize_provider_limits(
    guard: ApplicationAIGuard,
    headers: Any,
    *,
    operation: str,
    request_id: str | None,
    notify_waiters: bool = True,
) -> None:
    provider_limits = _provider_token_limits(headers)
    if provider_limits is None:
        return
    limit_tokens, remaining_tokens, reset_seconds = provider_limits
    synchronized = guard.synchronize_provider_limits(
        limit_tokens=limit_tokens,
        remaining_tokens=remaining_tokens,
        reset_seconds=reset_seconds,
        notify_waiters=notify_waiters,
    )
    if not synchronized:
        LOGGER.warning(
            "[AI_GUARD] operation=%s requestId=%s status=provider-sync-skipped",
            operation,
            normalize_request_id(request_id),
        )
        return
    LOGGER.info(
        "[AI_GUARD] operation=%s requestId=%s status=provider-synced "
        "providerLimitTokens=%.0f providerRemainingTokens=%s "
        "providerResetSeconds=%s effectiveCapacity=%.0f "
        "effectiveRefillPerMinute=%.0f availableTokens=%.0f",
        operation,
        normalize_request_id(request_id),
        limit_tokens,
        None if remaining_tokens is None else round(remaining_tokens),
        None if reset_seconds is None else round(reset_seconds, 3),
        guard.bucket.capacity,
        guard.bucket.refill_tokens_per_second * 60,
        guard.bucket.available_tokens,
    )


def _provider_error_details(
    error: Exception,
) -> tuple[int | None, str | None, str | None, str]:
    response = getattr(error, "response", None)
    status_code = getattr(error, "status_code", None) or getattr(
        response,
        "status_code",
        None,
    )
    body = getattr(error, "body", None)
    error_body = body.get("error") if isinstance(body, dict) else None
    if not isinstance(error_body, dict):
        error_body = {}
    message = error_body.get("message")
    return (
        status_code,
        error_body.get("type"),
        error_body.get("code"),
        message if isinstance(message, str) else "",
    )


def _log_provider_error(
    error: Exception,
    *,
    operation: str,
    request_id: str | None,
) -> None:
    response = getattr(error, "response", None)
    headers = getattr(response, "headers", None)
    status_code, error_type, error_code, provider_message = _provider_error_details(error)
    retry_after = _header_value(headers, "retry-after")
    if retry_after is None and provider_message:
        retry_match = _RETRY_AFTER_MESSAGE_PATTERN.search(provider_message)
        retry_after = retry_match.group("seconds") if retry_match else None

    quota_match = _TPM_USAGE_PATTERN.search(provider_message)
    quota_values = {
        "limit": quota_match.group("limit") if quota_match else None,
        "used": quota_match.group("used") if quota_match else None,
        "requested": quota_match.group("requested") if quota_match else None,
    }
    log_fields = (
        operation,
        normalize_request_id(request_id),
        status_code,
        error_type,
        error_code,
        retry_after,
        quota_values["limit"],
        quota_values["used"],
        quota_values["requested"],
        _header_value(headers, "x-ratelimit-limit-tokens"),
        _header_value(headers, "x-ratelimit-remaining-tokens"),
        _header_value(headers, "x-ratelimit-reset-tokens"),
        _header_value(headers, "x-ratelimit-limit-requests"),
        _header_value(headers, "x-ratelimit-remaining-requests"),
        _header_value(headers, "x-ratelimit-reset-requests"),
    )
    if status_code == 429 or error_code == "rate_limit_exceeded":
        LOGGER.warning(
            "[AI_RATE_LIMIT] operation=%s requestId=%s status=%s "
            "errorType=%s errorCode=%s retryAfter=%s "
            "providerLimitTokens=%s providerUsedTokens=%s "
            "providerRequestedTokens=%s limitTokens=%s remainingTokens=%s "
            "resetTokens=%s limitRequests=%s remainingRequests=%s "
            "resetRequests=%s",
            *log_fields,
        )
        return

    LOGGER.warning(
        "[AI_PROVIDER_ERROR] operation=%s requestId=%s status=%s "
        "errorType=%s errorCode=%s",
        operation,
        normalize_request_id(request_id),
        status_code,
        error_type,
        error_code,
    )


class GroqCompletionTimer:
    """공통 Groq completion 호출·결과 측정 컨텍스트."""

    def __init__(
        self,
        client: Any,
        operation: str,
        model: str,
        requested_completion_tokens: int | None = None,
        request_id: str | None = None,
    ):
        self.client = client
        self.operation = operation
        self.model = model
        self.requested_completion_tokens = requested_completion_tokens
        self.request_id = (
            normalize_request_id(request_id)
            if request_id is not None
            else current_request_id()
        )
        self.started_at: float | None = None
        self.completion: Any = None
        self.response_headers: Any = None
        self.failure_reason: str | None = None
        self.fallback_used = False
        self.fallback_reason: str | None = None
        self.success = False
        self.estimated_prompt_tokens: int | None = None
        self.guard_reserved_tokens: int | None = None
        self.guard_lease: ApplicationGuardLease | None = None
        self.application_guard: ApplicationAIGuard | None = None

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
            request_id=self.request_id,
            started_at=self.started_at or start_timer(),
            completion=self.completion,
            response_headers=self.response_headers,
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
        shape = _request_shape(request_options)
        self.estimated_prompt_tokens = _estimate_prompt_tokens(request_options)
        guard_reserved_tokens = max(
            1,
            self.estimated_prompt_tokens
            + (self.requested_completion_tokens or 0),
        )
        try:
            self.application_guard = get_application_ai_guard()
            self.guard_lease = self.application_guard.reserve(
                guard_reserved_tokens
            )
            self.guard_reserved_tokens = guard_reserved_tokens
        except ApplicationGuardError as error:
            self.failure_reason = type(error).__name__
            LOGGER.warning(
                "[AI_GUARD] operation=%s requestId=%s status=rejected "
                "reason=%s requestedTokens=%d availableTokens=%.0f "
                "retryAfterSeconds=%s queueWaitMs=%.0f",
                self.operation,
                normalize_request_id(self.request_id),
                type(error).__name__,
                guard_reserved_tokens,
                getattr(error, "available_tokens", None) or 0,
                getattr(error, "retry_after_seconds", None),
                getattr(error, "waited_seconds", 0.0) * 1000,
            )
            raise
        if self.guard_lease.queue_wait_seconds > 0:
            LOGGER.info(
                "[AI_GUARD] operation=%s requestId=%s status=dequeued "
                "queuePosition=%d queueWaitMs=%.0f",
                self.operation,
                normalize_request_id(self.request_id),
                self.guard_lease.queue_position,
                self.guard_lease.queue_wait_seconds * 1000,
            )
        LOGGER.info(
            "[AI_GUARD] operation=%s requestId=%s status=reserved "
            "estimatedPromptTokens=%d requestedCompletionTokens=%s "
            "reservedTokens=%d remainingTokens=%.0f inFlight=%d "
            "queuePosition=%d queueWaitMs=%.0f",
            self.operation,
            normalize_request_id(self.request_id),
            self.estimated_prompt_tokens,
            self.requested_completion_tokens,
            guard_reserved_tokens,
            self.guard_lease.available_tokens,
            self.guard_lease.in_flight_requests,
            self.guard_lease.queue_position,
            self.guard_lease.queue_wait_seconds * 1000,
        )
        in_flight = _begin_groq_request()
        LOGGER.info(
            "[AI_REQUEST] operation=%s requestId=%s model=%s inFlight=%d "
            "messageCount=%d messageContentChars=%d messageJsonChars=%d "
            "messageJsonBytes=%d toolCount=%d toolJsonChars=%d toolJsonBytes=%d "
            "estimatedPromptTokens=%d guardReservedTokens=%d "
            "requestedCompletionTokens=%s",
            self.operation,
            normalize_request_id(self.request_id),
            self.model,
            in_flight,
            shape["message_count"],
            shape["message_content_chars"],
            shape["message_json_chars"],
            shape["message_json_bytes"],
            shape["tool_count"],
            shape["tool_json_chars"],
            shape["tool_json_bytes"],
            self.estimated_prompt_tokens,
            guard_reserved_tokens,
            self.requested_completion_tokens,
        )
        try:
            completions = self.client.chat.completions
            raw_completions = getattr(completions, "with_raw_response", None)
            raw_create = getattr(raw_completions, "create", None)
            raw_response_descriptor = getattr(
                type(completions),
                "with_raw_response",
                None,
            )
            if raw_response_descriptor is not None and callable(raw_create):
                raw_response = raw_create(
                    model=self.model,
                    **request_options,
                )
                self.response_headers = getattr(raw_response, "headers", None)
                parse = getattr(raw_response, "parse", None)
                if not callable(parse):
                    raise TypeError("Groq raw response does not support parse()")
                self.completion = parse()
            else:
                self.completion = completions.create(
                    model=self.model,
                    **request_options,
                )
                self.response_headers = getattr(self.completion, "headers", None)
            self.success = True
            return self.completion
        except Exception as error:
            self.failure_reason = type(error).__name__
            error_response = getattr(error, "response", None)
            error_headers = getattr(error_response, "headers", None)
            if error_headers is not None:
                self.response_headers = error_headers
            _log_provider_error(
                error,
                operation=self.operation,
                request_id=self.request_id,
            )
            raise
        finally:
            _end_groq_request()
            if self.guard_lease is not None:
                usage = getattr(self.completion, "usage", None)
                actual_tokens = _usage_value(usage, "total_tokens")
                if not isinstance(actual_tokens, int) or isinstance(actual_tokens, bool):
                    actual_tokens = 0 if self.completion is not None else None
                self.guard_lease.release(actual_tokens, notify_waiters=False)
                LOGGER.info(
                    "[AI_GUARD] operation=%s requestId=%s status=reconciled "
                    "reservedTokens=%d actualTokens=%s remainingTokens=%.0f "
                    "inFlight=%d",
                    self.operation,
                    normalize_request_id(self.request_id),
                    self.guard_reserved_tokens or 0,
                    actual_tokens,
                    self.guard_lease.available_tokens,
                    self.guard_lease.in_flight_requests,
                )
                if self.application_guard is not None:
                    try:
                        _synchronize_provider_limits(
                            self.application_guard,
                            self.response_headers,
                            operation=self.operation,
                            request_id=self.request_id,
                            notify_waiters=False,
                        )
                    finally:
                        self.application_guard.notify_waiters()

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
    request_id: str | None = None,
) -> GroqCompletionTimer:
    return GroqCompletionTimer(
        client,
        operation,
        model,
        requested_completion_tokens,
        request_id,
    )


def start_timer() -> float:
    return perf_counter()


def log_groq_completion_timing(
    operation: str,
    model: str,
    started_at: float,
    request_id: str | None = None,
    completion: Any = None,
    response_headers: Any = None,
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
    prompt_tokens_details = _usage_value(usage, "prompt_tokens_details")
    completion_tokens_details = _usage_value(usage, "completion_tokens_details")

    LOGGER.info(
        "[AI_TIMING] operation=%s requestId=%s model=%s elapsedMs=%d "
        "promptTokens=%s completionTokens=%s totalTokens=%s "
        "cachedPromptTokens=%s reasoningTokens=%s "
        "limitTokens=%s remainingTokens=%s resetTokens=%s "
        "limitRequests=%s remainingRequests=%s resetRequests=%s "
        "requestedCompletionTokens=%s finishReason=%s responseReceived=%s "
        "retryCount=%s rateLimited=%s fallbackUsed=%s fallbackReason=%s "
        "failureReason=%s success=%s",
        operation,
        normalize_request_id(request_id),
        model,
        round((perf_counter() - started_at) * 1000),
        _usage_value(usage, "prompt_tokens"),
        _usage_value(usage, "completion_tokens"),
        _usage_value(usage, "total_tokens"),
        _usage_value(prompt_tokens_details, "cached_tokens"),
        _usage_value(completion_tokens_details, "reasoning_tokens"),
        _header_value(response_headers, "x-ratelimit-limit-tokens"),
        _header_value(response_headers, "x-ratelimit-remaining-tokens"),
        _header_value(response_headers, "x-ratelimit-reset-tokens"),
        _header_value(response_headers, "x-ratelimit-limit-requests"),
        _header_value(response_headers, "x-ratelimit-remaining-requests"),
        _header_value(response_headers, "x-ratelimit-reset-requests"),
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
