"""금융 목표 인터뷰 내부 상태 모델 테스트."""

from datetime import date

import pytest
from pydantic import ValidationError

from app.goals.interview.models import (
    GoalDraft,
    GoalField,
    GoalPriority,
    GoalType,
    InterviewState,
)


def test_goal_draft_starts_as_empty_discovery():
    draft = GoalDraft()

    assert draft.state == InterviewState.DISCOVERY
    assert draft.title is None
    assert draft.missing_fields == []
    assert draft.assumptions == []
    assert draft.confirmed is False


def test_goal_draft_accepts_structured_interview_state():
    draft = GoalDraft(
        state=InterviewState.FINANCIAL_CHECK,
        title=" 유럽 여행 자금 ",
        goal_type=GoalType.TRAVEL,
        target_amount=8_000_000,
        target_date=date(2027, 6, 1),
        motivation=" 취업 3주년 기념 여행 ",
        priority=GoalPriority.HIGH,
        current_amount=2_000_000,
        missing_fields=[GoalField.CURRENT_AMOUNT],
        assumptions=[" 목표일은 2027년 6월 1일로 임시 해석함 "],
    )

    assert draft.title == "유럽 여행 자금"
    assert draft.motivation == "취업 3주년 기념 여행"
    assert draft.assumptions == ["목표일은 2027년 6월 1일로 임시 해석함"]


@pytest.mark.parametrize(
    ("field_name", "invalid_value"),
    [
        ("target_amount", 0),
        ("target_amount", -1),
        ("current_amount", -1),
    ],
)
def test_goal_draft_rejects_invalid_money_values(field_name, invalid_value):
    with pytest.raises(ValidationError):
        GoalDraft(**{field_name: invalid_value})


def test_goal_draft_rejects_duplicate_missing_fields():
    with pytest.raises(ValidationError):
        GoalDraft(
            missing_fields=[GoalField.TARGET_AMOUNT, GoalField.TARGET_AMOUNT],
        )


def test_goal_draft_rejects_unknown_fields():
    with pytest.raises(ValidationError):
        GoalDraft(user_id=1)


def test_goal_draft_validates_assignment():
    draft = GoalDraft(target_amount=1_000_000)

    with pytest.raises(ValidationError):
        draft.target_amount = 0
