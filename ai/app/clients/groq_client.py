import logging
import threading
from functools import lru_cache
from typing import Any

from groq import Groq as GroqSdk

from app.core.config import (
    DEFAULT_GROQ_MAX_RETRY_DELAY_SECONDS,
    get_groq_api_key,
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

    def get_retry_count(self) -> int:
        return getattr(self._retry_state, "retry_count", 0)

    def was_rate_limited(self) -> bool:
        return getattr(self._retry_state, "rate_limited", False)

    def _should_retry(self, response: Any) -> bool:
        if getattr(response, "status_code", None) == 429:
            self._retry_state.rate_limited = True
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
            elif self.get_retry_count() >= 1:
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
