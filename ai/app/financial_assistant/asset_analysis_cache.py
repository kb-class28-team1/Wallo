import hashlib
import json
import threading
import time
from dataclasses import dataclass
from typing import Any


ASSET_ANALYSIS_CACHE_TTL_SECONDS = 24 * 60 * 60
ASSET_ANALYSIS_PROMPT_VERSION = "asset-analysis-v3-database-context"


@dataclass(frozen=True)
class CacheEntry:
    answer: str
    expires_at: float


_cache: dict[str, CacheEntry] = {}
_lock = threading.Lock()


def build_cache_key(tool_data: dict[str, Any], model: str) -> str | None:
    if tool_data.get("dataMode") != "database":
        return None

    calculated_metrics = tool_data.get("calculatedMetrics")
    profile = tool_data.get("profile")
    if not isinstance(calculated_metrics, dict) or not isinstance(profile, dict):
        return None

    # DB 동기화 시각과 실제 분석 데이터 fingerprint를 함께 사용한다.
    # 자산 데이터가 변경되면 asOf가 같더라도 fingerprint가 달라져 이전 답변을
    # 재사용하지 않는다. 반대로 asOf가 변경되면 데이터가 같아도 새 분석을 만든다.
    as_of = tool_data.get("asOf")
    if as_of is None:
        as_of = profile.get("as_of")

    financial_data = {
        "dataMode": "database",
        "asOf": as_of,
        "calculatedMetrics": calculated_metrics,
        "profile": profile,
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
    return f"database:{fingerprint}"


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
