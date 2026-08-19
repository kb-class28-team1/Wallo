import logging
import threading
from collections import deque
from functools import lru_cache
from time import monotonic
from typing import Any, Callable

from groq import Groq as GroqSdk

from app.core.ai_guard import ProviderCircuitOpenError
from app.core.config import (
    DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS,
    get_groq_api_key,
    get_groq_circuit_breaker_failure_threshold,
    get_groq_circuit_breaker_open_seconds,
    get_groq_circuit_breaker_window_seconds,
    get_groq_max_retries,
    get_groq_max_retry_delay_seconds,
    get_groq_timeout_seconds,
)
from app.core.ai_timing import current_request_id


logger = logging.getLogger("wallo_ai")
# The SDK honors provider Retry-After values up to 60 seconds. Keep that
# provider-directed window while using GROQ_MAX_RETRY_DELAY_SECONDS only for
# exponential-backoff fallback values.
MAX_PROVIDER_RETRY_AFTER_SECONDS = 60.0
Clock = Callable[[], float]


class RateLimitCircuitBreaker:
    """Process-local circuit breaker for repeated provider rate limits."""

    def __init__(
        self,
        failure_threshold: int,
        failure_window_seconds: float,
        open_seconds: float,
        *,
        clock: Clock = monotonic,
    ):
        if failure_threshold <= 0:
            raise ValueError("failure_threshold must be positive")
        if failure_window_seconds <= 0:
            raise ValueError("failure_window_seconds must be positive")
        if open_seconds <= 0:
            raise ValueError("open_seconds must be positive")
        self.failure_threshold = failure_threshold
        self.failure_window_seconds = float(failure_window_seconds)
        self.open_seconds = float(open_seconds)
        self._clock = clock
        self._lock = threading.Lock()
        self._failure_times: deque[float] = deque()
        self._open_until = 0.0

    def before_request(self) -> None:
        now = self._clock()
        with self._lock:
            if self._open_until > now:
                raise ProviderCircuitOpenError(self._open_until - now)
            if self._open_until:
                self._open_until = 0.0
                self._failure_times.clear()
            self._prune_locked(now)

    def is_open(self) -> bool:
        with self._lock:
            return self._open_until > self._clock()

    def record_rate_limit(self, retry_after_seconds: float | None = None) -> bool:
        now = self._clock()
        provider_delay = self._nonnegative_delay(retry_after_seconds)
        with self._lock:
            if self._open_until > now:
                self._open_until = max(
                    self._open_until,
                    now + provider_delay,
                )
                return False
            if self._open_until:
                self._open_until = 0.0
                self._failure_times.clear()
            self._prune_locked(now)
            self._failure_times.append(now)
            if len(self._failure_times) < self.failure_threshold:
                return False
            self._open_until = max(
                now + self.open_seconds,
                now + provider_delay,
            )
            return True

    def record_success(self) -> None:
        with self._lock:
            if self._open_until <= self._clock():
                self._open_until = 0.0
                self._failure_times.clear()

    def reset(self) -> None:
        with self._lock:
            self._open_until = 0.0
            self._failure_times.clear()

    def _prune_locked(self, now: float) -> None:
        cutoff = now - self.failure_window_seconds
        while self._failure_times and self._failure_times[0] < cutoff:
            self._failure_times.popleft()

    @staticmethod
    def _nonnegative_delay(value: float | None) -> float:
        try:
            return max(0.0, float(value or 0.0))
        except (TypeError, ValueError):
            return 0.0


_CIRCUIT_BREAKER_LOCK = threading.Lock()
_RATE_LIMIT_CIRCUIT_BREAKER: RateLimitCircuitBreaker | None = None


def get_rate_limit_circuit_breaker() -> RateLimitCircuitBreaker:
    global _RATE_LIMIT_CIRCUIT_BREAKER
    if _RATE_LIMIT_CIRCUIT_BREAKER is None:
        with _CIRCUIT_BREAKER_LOCK:
            if _RATE_LIMIT_CIRCUIT_BREAKER is None:
                _RATE_LIMIT_CIRCUIT_BREAKER = RateLimitCircuitBreaker(
                    failure_threshold=get_groq_circuit_breaker_failure_threshold(),
                    failure_window_seconds=get_groq_circuit_breaker_window_seconds(),
                    open_seconds=get_groq_circuit_breaker_open_seconds(),
                )
    return _RATE_LIMIT_CIRCUIT_BREAKER


def reset_rate_limit_circuit_breaker() -> None:
    global _RATE_LIMIT_CIRCUIT_BREAKER
    with _CIRCUIT_BREAKER_LOCK:
        _RATE_LIMIT_CIRCUIT_BREAKER = None


def _status_code(value: Any) -> int | None:
    response = getattr(value, "response", value)
    return getattr(value, "status_code", None) or getattr(
        response,
        "status_code",
        None,
    )


class Groq(GroqSdk):
    """Groq SDK 재시도 상태를 요청 스레드별로 추적하는 클라이언트."""

    def __init__(self, *args: Any, **kwargs: Any):
        self._max_retry_delay_seconds = kwargs.pop(
            "max_retry_delay_seconds",
            DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS,
        )
        super().__init__(*args, **kwargs)
        self._retry_state = threading.local()

    def reset_retry_tracking(self) -> None:
        self._retry_state.retry_count = 0
        self._retry_state.rate_limited = False
        self._retry_state.rate_limit_recorded = False

    def get_retry_count(self) -> int:
        return getattr(self._retry_state, "retry_count", 0)

    def was_rate_limited(self) -> bool:
        return getattr(self._retry_state, "rate_limited", False)

    def request(self, *args: Any, **kwargs: Any) -> Any:
        circuit_breaker = get_rate_limit_circuit_breaker()
        try:
            circuit_breaker.before_request()
        except ProviderCircuitOpenError as error:
            logger.warning(
                "[AI_CIRCUIT] requestId=%s status=blocked retryAfterSeconds=%.3f",
                current_request_id(),
                error.retry_after_seconds,
            )
            raise

        self._retry_state.rate_limit_recorded = False
        try:
            response = super().request(*args, **kwargs)
        except Exception as error:
            if (
                _status_code(error) == 429
                and not getattr(self._retry_state, "rate_limit_recorded", False)
            ):
                self._record_rate_limit(error)
            raise
        circuit_breaker.record_success()
        return response

    def _record_rate_limit(self, response_or_error: Any) -> None:
        response = getattr(response_or_error, "response", response_or_error)
        headers = getattr(response, "headers", None)
        retry_after = self._parse_retry_after_header(headers)
        circuit_breaker = get_rate_limit_circuit_breaker()
        opened = circuit_breaker.record_rate_limit(retry_after)
        if opened:
            logger.warning(
                "[AI_CIRCUIT] requestId=%s status=open failureThreshold=%s "
                "openSeconds=%.3f providerRetryAfter=%s",
                current_request_id(),
                circuit_breaker.failure_threshold,
                circuit_breaker.open_seconds,
                retry_after,
            )
        self._retry_state.rate_limit_recorded = True

    def _should_retry(self, response: Any) -> bool:
        if getattr(response, "status_code", None) == 429:
            self._retry_state.rate_limited = True
            self._record_rate_limit(response)
            headers = getattr(response, "headers", None)
            max_retry_delay_seconds = getattr(
                self,
                "_max_retry_delay_seconds",
                DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS,
            )
            retry_after = self._parse_retry_after_header(
                headers
            )
            retry_after_is_usable = (
                retry_after is not None
                and 0 <= retry_after <= MAX_PROVIDER_RETRY_AFTER_SECONDS
            )
            if retry_after is not None and not retry_after_is_usable:
                will_retry = False
            elif (
                self.get_retry_count() >= 1
                or get_rate_limit_circuit_breaker().is_open()
            ):
                will_retry = False
            else:
                will_retry = super()._should_retry(response)
            logger.warning(
                "[AI_RATE_LIMIT_RETRY] requestId=%s status=429 "
                "retryAfter=%s limitTokens=%s remainingTokens=%s "
                "resetTokens=%s willRetry=%s retryAfterDynamic=%s "
                "maxRetryDelaySeconds=%s",
                current_request_id(),
                headers.get("retry-after") if headers is not None else None,
                headers.get("x-ratelimit-limit-tokens") if headers is not None else None,
                headers.get("x-ratelimit-remaining-tokens") if headers is not None else None,
                headers.get("x-ratelimit-reset-tokens") if headers is not None else None,
                will_retry,
                retry_after_is_usable,
                max_retry_delay_seconds,
            )
            return will_retry
        return super()._should_retry(response)

    def _calculate_retry_timeout(
        self,
        remaining_retries: int,
        options: Any,
        response_headers: Any = None,
    ) -> float:
        retry_after = self._parse_retry_after_header(response_headers)
        if (
            retry_after is not None
            and 0 <= retry_after <= MAX_PROVIDER_RETRY_AFTER_SECONDS
        ):
            return retry_after
        timeout = super()._calculate_retry_timeout(
            remaining_retries,
            options,
            response_headers,
        )
        return min(timeout, self._max_retry_delay_seconds)

    def _sleep_for_retry(self, **kwargs: Any) -> None:
        response = kwargs.get("response")
        if (
            _status_code(response) == 429
            and not getattr(self._retry_state, "rate_limit_recorded", False)
        ):
            self._record_rate_limit(response)
        self._retry_state.rate_limit_recorded = False
        self._retry_state.retry_count = self.get_retry_count() + 1
        super()._sleep_for_retry(**kwargs)


@lru_cache(maxsize=4)
def _get_cached_groq_client(
    api_key: str,
    max_retries: int,
    max_retry_delay_seconds: float,
    timeout_seconds: float,
) -> Groq:
    return Groq(
        api_key=api_key,
        max_retries=max_retries,
        max_retry_delay_seconds=max_retry_delay_seconds,
        timeout=timeout_seconds,
    )


def create_groq_client(api_key: str | None = None) -> Groq:
    resolved_key = api_key or get_groq_api_key()
    if not resolved_key:
        raise RuntimeError("GROQ_API_KEY가 설정되지 않았습니다.")
    return _get_cached_groq_client(
        resolved_key,
        get_groq_max_retries(),
        get_groq_max_retry_delay_seconds(),
        get_groq_timeout_seconds(),
    )
