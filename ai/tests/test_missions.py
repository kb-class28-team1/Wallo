import json
from types import SimpleNamespace

import pytest

from app.missions.schemas import MissionGenerateRequest
from app.missions.service import InvalidMissionResponseError, generate_missions


def _mission(index: int) -> dict:
    return {
        "title": f"맞춤 미션 {index}",
        "description": f"서로 다른 행동 {index}을 실천하세요.",
        "category": "FOOD",
        "rewardPoint": 10,
        "verificationType": "MEDIA_AI",
        "verificationRule": {"minimumConfidence": 0.8},
        "evidenceGuide": "행동이 보이도록 촬영하세요.",
    }


def _client(payload: dict):
    response = SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content=json.dumps(payload)))]
    )
    completions = SimpleNamespace(create=lambda **kwargs: response)
    return SimpleNamespace(chat=SimpleNamespace(completions=completions))


def _request() -> MissionGenerateRequest:
    return MissionGenerateRequest(
        userId=7,
        analysisResultId=10,
        consumptionAnalysis={"summary": "카페 소비가 증가했습니다."},
    )


def test_generates_exactly_twenty_unique_missions():
    result = generate_missions(
        _client({"missions": [_mission(i) for i in range(20)],
                 "promptVersion": "personalized-mission-v1"}),
        _request(),
        "test-model",
    )
    assert len(result.missions) == 20
    assert len({mission.title for mission in result.missions}) == 20


def test_rejects_less_than_twenty_missions():
    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": [_mission(i) for i in range(19)],
                     "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )


def test_removes_semantically_identical_normalized_title():
    missions = [_mission(i) for i in range(20)]
    missions[1]["title"] = " 맞춤   미션 0 "
    missions[1]["description"] = "설명은 달라도 같은 제목이면 중복입니다."
    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": missions, "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )


def test_trims_extra_unique_missions_to_twenty():
    result = generate_missions(
        _client({"missions": [_mission(i) for i in range(23)],
                 "promptVersion": "personalized-mission-v1"}),
        _request(),
        "test-model",
    )

    assert len(result.missions) == 20


def test_structured_schema_accepts_small_over_generation_for_trimming():
    from app.missions.service import MISSION_RESPONSE_SCHEMA

    missions_schema = MISSION_RESPONSE_SCHEMA["schema"]["properties"]["missions"]
    assert missions_schema["minItems"] == 20
    assert missions_schema["maxItems"] == 24


def test_rejects_changed_field_names_and_missing_category():
    missions = [_mission(i) for i in range(20)]
    for mission in missions:
        mission["type"] = mission.pop("verificationType")
        mission["points"] = mission.pop("rewardPoint")
        mission.pop("category")

    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": missions, "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )


def test_rejects_reward_points_other_than_ten():
    missions = [_mission(i) for i in range(20)]
    missions[0]["rewardPoint"] = 20

    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": missions, "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )
