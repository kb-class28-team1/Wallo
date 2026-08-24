from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from app.financial_assistant.consumption_models import ConsumptionContext

from app.goals.interview.context import (
    AccountSubtype,
    FinancialContext,
    GoalAccountContext,
    GoalFundAvailability,
)
from app.goals.interview.models import (
    FeasibilityResult,
    GoalDraft,
    GoalInterviewAction,
)
from app.goals.roadmap.models import GoalRoadmap

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


class AssetCompositionContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    category: str
    amount: int
    share_percent: float = Field(alias="sharePercent")


class AssetAnalysisContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    total_assets: int = Field(alias="totalAssets")
    total_debt: int = Field(alias="totalDebt")
    net_assets: int = Field(alias="netAssets")
    monthly_income: int = Field(alias="monthlyIncome")
    monthly_expense: int = Field(alias="monthlyExpense")
    monthly_saving: int = Field(alias="monthlySaving")
    saving_rate_percent: float | None = Field(default=None, alias="savingRatePercent")
    asset_composition: list[AssetCompositionContext] = Field(
        default_factory=list,
        alias="assetComposition",
    )
    as_of: str | None = Field(default=None, alias="asOf")


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1)
    chat_mode: str | None = Field(default=None, alias="chatMode")
    generate_title: bool = Field(default=False, alias="generateTitle")
    summary: str | None = None
    history: list[ChatHistoryMessage] = Field(default_factory=list, max_length=20)
    financial_context: FinancialContext | None = Field(
        default=None,
        alias="financialContext",
    )
    asset_analysis_context: AssetAnalysisContext | None = Field(
        default=None,
        alias="assetAnalysisContext",
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
    product_recommendation: dict | None = Field(
        default=None,
        alias="productRecommendation",
    )
    consumption_analysis_reused: bool = Field(
        default=False,
        alias="consumptionAnalysisReused",
    )


class SummarizeConversationRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    existing_summary: str | None = Field(default=None, alias="existingSummary")
    messages: list[ChatHistoryMessage] = Field(min_length=1)


class SummarizeConversationResponse(BaseModel):
    summary: str
