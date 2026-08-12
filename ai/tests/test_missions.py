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
    cursor = 0

    def create(**kwargs):
        nonlocal cursor
        request_body = json.loads(kwargs["messages"][-1]["content"])
        count = request_body["requestedMissionCount"]
        missions = payload["missions"][cursor:cursor + count]
        cursor += count
        response_payload = {**payload, "missions": missions}
        return SimpleNamespace(choices=[SimpleNamespace(
            message=SimpleNamespace(content=json.dumps(response_payload)))])

    completions = SimpleNamespace(create=create)
    return SimpleNamespace(chat=SimpleNamespace(completions=completions))


def _sequence_client(batches: list[list[dict]]):
    iterator = iter(batches)

    def create(**kwargs):
        payload = {
            "missions": next(iterator),
            "promptVersion": "personalized-mission-v1",
        }
        return SimpleNamespace(choices=[SimpleNamespace(
            message=SimpleNamespace(content=json.dumps(payload)))])

    return SimpleNamespace(chat=SimpleNamespace(
        completions=SimpleNamespace(create=create)))


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
    from app.missions.service import mission_response_schema

    missions_schema = mission_response_schema(10)["schema"]["properties"]["missions"]
    assert missions_schema["minItems"] == 10
    assert missions_schema["maxItems"] == 12


def test_requests_only_missing_count_after_cross_batch_duplicate():
    first = [_mission(i) for i in range(10)]
    second = [_mission(0)] + [_mission(i) for i in range(10, 19)]
    refill = [_mission(19)]

    result = generate_missions(
        _sequence_client([first, second, refill]),
        _request(),
        "test-model",
    )

    assert len(result.missions) == 20
    assert len({mission.title for mission in result.missions}) == 20


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
