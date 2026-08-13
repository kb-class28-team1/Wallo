from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from app.agents.financial.consumption_models import ConsumptionContext

from app.agents.goal.context import (
    AccountSubtype,
    FinancialContext,
    GoalAccountContext,
    GoalFundAvailability,
)
from app.agents.goal.models import (
    FeasibilityResult,
    GoalDraft,
    GoalInterviewAction,
)
from app.agents.roadmap.models import GoalRoadmap

class ChatHistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1)


class ConsumptionAnalysisPeriodContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    type: str
    label: str | None = None
    start_date: str = Field(alias="startDate")
    end_date: str = Field(alias="endDate")
    compare_start: str = Field(alias="compareStart")
    compare_end: str = Field(alias="compareEnd")


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1)
    generate_title: bool = Field(default=False, alias="generateTitle")
    summary: str | None = None
    history: list[ChatHistoryMessage] = Field(default_factory=list, max_length=20)
    financial_context: FinancialContext | None = Field(
        default=None,
        alias="financialContext",
    )
    goal_draft: GoalDraft | None = Field(default=None, alias="goalDraft")
    goal_already_exists: bool = Field(default=False, alias="goalAlreadyExists")
    consumption_context: ConsumptionContext | None = Field(
        default=None, alias="consumptionContext"
    )
    previous_consumption_period: ConsumptionAnalysisPeriodContext | None = Field(
        default=None, alias="previousConsumptionPeriod"
    )


class GoalInterviewResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    action: GoalInterviewAction
    active: bool
    draft: GoalDraft
    feasibility: FeasibilityResult | None = None
    roadmap: GoalRoadmap | None = None
    roadmap_error: str | None = Field(default=None, alias="roadmapError")


class ChatResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    answer: str
    title: str | None = None
    goal_interview: GoalInterviewResponse | None = Field(
        default=None,
        alias="goalInterview",
    )
    consumption_analysis: dict | None = Field(
        default=None,
        alias="consumptionAnalysis",
    )
    asset_analysis: dict | None = Field(
        default=None,
        alias="assetAnalysis",
    )


class SummarizeConversationRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    existing_summary: str | None = Field(default=None, alias="existingSummary")
    messages: list[ChatHistoryMessage] = Field(min_length=1)


class SummarizeConversationResponse(BaseModel):
    summary: str
