from datetime import date
from math import ceil

from app.agents.goal.context import FinancialContext
from app.agents.goal.extractor import GoalExtractionError, GoalExtractor
from app.agents.goal.models import (
    FeasibilityResult,
    FeasibilityStatus,
    GoalDraft,
    GoalExtraction,
    GoalField,
    GoalInterviewResult,
    GoalType,
    InterviewState,
)


PLANNING_FIELD_ORDER = (
    GoalField.TARGET_AMOUNT,
    GoalField.TARGET_DATE,
    GoalField.CURRENT_AMOUNT,
    GoalField.MONTHLY_CONTRIBUTION,
)

FIELD_ATTRIBUTE = {
    GoalField.TITLE: "title",
    GoalField.GOAL_TYPE: "goal_type",
    GoalField.TARGET_AMOUNT: "target_amount",
    GoalField.TARGET_DATE: "target_date",
    GoalField.MOTIVATION: "motivation",
    GoalField.PRIORITY: "priority",
    GoalField.CURRENT_AMOUNT: "current_amount",
    GoalField.MONTHLY_CONTRIBUTION: "monthly_contribution",
}

DEFAULT_TITLE_BY_GOAL_TYPE = {
    GoalType.EMERGENCY_FUND: "비상금 마련",
    GoalType.TRAVEL: "여행 자금 마련",
    GoalType.HOUSING: "주거 자금 마련",
    GoalType.EDUCATION: "교육 자금 마련",
    GoalType.MARRIAGE: "결혼 자금 마련",
    GoalType.DEBT_REPAYMENT: "부채 상환",
    GoalType.INVESTMENT: "투자 자금 마련",
    GoalType.RETIREMENT: "은퇴 자금 마련",
    GoalType.PURCHASE: "구매 자금 마련",
    GoalType.OTHER: "재무 목표 마련",
}


class GoalInterviewService:
    def __init__(self, extractor: GoalExtractor):
        self.extractor = extractor

    def process(
        self,
        user_message: str,
        draft: GoalDraft | None = None,
        financial_context: FinancialContext | None = None,
        reference_date: date | None = None,
    ) -> GoalInterviewResult:
        today = reference_date or date.today()
        current_draft = draft or GoalDraft()
        try:
            extraction = self.extractor.extract(user_message, current_draft, today)
        except GoalExtractionError:
            missing_fields = find_missing_fields(current_draft)
            recovered = GoalDraft.model_validate({
                **current_draft.model_dump(),
                "missing_fields": missing_fields,
                "state": interview_state_for(missing_fields),
            })
            next_question = (
                question_for(missing_fields[0], financial_context)
                if missing_fields
                else "입력하신 내용을 이해하지 못했습니다. 변경할 내용을 다시 알려주세요."
            )
            return GoalInterviewResult(
                draft=recovered,
                next_question=(
                    "답변을 정확히 이해하지 못했어요. " + next_question
                ),
            )
        updated = merge_goal_draft(current_draft, extraction)
        updated = apply_goal_defaults(updated)
        missing_fields = find_missing_fields(updated)
        updated = GoalDraft.model_validate({
            **updated.model_dump(),
            "missing_fields": missing_fields,
            "state": interview_state_for(missing_fields),
        })

        if missing_fields:
            next_field = choose_next_field(missing_fields, extraction)
            return GoalInterviewResult(
                draft=updated,
                next_question=next_question_for(
                    next_field,
                    extraction,
                    updated,
                    financial_context,
                ),
            )

        feasibility = calculate_feasibility(updated, today)
        state = (
            InterviewState.REVIEW
            if feasibility.status == FeasibilityStatus.ADJUSTMENT_REQUIRED
            else InterviewState.CONFIRMATION
        )
        updated = GoalDraft.model_validate({
            **updated.model_dump(),
            "state": state,
        })
        return GoalInterviewResult(
            draft=updated,
            next_question=feasibility_question(updated, feasibility),
            feasibility=feasibility,
        )


def merge_goal_draft(draft: GoalDraft, extraction: GoalExtraction) -> GoalDraft:
    updates = extraction.model_dump(
        exclude_none=True,
        exclude={"assumptions", "next_field", "next_question"},
    )
    assumptions = list(dict.fromkeys([
        *draft.assumptions,
        *extraction.assumptions,
    ]))
    return GoalDraft.model_validate({
        **draft.model_dump(),
        **updates,
        "assumptions": assumptions,
    })


def apply_goal_defaults(draft: GoalDraft) -> GoalDraft:
    updates = {}
    if draft.goal_type is None and draft.title is not None:
        updates["goal_type"] = GoalType.OTHER
    if draft.title is None and draft.goal_type is not None:
        updates["title"] = DEFAULT_TITLE_BY_GOAL_TYPE[draft.goal_type]
    return draft.model_copy(update=updates) if updates else draft


def find_missing_fields(draft: GoalDraft) -> list[GoalField]:
    missing = []
    if draft.title is None and draft.goal_type is None:
        missing.append(GoalField.GOAL_TYPE)
    missing.extend([
        field
        for field in PLANNING_FIELD_ORDER
        if getattr(draft, FIELD_ATTRIBUTE[field]) is None
    ])
    return missing


def interview_state_for(missing_fields: list[GoalField]) -> InterviewState:
    return InterviewState.ACTIVE if missing_fields else InterviewState.REVIEW


def choose_next_field(
    missing_fields: list[GoalField],
    extraction: GoalExtraction,
) -> GoalField:
    if extraction.next_field in missing_fields:
        return extraction.next_field
    return missing_fields[0]


def next_question_for(
    field: GoalField,
    extraction: GoalExtraction,
    draft: GoalDraft,
    financial_context: FinancialContext | None,
) -> str:
    if extraction.next_field == field and extraction.next_question:
        return extraction.next_question
    return question_for(field, financial_context, draft)


def question_for(
    field: GoalField,
    financial_context: FinancialContext | None = None,
    draft: GoalDraft | None = None,
) -> str:
    goal_name = draft.title if draft and draft.title else "이 목표"
    questions = {
        GoalField.GOAL_TYPE: "어떤 상황이나 계획을 위해 돈을 마련하고 싶으세요?",
        GoalField.TARGET_AMOUNT: f"{goal_name}에 필요한 금액은 어느 정도인가요?",
        GoalField.TARGET_DATE: f"{goal_name}을 언제까지 마련하고 싶으세요?",
        GoalField.MONTHLY_CONTRIBUTION: f"{goal_name}을 위해 매달 부담 없이 마련할 수 있는 금액은 얼마인가요?",
    }
    if field == GoalField.CURRENT_AMOUNT:
        return current_amount_question(financial_context)
    return questions.get(field, "계획을 계산하는 데 필요한 내용을 조금 더 알려주세요.")


def current_amount_question(financial_context: FinancialContext | None) -> str:
    if financial_context is None or not financial_context.has_connected_accounts:
        return "이 목표를 위해 이미 따로 준비해 둔 금액이 있나요?"

    ready = financial_context.ready_amount
    conditional = financial_context.conditional_amount
    if ready > 0 and conditional > 0:
        return (
            f"바로 활용 가능한 후보 자산은 약 {ready:,}원이고, "
            f"조건 확인이 필요한 예·적금은 약 {conditional:,}원입니다. "
            "이 중 이 목표에 실제로 배정한 금액은 얼마인가요?"
        )
    if ready > 0:
        return (
            f"바로 활용 가능한 후보 자산이 약 {ready:,}원 확인됩니다. "
            "이 중 이 목표에 실제로 배정한 금액은 얼마인가요?"
        )
    if conditional > 0:
        return (
            f"조건 확인이 필요한 예·적금이 약 {conditional:,}원 확인됩니다. "
            "이 목표에 이미 배정한 금액이 있나요?"
        )
    return "연결된 계좌 중 이 목표에 실제로 배정한 금액이 있나요?"


def calculate_feasibility(
    draft: GoalDraft,
    reference_date: date | None = None,
) -> FeasibilityResult:
    required_values = (
        draft.target_amount,
        draft.target_date,
        draft.current_amount,
        draft.monthly_contribution,
    )
    if any(value is None for value in required_values):
        return FeasibilityResult(status=FeasibilityStatus.INSUFFICIENT_INFORMATION)

    target_amount = draft.target_amount
    target_date = draft.target_date
    current_amount = draft.current_amount
    monthly_contribution = draft.monthly_contribution
    assert target_amount is not None
    assert target_date is not None
    assert current_amount is not None
    assert monthly_contribution is not None

    remaining_amount = max(0, target_amount - current_amount)
    if remaining_amount == 0:
        return FeasibilityResult(
            status=FeasibilityStatus.ALREADY_ACHIEVED,
            remaining_amount=0,
            remaining_months=0,
            required_monthly_amount=0,
            monthly_gap=monthly_contribution,
        )

    today = reference_date or date.today()
    remaining_months = months_until(today, target_date)
    if remaining_months <= 0:
        return FeasibilityResult(
            status=FeasibilityStatus.ADJUSTMENT_REQUIRED,
            remaining_amount=remaining_amount,
            remaining_months=0,
            required_monthly_amount=None,
            monthly_gap=None,
        )

    required_monthly_amount = ceil(remaining_amount / remaining_months)
    monthly_gap = monthly_contribution - required_monthly_amount
    if monthly_contribution < required_monthly_amount:
        status = FeasibilityStatus.ADJUSTMENT_REQUIRED
    elif monthly_contribution * 10 < required_monthly_amount * 11:
        status = FeasibilityStatus.TIGHT
    else:
        status = FeasibilityStatus.ACHIEVABLE

    return FeasibilityResult(
        status=status,
        remaining_amount=remaining_amount,
        remaining_months=remaining_months,
        required_monthly_amount=required_monthly_amount,
        monthly_gap=monthly_gap,
    )


def months_until(start: date, target: date) -> int:
    months = (target.year - start.year) * 12 + target.month - start.month
    if target.day > start.day:
        months += 1
    return max(0, months)


def feasibility_question(
    draft: GoalDraft,
    result: FeasibilityResult,
) -> str:
    goal_name = draft.title or "재무 목표"
    if result.status == FeasibilityStatus.ALREADY_ACHIEVED:
        return (
            f"'{goal_name}'은 현재 준비금으로 이미 마련할 수 있어요. "
            "이 계획으로 목표를 확정할까요?"
        )
    if result.status == FeasibilityStatus.ADJUSTMENT_REQUIRED:
        if result.required_monthly_amount is None:
            return (
                f"'{goal_name}'의 목표 날짜가 이미 지났거나 너무 가까워요. "
                "새로운 목표 시점을 알려주시겠어요?"
            )
        increase = max(0, -result.monthly_gap) if result.monthly_gap is not None else 0
        return (
            f"'{goal_name}'까지 남은 금액은 약 {result.remaining_amount:,}원이고, "
            f"목표 시점까지 매달 약 {result.required_monthly_amount:,}원이 필요해요. "
            f"현재 계획보다 월 {increase:,}원 정도 더 필요합니다. "
            "월 납입액이나 목표 시점을 조정할까요, 아니면 이대로 확정할까요?"
        )
    if result.status == FeasibilityStatus.TIGHT:
        return (
            f"'{goal_name}'을 위해 매달 약 {result.required_monthly_amount:,}원이 필요해 "
            "여유가 크지는 않아요. 현재 계획으로 확정할까요?"
        )
    return (
        f"'{goal_name}'은 매달 약 {result.required_monthly_amount:,}원을 마련하면 "
        "목표 시점까지 달성할 수 있어요. 이 계획으로 확정할까요?"
    )
