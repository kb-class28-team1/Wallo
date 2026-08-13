"""금융 리포트 개인화에 사용하는 고정 사용자 프로필 로더."""

import json
from pathlib import Path
from typing import Any


REPORT_PROFILE_ID = 7
PROFILE_DATA_FILE = (
    Path(__file__).resolve().parents[2]
    / "data"
    / "processed"
    / "selected_asset_profiles.json"
)


def load_report_profile() -> dict[str, Any]:
    """매 리포트 생성 시 선별 프로필 파일에서 profile_id=7을 새로 읽는다."""
    try:
        document = json.loads(PROFILE_DATA_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError("금융 리포트 사용자 프로필을 읽지 못했습니다.") from error

    profiles = document.get("profiles")
    if not isinstance(profiles, list):
        raise RuntimeError("금융 리포트 사용자 프로필 목록이 올바르지 않습니다.")

    for profile in profiles:
        if isinstance(profile, dict) and profile.get("profile_id") == REPORT_PROFILE_ID:
            return profile

    raise RuntimeError(f"금융 리포트 사용자 프로필을 찾지 못했습니다: {REPORT_PROFILE_ID}")


def build_report_profile_context(profile: dict[str, Any]) -> dict[str, Any]:
    """LLM 개인화에 필요한 사실만 추려 입력 길이와 불필요한 추론 여지를 줄인다."""
    return {
        "profile_id": profile.get("profile_id"),
        "nickname": profile.get("nickname"),
        "employment": profile.get("employment"),
        "housing_type": profile.get("housing_type"),
        "income": profile.get("income"),
        "cashflow": profile.get("cashflow"),
        "assets": profile.get("assets"),
        "total_debt_krw": profile.get("total_debt_krw"),
    }
