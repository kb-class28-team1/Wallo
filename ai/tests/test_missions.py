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
        "difficulty": "EASY",
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


def test_generates_exactly_thirty_unique_missions():
    result = generate_missions(
        _client({"missions": [_mission(i) for i in range(30)],
                 "promptVersion": "personalized-mission-v1"}),
        _request(),
        "test-model",
    )
    assert len(result.missions) == 30
    assert len({mission.title for mission in result.missions}) == 30


def test_rejects_less_than_thirty_missions():
    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": [_mission(i) for i in range(29)],
                     "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )


def test_rejects_semantically_identical_normalized_text():
    missions = [_mission(i) for i in range(30)]
    missions[1]["title"] = " 맞춤   미션 0 "
    missions[1]["description"] = missions[0]["description"]
    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": missions, "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )
