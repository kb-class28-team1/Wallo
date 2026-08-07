import hashlib
import json
import threading
import time
from dataclasses import dataclass
from typing import Any


ASSET_ANALYSIS_CACHE_TTL_SECONDS = 24 * 60 * 60
ASSET_ANALYSIS_PROMPT_VERSION = "asset-analysis-v1"


@dataclass(frozen=True)
class CacheEntry:
    answer: str
    expires_at: float


_cache: dict[str, CacheEntry] = {}
_lock = threading.Lock()


def build_cache_key(tool_data: dict[str, Any], model: str) -> str | None:
    profile_id = tool_data.get("profileId")
    if profile_id is None:
        return None

    # 사용자가 같은 자산분석 요청을 다르게 표현해도 같은 보고서를 재사용한다.
    financial_data = {
        "profileId": profile_id,
        "calculatedMetrics": tool_data.get("calculatedMetrics"),
        "profile": tool_data.get("profile"),
        "model": model,
        "promptVersion": ASSET_ANALYSIS_PROMPT_VERSION,
    }
    serialized = json.dumps(
        financial_data,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    fingerprint = hashlib.sha256(serialized.encode("utf-8")).hexdigest()
    return f"{profile_id}:{fingerprint}"


def get_cached_answer(cache_key: str | None) -> str | None:
    if cache_key is None:
        return None
    now = time.monotonic()
    with _lock:
        entry = _cache.get(cache_key)
        if entry is None:
            return None
        if entry.expires_at <= now:
            _cache.pop(cache_key, None)
            return None
        return entry.answer


def cache_answer(cache_key: str | None, answer: str) -> None:
    if cache_key is None or not answer.strip():
        return
    with _lock:
        _cache[cache_key] = CacheEntry(
            answer=answer,
            expires_at=time.monotonic() + ASSET_ANALYSIS_CACHE_TTL_SECONDS,
        )


def clear_asset_analysis_cache() -> None:
    with _lock:
        _cache.clear()
