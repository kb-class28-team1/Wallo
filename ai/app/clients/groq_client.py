import threading
from functools import lru_cache
from typing import Any

from groq import Groq as GroqSdk

from app.core.config import (
    get_groq_api_key,
    get_groq_max_retries,
    get_groq_timeout_seconds,
)


class Groq(GroqSdk):
    """Groq SDK 재시도 상태를 요청 스레드별로 추적하는 클라이언트."""

    def __init__(self, *args: Any, **kwargs: Any):
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
        return super()._should_retry(response)

    def _sleep_for_retry(self, **kwargs: Any) -> None:
        self._retry_state.retry_count = self.get_retry_count() + 1
        super()._sleep_for_retry(**kwargs)


@lru_cache(maxsize=4)
def _get_cached_groq_client(
    api_key: str,
    max_retries: int,
    timeout_seconds: float,
) -> Groq:
    return Groq(
        api_key=api_key,
        max_retries=max_retries,
        timeout=timeout_seconds,
    )


def create_groq_client(api_key: str | None = None) -> Groq:
    resolved_key = api_key or get_groq_api_key()
    if not resolved_key:
        raise RuntimeError("GROQ_API_KEY가 설정되지 않았습니다.")
    return _get_cached_groq_client(
        resolved_key,
        get_groq_max_retries(),
        get_groq_timeout_seconds(),
    )
