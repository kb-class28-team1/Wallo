"""목표 인터뷰 과정에서 사용하는 내부 상태 모델."""

from datetime import date
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, field_validator


class InterviewState(str, Enum):
    """목표 인터뷰의 진행 단계."""

    DISCOVERY = "DISCOVERY"
    DETAILING = "DETAILING"
    FINANCIAL_CHECK = "FINANCIAL_CHECK"
    FEASIBILITY_REVIEW = "FEASIBILITY_REVIEW"
    CONFIRMATION = "CONFIRMATION"
    COMPLETED = "COMPLETED"


class GoalType(str, Enum):
    """사용자가 설정할 수 있는 대표 금융 목표 유형."""

    EMERGENCY_FUND = "EMERGENCY_FUND"
    TRAVEL = "TRAVEL"
    HOUSING = "HOUSING"
    EDUCATION = "EDUCATION"
    MARRIAGE = "MARRIAGE"
    DEBT_REPAYMENT = "DEBT_REPAYMENT"
    INVESTMENT = "INVESTMENT"
    RETIREMENT = "RETIREMENT"
    PURCHASE = "PURCHASE"
    OTHER = "OTHER"


class GoalPriority(str, Enum):
    """여러 목표 사이의 상대적 중요도."""

    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class GoalField(str, Enum):
    """인터뷰 중 수집하거나 추가 확인할 수 있는 목표 필드."""

    TITLE = "title"
    GOAL_TYPE = "goalType"
    TARGET_AMOUNT = "targetAmount"
    TARGET_DATE = "targetDate"
    MOTIVATION = "motivation"
    PRIORITY = "priority"
    CURRENT_AMOUNT = "currentAmount"
    MONTHLY_CONTRIBUTION = "monthlyContribution"


class GoalDraft(BaseModel):
    """사용자가 확정하기 전까지 점진적으로 채워지는 목표 초안."""

    model_config = ConfigDict(extra="forbid", validate_assignment=True)

    state: InterviewState = InterviewState.DISCOVERY
    title: str | None = Field(default=None, max_length=100)
    goal_type: GoalType | None = None
    target_amount: int | None = Field(default=None, gt=0)
    target_date: date | None = None
    motivation: str | None = Field(default=None, max_length=500)
    priority: GoalPriority | None = None
    current_amount: int | None = Field(default=None, ge=0)
    monthly_contribution: int | None = Field(default=None, ge=0)
    missing_fields: list[GoalField] = Field(default_factory=list)
    assumptions: list[str] = Field(default_factory=list)
    confirmed: bool = False

    @field_validator("title", "motivation")
    @classmethod
    def normalize_optional_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None

    @field_validator("missing_fields")
    @classmethod
    def reject_duplicate_missing_fields(
        cls,
        fields: list[GoalField],
    ) -> list[GoalField]:
        if len(fields) != len(set(fields)):
            raise ValueError("missing_fields에는 같은 필드를 중복 지정할 수 없습니다.")
        return fields

    @field_validator("assumptions")
    @classmethod
    def normalize_assumptions(cls, assumptions: list[str]) -> list[str]:
        normalized = [assumption.strip() for assumption in assumptions]
        if any(not assumption for assumption in normalized):
            raise ValueError("assumptions에는 빈 내용을 지정할 수 없습니다.")
        return normalized
