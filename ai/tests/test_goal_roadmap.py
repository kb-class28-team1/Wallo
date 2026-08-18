import json
from types import SimpleNamespace

import pytest

from app.agents.roadmap.generator import (
    ROADMAP_MAX_COMPLETION_TOKENS,
    generate_goal_roadmap,
)
from app.agents.roadmap.models import RoadmapGoal


class FakeCompletions:
    def __init__(self, arguments):
        self.arguments = arguments
        self.kwargs = None

    def create(self, **kwargs):
        self.kwargs = kwargs
        call = SimpleNamespace(function=SimpleNamespace(arguments=json.dumps(self.arguments)))
        return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(tool_calls=[call]))])


def goal():
    return RoadmapGoal.model_validate({
        "title": "비상금",
        "goalType": "EMERGENCY_FUND",
        "targetAmount": 10_000_000,
        "currentAmount": 2_000_000,
        "targetDate": "2027-11-30",
        "requiredMonthlyAmount": 500_000,
        "referenceDate": "2026-08-11",
    })


def valid_roadmap():
    return {
        "summary": "비상금 목표 로드맵",
        "strategy": "월별 자동이체 후 분기별로 점검한다.",
        "steps": [
            {"stepNumber": 1, "description": "자동이체를 설정한다.",
             "targetDate": "2026-08-31", "targetAmount": 2500000,
             "actionItems": ["자동이체 설정"]},
            {"stepNumber": 2, "description": "목표 금액을 확인한다.",
             "targetDate": "2027-11-30", "targetAmount": 10000000,
             "actionItems": ["잔액 확인"]},
        ],
    }


def test_generates_and_validates_structured_roadmap():
    completions = FakeCompletions(valid_roadmap())
    client = SimpleNamespace(chat=SimpleNamespace(completions=completions))

    roadmap = generate_goal_roadmap(client, goal())

    assert len(roadmap.steps) == 2
    assert roadmap.steps[-1].target_amount == 10_000_000
    assert roadmap.steps[0].title == "1단계 목표"
    assert roadmap.steps[0].monthly_contribution == 500_000
    assert completions.kwargs["max_completion_tokens"] == ROADMAP_MAX_COMPLETION_TOKENS
    assert '"motivation"' not in completions.kwargs["messages"][1]["content"]


def test_rejects_roadmap_whose_last_step_does_not_match_goal():
    payload = valid_roadmap()
    payload["steps"][-1]["targetAmount"] = 9_000_000
    client = SimpleNamespace(chat=SimpleNamespace(completions=FakeCompletions(payload)))

    with pytest.raises(ValueError, match="마지막 단계 금액"):
        generate_goal_roadmap(client, goal())
