from datetime import date

from pydantic import BaseModel, ConfigDict, Field, model_validator


class RoadmapGoal(BaseModel):
    model_config = ConfigDict(populate_by_name=True, alias_generator=lambda value: "".join(
        word.capitalize() if index else word
        for index, word in enumerate(value.split("_"))
    ))

    title: str = Field(min_length=1, max_length=100)
    goal_type: str = Field(min_length=1, max_length=50)
    target_amount: int = Field(gt=0)
    current_amount: int = Field(ge=0)
    target_date: date
    required_monthly_amount: int = Field(ge=0)
    motivation: str | None = Field(default=None, max_length=500)
    reference_date: date = Field(default_factory=date.today)

    @model_validator(mode="after")
    def validate_period(self):
        if self.target_date <= self.reference_date:
            raise ValueError("targetDate는 referenceDate보다 이후여야 합니다.")
        return self


class RoadmapStep(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    sequence: int = Field(alias="stepNumber", ge=1)
    title: str | None = Field(default=None, min_length=1, max_length=100)
    description: str = Field(min_length=1, max_length=500)
    due_date: date = Field(alias="targetDate")
    target_amount: int = Field(alias="targetAmount", ge=0)
    monthly_contribution: int | None = Field(
        default=None,
        alias="monthlyContribution",
        ge=0,
    )
    action_items: list[str] = Field(alias="actionItems", min_length=1, max_length=5)


class GoalRoadmap(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    summary: str = Field(min_length=1, max_length=500)
    strategy: str = Field(min_length=1, max_length=1000)
    steps: list[RoadmapStep] = Field(min_length=2, max_length=24)

    def validate_for(self, goal: RoadmapGoal) -> "GoalRoadmap":
        expected_sequence = list(range(1, len(self.steps) + 1))
        if [step.sequence for step in self.steps] != expected_sequence:
            raise ValueError("로드맵 단계 sequence는 1부터 연속되어야 합니다.")
        dates = [step.due_date for step in self.steps]
        if dates != sorted(dates) or len(dates) != len(set(dates)):
            raise ValueError("로드맵 단계 날짜는 중복 없이 오름차순이어야 합니다.")
        amounts = [step.target_amount for step in self.steps]
        if amounts != sorted(amounts):
            raise ValueError("단계별 누적 목표 금액은 감소할 수 없습니다.")
        if dates[-1] != goal.target_date:
            raise ValueError("마지막 단계 날짜는 목표일과 일치해야 합니다.")
        if amounts[-1] != goal.target_amount:
            raise ValueError("마지막 단계 금액은 목표 금액과 일치해야 합니다.")
        return self
