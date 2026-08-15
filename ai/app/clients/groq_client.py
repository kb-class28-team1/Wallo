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
            retry_after = self._parse_retry_after_header(
                getattr(response, "headers", None)
            )
            if (
                retry_after is not None
                and retry_after > self._max_retry_delay_seconds
            ):
                return False
        return super()._should_retry(response)

    def _calculate_retry_timeout(
        self,
        remaining_retries: int,
        options: Any,
        response_headers: Any = None,
    ) -> float:
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
