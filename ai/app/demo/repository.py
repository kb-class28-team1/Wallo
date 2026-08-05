import json
import logging
from functools import lru_cache
from pathlib import Path
from typing import Any

logger = logging.getLogger("wallo_ai")
DATA_FILE = Path(__file__).resolve().parents[2] / "data" / "processed" / "money_log_agent_inputs.json"


@lru_cache(maxsize=1)
def load_demo_profiles() -> dict[int, dict[str, Any]]:
    try:
        rows = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        logger.exception("Failed to load demo asset profiles")
        raise RuntimeError("가상 사용자 데이터를 읽지 못했습니다.") from error

    profiles: dict[int, dict[str, Any]] = {}
    for row in rows:
        profile = row.get("asset_analysis_input")
        if isinstance(profile, dict) and isinstance(profile.get("profile_id"), int):
            profiles[profile["profile_id"]] = profile
    return profiles
