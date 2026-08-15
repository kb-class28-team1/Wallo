from functools import lru_cache

from groq import Groq

from app.core.config import (
    get_groq_api_key,
    get_groq_max_retries,
    get_groq_timeout_seconds,
)


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
