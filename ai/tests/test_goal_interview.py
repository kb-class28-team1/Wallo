"""목표 정보 추출, 꼬리질문, 자산 개인화 및 월 필요액 계산 테스트."""

import json
from datetime import date
from types import SimpleNamespace
from unittest.mock import Mock

import httpx
import pytest
from groq import BadRequestError

from app.agents.goal.extractor import (
    GoalExtractionError,
    GoalExtractor,
    explicit_target_date,
)
from app.agents.goal.models import (
    FeasibilityStatus,
    GoalDraft,
    GoalExtraction,
    GoalField,
    GoalPriority,
    GoalType,
    InterviewState,
)
from app.agents.goal.prompts import EXTRACTION_SYSTEM_PROMPT
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


def tool_completion(arguments):
    return SimpleNamespace(
        choices=[SimpleNamespace(message=SimpleNamespace(
            content=None,
            tool_calls=[SimpleNamespace(
                function=SimpleNamespace(arguments=arguments),
            )],
        ))],
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


def test_extraction_prompt_requires_only_goal_tool_call():
    assert "extract_financial_goal" in EXTRACTION_SYSTEM_PROMPT
    assert "JSON 객체만 반환" not in EXTRACTION_SYSTEM_PROMPT
    assert "함수 호출의 arguments" in EXTRACTION_SYSTEM_PROMPT
    assert "다른 함수나 도구를 호출하지 마세요" in EXTRACTION_SYSTEM_PROMPT


def test_extractor_parses_structured_goal_fields_and_sends_current_draft():
    client = SimpleNamespace(
        chat=SimpleNamespace(
            completions=SimpleNamespace(
                create=lambda **kwargs: tool_completion(json.dumps({
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


def test_goal_tool_schema_allows_null_for_optional_fields():
    properties = GoalExtractor.TOOL_SCHEMA["function"]["parameters"]["properties"]
    nullable_fields = {
        "title",
        "goal_type",
        "target_amount",
        "target_date",
        "motivation",
        "priority",
        "current_amount",
        "next_field",
        "next_question",
    }

    for field in nullable_fields:
        schema = properties[field]
        assert {"type": "null"} in schema["anyOf"]


def test_extractor_accepts_partial_goal_with_null_optional_fields():
    client = SimpleNamespace(
        chat=SimpleNamespace(
            completions=SimpleNamespace(
                create=lambda **kwargs: tool_completion(json.dumps({
                    "title": "비상금 마련",
                    "goal_type": "EMERGENCY_FUND",
                    "target_amount": None,
                    "target_date": None,
                    "motivation": None,
                    "priority": None,
                    "current_amount": None,
                    "assumptions": [],
                    "next_field": "targetAmount",
                    "next_question": "목표 금액을 알려주실 수 있나요?",
                }, ensure_ascii=False))
            )
        )
    )

    result = GoalExtractor(client).extract(
        "비상금을 마련하고 싶어",
        GoalDraft(),
        date(2026, 8, 6),
    )

    assert result.title == "비상금 마련"
    assert result.goal_type == GoalType.EMERGENCY_FUND
    assert result.target_amount is None
    assert result.next_field == GoalField.TARGET_AMOUNT
    assert result.next_question == "목표 금액을 알려주실 수 있나요?"


def test_extractor_uses_explicit_facts_when_model_response_is_invalid():
    client = SimpleNamespace(
        chat=SimpleNamespace(
            completions=SimpleNamespace(
                create=lambda **kwargs: tool_completion('{"goal_type":"INVALID"}')
            )
        )
    )

    result = GoalExtractor(client).extract(
        "여행 가고 싶어",
        GoalDraft(),
        date(2026, 8, 6),
    )

    assert result.goal_type == GoalType.TRAVEL


def test_extractor_uses_fallback_without_repeating_failed_provider_call():
    client = Mock()
    response = httpx.Response(
        400,
        request=httpx.Request("POST", "https://api.groq.com/openai/v1/chat/completions"),
    )
    json_error = BadRequestError(
        "Failed to validate JSON",
        response=response,
        body={"error": {"code": "json_validate_failed"}},
    )
    client.chat.completions.create.side_effect = json_error

    result = GoalExtractor(client).extract(
        "1300만 원 정도 필요해",
        GoalDraft(title="유럽 여행 자금", goal_type=GoalType.TRAVEL),
        date(2026, 8, 6),
    )

    assert result.target_amount == 13_000_000
    assert client.chat.completions.create.call_count == 1
    assert client.chat.completions.create.call_args.kwargs["temperature"] == 0
    assert client.chat.completions.create.call_args.kwargs["reasoning_effort"] == "low"
    assert client.chat.completions.create.call_args.kwargs["tool_choice"] == {
        "type": "function",
        "function": {"name": "extract_financial_goal"},
    }


def test_emergency_goal_gets_default_title_and_skips_redundant_title_question():
    extraction = GoalExtraction(
        goal_type=GoalType.EMERGENCY_FUND,
        target_amount=10_000_000,
        target_date=date(2027, 11, 1),
        motivation="비상 상황 대비",
        current_amount=2_000_000,
    )

    result = GoalInterviewService(StubExtractor(extraction)).process(
        "내년 11월까지 비상금 1000만 원을 모으고 싶고, "
        "현재 200만 원이 있으며 매달 50만 원씩 저축할 수 있어",
        reference_date=date(2026, 8, 6),
    )

    assert result.draft.title == "비상금 마련"
    assert result.draft.goal_type == GoalType.EMERGENCY_FUND
    assert result.draft.missing_fields == []
    assert result.draft.state == InterviewState.CONFIRMATION
    assert result.feasibility is not None
    assert result.feasibility.status == FeasibilityStatus.CALCULATED
    assert result.feasibility.required_monthly_amount == 533_334
    assert "이 계획으로 확정" in result.next_question


def test_explicit_emergency_facts_survive_model_extraction_failure():
    client = Mock()
    client.chat.completions.create.side_effect = [
        tool_completion("invalid-json"),
        tool_completion("invalid-json"),
    ]
    message = (
        "내년 11월까지 비상금 1000만 원을 모으고 싶고, "
        "현재 200만 원이 있으며 매달 50만 원씩 저축할 수 있어"
    )

    extraction = GoalExtractor(client).extract(
        message,
        GoalDraft(),
        date(2026, 8, 6),
    )

    assert extraction.goal_type == GoalType.EMERGENCY_FUND
    assert extraction.target_amount == 10_000_000
    assert extraction.target_date == date(2027, 11, 30)
    assert extraction.current_amount == 2_000_000
    assert extraction.motivation == "비상 상황에 대비하기 위해"


def test_understands_common_relative_and_explicit_deadlines():
    reference = date(2026, 8, 6)

    assert explicit_target_date("2027년 11월까지", reference)[0] == date(2027, 11, 30)
    assert explicit_target_date("내년 말까지", reference)[0] == date(2027, 12, 31)
    assert explicit_target_date("1년 뒤까지", reference)[0] == date(2027, 8, 6)
    assert explicit_target_date("1년 정도 모을래", reference)[0] == date(2027, 8, 6)
    assert explicit_target_date("18개월 후", reference)[0] == date(2028, 2, 6)


def test_interview_preserves_draft_when_json_extraction_keeps_failing():
    extractor = Mock()
    extractor.extract.side_effect = GoalExtractionError("추출 실패")
    draft = GoalDraft(
        title="유럽 여행 자금",
        goal_type=GoalType.TRAVEL,
        target_amount=13_000_000,
        target_date=date(2026, 11, 1),
    )

    result = GoalInterviewService(extractor).process(
        "그게 무슨 말이야",
        draft,
        financial_context(),
        date(2026, 8, 6),
    )

    assert result.draft.title == "유럽 여행 자금"
    assert result.draft.target_amount == 13_000_000
    assert result.draft.missing_fields[0] == GoalField.CURRENT_AMOUNT
    assert "정확히 이해하지 못했어요" in result.next_question


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
    draft = complete_draft(current_amount=None)
    extractor = StubExtractor(GoalExtraction())

    result = GoalInterviewService(extractor).process(
        "아직 준비금은 말하지 않았어",
        draft,
        financial_context(),
        date(2026, 8, 6),
    )

    assert result.draft.state == InterviewState.ACTIVE
    assert result.draft.missing_fields == [
        GoalField.CURRENT_AMOUNT,
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
    assert result.feasibility.status == FeasibilityStatus.CALCULATED
    assert result.feasibility.remaining_amount == 6_000_000
    assert result.feasibility.remaining_months == 10
    assert result.feasibility.required_monthly_amount == 600_000
    assert "600,000원" in result.next_question


def test_goal_does_not_ask_for_monthly_contribution():
    result = GoalInterviewService(StubExtractor(GoalExtraction())).process(
        "현재 내용으로 계산해줘",
        complete_draft(),
        financial_context(),
        date(2026, 8, 6),
    )

    assert result.draft.state == InterviewState.CONFIRMATION
    assert result.feasibility is not None
    assert result.feasibility.status == FeasibilityStatus.CALCULATED
    assert GoalField.CURRENT_AMOUNT not in result.draft.missing_fields
    assert "월 납입액" not in result.next_question
    assert "이 계획으로 확정" in result.next_question


def test_feasibility_handles_missing_already_achieved_and_calculated_goals():
    insufficient = calculate_feasibility(GoalDraft(target_amount=1_000_000))
    achieved = calculate_feasibility(
        complete_draft(current_amount=8_000_000),
        date(2026, 8, 6),
    )
    calculated = calculate_feasibility(complete_draft(), date(2026, 8, 6))

    assert insufficient.status == FeasibilityStatus.INSUFFICIENT_INFORMATION
    assert achieved.status == FeasibilityStatus.ALREADY_ACHIEVED
    assert calculated.status == FeasibilityStatus.CALCULATED


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
        GoalField.CURRENT_AMOUNT,
    ]


def test_model_can_choose_a_more_natural_next_question_than_fallback_order():
    extraction = GoalExtraction(
        goal_type=GoalType.TRAVEL,
        target_amount=8_000_000,
        next_field=GoalField.CURRENT_AMOUNT,
        next_question="여행을 위해 이미 준비해 둔 돈이 있나요?",
    )

    result = GoalInterviewService(StubExtractor(extraction)).process(
        "유럽 여행 자금 800만 원을 모으고 싶어",
        reference_date=date(2026, 8, 6),
    )

    assert GoalField.TARGET_DATE in result.draft.missing_fields
    assert GoalField.CURRENT_AMOUNT in result.draft.missing_fields
    assert result.next_question == "여행을 위해 이미 준비해 둔 돈이 있나요?"
