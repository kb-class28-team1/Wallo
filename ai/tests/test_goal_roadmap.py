import json
from types import SimpleNamespace

import pytest

from app.goals.roadmap.generator import (
    ROADMAP_MAX_COMPLETION_TOKENS,
    SYSTEM_PROMPT,
    generate_goal_roadmap,
    normalize_roadmap_text,
)
from app.goals.roadmap.models import RoadmapGoal


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


def test_prompt_requires_numeric_roadmap_amount_fields():
    assert "targetAmount와 monthlyContribution은 반드시 원 단위 정수인 JSON 숫자" in SYSTEM_PROMPT
    assert "targetAmount에 3000000으로 작성" in SYSTEM_PROMPT
    assert '"3000000"' in SYSTEM_PROMPT
    assert "문자열로 작성하면 안 됩니다." in SYSTEM_PROMPT


def test_rejects_roadmap_whose_last_step_does_not_match_goal():
    payload = valid_roadmap()
    payload["steps"][-1]["targetAmount"] = 9_000_000
    client = SimpleNamespace(chat=SimpleNamespace(completions=FakeCompletions(payload)))

    with pytest.raises(ValueError, match="마지막 단계 금액"):
        generate_goal_roadmap(client, goal())


def test_normalizes_million_shorthand_in_generated_roadmap_text():
    assert normalize_roadmap_text("현재 3M에 1M 추가") == "현재 3백만원에 1백만원 추가"
    assert normalize_roadmap_text("3M원과 1.5m") == "3백만원과 1.5백만원"
