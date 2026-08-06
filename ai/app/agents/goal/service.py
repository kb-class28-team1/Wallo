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
    InterviewState,
)


REQUIRED_FIELD_ORDER = (
    GoalField.TITLE,
    GoalField.GOAL_TYPE,
    GoalField.TARGET_AMOUNT,
    GoalField.TARGET_DATE,
    GoalField.MOTIVATION,
    GoalField.PRIORITY,
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
        missing_fields = find_missing_fields(updated)
        updated = GoalDraft.model_validate({
            **updated.model_dump(),
            "missing_fields": missing_fields,
            "state": interview_state_for(missing_fields),
        })

        if missing_fields:
            return GoalInterviewResult(
                draft=updated,
                next_question=question_for(missing_fields[0], financial_context),
            )

        feasibility = calculate_feasibility(updated, today)
        state = (
            InterviewState.FEASIBILITY_REVIEW
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
        exclude={"assumptions"},
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


def find_missing_fields(draft: GoalDraft) -> list[GoalField]:
    return [
        field
        for field in REQUIRED_FIELD_ORDER
        if getattr(draft, FIELD_ATTRIBUTE[field]) is None
    ]


def interview_state_for(missing_fields: list[GoalField]) -> InterviewState:
    if not missing_fields:
        return InterviewState.FEASIBILITY_REVIEW
    next_field = missing_fields[0]
    if next_field in {GoalField.TITLE, GoalField.GOAL_TYPE}:
        return InterviewState.DISCOVERY
    if next_field in {
        GoalField.TARGET_AMOUNT,
        GoalField.TARGET_DATE,
        GoalField.MOTIVATION,
        GoalField.PRIORITY,
    }:
        return InterviewState.DETAILING
    return InterviewState.FINANCIAL_CHECK


def question_for(
    field: GoalField,
    financial_context: FinancialContext | None = None,
) -> str:
    questions = {
        GoalField.TITLE: "달성하고 싶은 금융 목표를 구체적으로 알려주세요.",
        GoalField.GOAL_TYPE: "이 목표는 여행, 주거, 비상금, 부채 상환 등 어떤 유형인가요?",
        GoalField.TARGET_AMOUNT: "이 목표를 이루려면 총 얼마가 필요할까요?",
        GoalField.TARGET_DATE: "언제까지 이 목표를 달성하고 싶으세요?",
        GoalField.MOTIVATION: "이 목표가 지금 중요한 이유는 무엇인가요?",
        GoalField.PRIORITY: "다른 재무 계획과 비교했을 때 이 목표의 우선순위는 어느 정도인가요?",
        GoalField.MONTHLY_CONTRIBUTION: "이 목표를 위해 매달 부담 없이 마련할 수 있는 금액은 얼마인가요?",
    }
    if field == GoalField.CURRENT_AMOUNT:
        return current_amount_question(financial_context)
    return questions[field]


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
    if result.status == FeasibilityStatus.ALREADY_ACHIEVED:
        return "현재 준비금으로 목표 금액을 이미 마련했습니다. 이 내용으로 목표를 확정할까요?"
    if result.status == FeasibilityStatus.ADJUSTMENT_REQUIRED:
        if result.required_monthly_amount is None:
            return "목표 날짜가 이미 지났거나 너무 가까워 계획을 계산할 수 없습니다. 목표 시점을 조정할까요?"
        return (
            f"현재 계획에는 매달 약 {result.required_monthly_amount:,}원이 필요하지만, "
            f"가능한 금액은 {draft.monthly_contribution:,}원입니다. "
            "목표 금액, 목표 시점, 월 납입액 중 무엇을 조정할까요?"
        )
    if result.status == FeasibilityStatus.TIGHT:
        return (
            f"매달 약 {result.required_monthly_amount:,}원이 필요해 여유가 크지 않습니다. "
            "현재 계획으로 목표를 확정할까요?"
        )
    return (
        f"매달 약 {result.required_monthly_amount:,}원을 마련하면 목표를 달성할 수 있습니다. "
        "이 내용으로 목표를 확정할까요?"
    )
