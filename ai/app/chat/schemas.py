from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

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


class SummarizeConversationRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    existing_summary: str | None = Field(default=None, alias="existingSummary")
    messages: list[ChatHistoryMessage] = Field(min_length=1)


class SummarizeConversationResponse(BaseModel):
    summary: str
