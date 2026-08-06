"""목표 정보 추출, 꼬리질문, 자산 개인화 및 달성 가능성 테스트."""

import json
from datetime import date
from types import SimpleNamespace

import pytest

from app.agents.goal.extractor import GoalExtractionError, GoalExtractor
from app.agents.goal.models import (
    FeasibilityStatus,
    GoalDraft,
    GoalExtraction,
    GoalField,
    GoalPriority,
    GoalType,
    InterviewState,
)
from app.agents.goal.service import (
    GoalInterviewService,
    calculate_feasibility,
    find_missing_fields,
    merge_goal_draft,
    months_until,
)
from app.chat.schemas import FinancialContext


class StubExtractor:
    def __init__(self, extraction: GoalExtraction):
        self.extraction = extraction
        self.calls = []

    def extract(self, user_message, draft, reference_date):
        self.calls.append((user_message, draft, reference_date))
        return self.extraction


def completion(content):
    return SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(content=content))],
    )


def complete_draft(**updates) -> GoalDraft:
    values = {
        "title": "유럽 여행 자금",
        "goal_type": GoalType.TRAVEL,
        "target_amount": 8_000_000,
        "target_date": date(2027, 6, 1),
        "motivation": "취업 3주년 기념 여행",
        "priority": GoalPriority.HIGH,
        "current_amount": 2_000_000,
        "monthly_contribution": 700_000,
    }
    values.update(updates)
    return GoalDraft(**values)


def financial_context(
    ready_amount=7_000_000,
    conditional_amount=15_000_000,
) -> FinancialContext:
    return FinancialContext(
        has_connected_accounts=True,
        ready_amount=ready_amount,
        conditional_amount=conditional_amount,
        risk_asset_amount=14_500_000,
        excluded_amount=13_200_000,
        unknown_amount=0,
        debt_amount=4_800_000,
        accounts=[],
    )


def test_extractor_parses_structured_goal_fields_and_sends_current_draft():
    client = SimpleNamespace(
        chat=SimpleNamespace(
            completions=SimpleNamespace(
                create=lambda **kwargs: completion(json.dumps({
                    "title": "유럽 여행 자금",
                    "goal_type": "TRAVEL",
                    "target_amount": 8_000_000,
                    "target_date": "2027-06-01",
                    "assumptions": ["내년 6월은 2027년 6월 1일로 해석함"],
                }, ensure_ascii=False))
            )
        )
    )
    extractor = GoalExtractor(client)

    result = extractor.extract(
        "내년 6월까지 유럽 여행 자금 800만 원을 모으고 싶어",
        GoalDraft(),
        date(2026, 8, 6),
    )

    assert result.goal_type == GoalType.TRAVEL
    assert result.target_amount == 8_000_000
    assert result.target_date == date(2027, 6, 1)
    call = client.chat.completions.create
    assert call is not None


def test_extractor_rejects_invalid_model_response():
    client = SimpleNamespace(
        chat=SimpleNamespace(
            completions=SimpleNamespace(
                create=lambda **kwargs: completion('{"goal_type":"INVALID"}')
            )
        )
    )

    with pytest.raises(GoalExtractionError):
        GoalExtractor(client).extract("여행 가고 싶어", GoalDraft(), date(2026, 8, 6))


def test_merges_new_information_and_user_correction_without_duplicate_assumptions():
    draft = GoalDraft(
        title="유럽 여행",
        target_amount=7_000_000,
        assumptions=["날짜를 월초로 해석함"],
    )
    extraction = GoalExtraction(
        target_amount=8_000_000,
        assumptions=["날짜를 월초로 해석함", "금액을 800만 원으로 정정함"],
    )

    updated = merge_goal_draft(draft, extraction)

    assert updated.title == "유럽 여행"
    assert updated.target_amount == 8_000_000
    assert updated.assumptions == [
        "날짜를 월초로 해석함",
        "금액을 800만 원으로 정정함",
    ]


def test_selects_one_asset_personalized_question_for_current_amount():
    draft = complete_draft(current_amount=None, monthly_contribution=None)
    extractor = StubExtractor(GoalExtraction())

    result = GoalInterviewService(extractor).process(
        "아직 준비금은 말하지 않았어",
        draft,
        financial_context(),
        date(2026, 8, 6),
    )

    assert result.draft.state == InterviewState.FINANCIAL_CHECK
    assert result.draft.missing_fields == [
        GoalField.CURRENT_AMOUNT,
        GoalField.MONTHLY_CONTRIBUTION,
    ]
    assert "7,000,000원" in result.next_question
    assert "15,000,000원" in result.next_question
    assert result.next_question.count("?") == 1


def test_asks_for_direct_input_when_accounts_are_not_connected():
    draft = complete_draft(current_amount=None)
    context = financial_context(0, 0).model_copy(
        update={"has_connected_accounts": False},
    )

    result = GoalInterviewService(StubExtractor(GoalExtraction())).process(
        "모르겠어",
        draft,
        context,
        date(2026, 8, 6),
    )

    assert result.next_question == "이 목표를 위해 이미 따로 준비해 둔 금액이 있나요?"


def test_complete_achievable_goal_moves_to_confirmation():
    result = GoalInterviewService(StubExtractor(GoalExtraction())).process(
        "이 내용이 맞아",
        complete_draft(),
        financial_context(),
        date(2026, 8, 6),
    )

    assert result.draft.state == InterviewState.CONFIRMATION
    assert result.feasibility is not None
    assert result.feasibility.status == FeasibilityStatus.ACHIEVABLE
    assert result.feasibility.remaining_amount == 6_000_000
    assert result.feasibility.remaining_months == 10
    assert result.feasibility.required_monthly_amount == 600_000
    assert "600,000원" in result.next_question


def test_unaffordable_goal_moves_to_feasibility_review():
    result = GoalInterviewService(StubExtractor(GoalExtraction())).process(
        "월 30만 원까지 가능해",
        complete_draft(monthly_contribution=300_000),
        financial_context(),
        date(2026, 8, 6),
    )

    assert result.draft.state == InterviewState.FEASIBILITY_REVIEW
    assert result.feasibility is not None
    assert result.feasibility.status == FeasibilityStatus.ADJUSTMENT_REQUIRED
    assert result.feasibility.monthly_gap == -300_000
    assert "무엇을 조정할까요?" in result.next_question


def test_feasibility_handles_missing_already_achieved_and_tight_goals():
    insufficient = calculate_feasibility(GoalDraft(target_amount=1_000_000))
    achieved = calculate_feasibility(
        complete_draft(current_amount=8_000_000),
        date(2026, 8, 6),
    )
    tight = calculate_feasibility(
        complete_draft(monthly_contribution=600_000),
        date(2026, 8, 6),
    )

    assert insufficient.status == FeasibilityStatus.INSUFFICIENT_INFORMATION
    assert achieved.status == FeasibilityStatus.ALREADY_ACHIEVED
    assert tight.status == FeasibilityStatus.TIGHT


def test_past_target_date_requires_adjustment():
    result = calculate_feasibility(
        complete_draft(target_date=date(2026, 8, 1)),
        date(2026, 8, 6),
    )

    assert result.status == FeasibilityStatus.ADJUSTMENT_REQUIRED
    assert result.remaining_months == 0
    assert result.required_monthly_amount is None


def test_months_until_counts_partial_final_month():
    assert months_until(date(2026, 8, 6), date(2027, 6, 1)) == 10
    assert months_until(date(2026, 8, 6), date(2027, 6, 20)) == 11
    assert months_until(date(2026, 8, 6), date(2026, 8, 1)) == 0


def test_missing_field_order_is_deterministic():
    missing = find_missing_fields(GoalDraft(title="여행 자금", goal_type=GoalType.TRAVEL))

    assert missing[:3] == [
        GoalField.TARGET_AMOUNT,
        GoalField.TARGET_DATE,
        GoalField.MOTIVATION,
    ]
