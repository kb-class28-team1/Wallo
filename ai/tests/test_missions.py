import json
from types import SimpleNamespace

import pytest

from app.missions.schemas import MissionGenerateRequest
from app.missions.service import InvalidMissionResponseError, generate_missions
from app.missions.prompts import MISSION_GENERATION_INSTRUCTIONS, build_mission_input


def _mission(index: int) -> dict:
    return {
        "title": f"맞춤 미션 {index}",
        "description": f"서로 다른 행동 {index}을 실천하세요.",
        "category": "FOOD",
        "rewardPoint": 10,
        "verificationType": "MEDIA_AI",
        "verificationRule": {"description": "행동 수행 장면인지 확인"},
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


def _request(count: int = 3) -> MissionGenerateRequest:
    return MissionGenerateRequest(
        userId=7,
        analysisResultId=10,
        consumptionAnalysis={"summary": "카페 소비가 증가했습니다."},
        requestedMissionCount=count,
        excludedTitles=[],
    )


def test_prompt_output_contract_matches_structured_schema():
    prompt_input = json.loads(build_mission_input(_request(), requested_count=7))
    contract = prompt_input["outputContract"]

    assert contract["topLevelFields"] == ["missions", "promptVersion"]
    assert contract["promptVersion"] == "personalized-mission-v1"
    assert contract["rewardPoint"] == 10
    assert contract["verificationTypes"] == [
        "MEDIA_AI", "TRANSACTION", "HYBRID", "SELF_CHECK", "MANUAL",
    ]
    assert contract["additionalFieldsAllowed"] is False
    assert '"verificationRule":{"description":' in MISSION_GENERATION_INSTRUCTIONS
    assert "JSON 이외의 설명" in MISSION_GENERATION_INSTRUCTIONS


def test_structured_schema_uses_only_single_concrete_field_types():
    from app.missions.service import mission_response_schema

    schema = mission_response_schema(10)["schema"]
    mission = schema["properties"]["missions"]["items"]

    assert mission["properties"]["verificationRule"]["type"] == "object"
    assert "anyOf" not in mission["properties"]["verificationRule"]
    assert mission["properties"]["evidenceGuide"]["type"] == "string"
    assert schema["properties"]["promptVersion"]["const"] == (
        "personalized-mission-v1"
    )


def test_generates_requested_daily_missions():
    result = generate_missions(
        _client({"missions": [_mission(i) for i in range(3)],
                 "promptVersion": "personalized-mission-v1"}),
        _request(),
        "test-model",
    )
    assert len(result.missions) == 3
    assert len({mission.title for mission in result.missions}) == 3


def test_rejects_less_than_requested_daily_missions():
    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": [_mission(i) for i in range(2)],
                     "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )


def test_removes_semantically_identical_normalized_title():
    missions = [_mission(i) for i in range(3)]
    missions[1]["title"] = " 맞춤   미션 0 "
    missions[1]["description"] = "설명은 달라도 같은 제목이면 중복입니다."
    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": missions, "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )


def test_structured_schema_accepts_small_over_generation_for_trimming():
    from app.missions.service import mission_response_schema

    missions_schema = mission_response_schema(3)["schema"]["properties"]["missions"]
    assert missions_schema["minItems"] == 1
    assert missions_schema["maxItems"] == 5


def test_rejects_changed_field_names_and_missing_category():
    missions = [_mission(i) for i in range(3)]
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
    missions = [_mission(i) for i in range(3)]
    missions[0]["rewardPoint"] = 20

    with pytest.raises(InvalidMissionResponseError):
        generate_missions(
            _client({"missions": missions, "promptVersion": "personalized-mission-v1"}),
            _request(),
            "test-model",
        )
