from enum import Enum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ChatHistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1)


class AccountSubtype(str, Enum):
    DEPOSIT = "DEPOSIT"
    SAVINGS = "SAVINGS"
    STOCK = "STOCK"
    CMA = "CMA"
    PENSION = "PENSION"
    LOAN = "LOAN"
    UNKNOWN = "UNKNOWN"


class GoalFundAvailability(str, Enum):
    READY = "READY"
    CONDITIONAL = "CONDITIONAL"
    RISK_ASSET = "RISK_ASSET"
    EXCLUDED = "EXCLUDED"
    UNKNOWN = "UNKNOWN"


class GoalAccountContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    subtype: AccountSubtype
    source_subtype: str | None = Field(
        default=None,
        alias="sourceSubtype",
        max_length=20,
    )
    amount: int = Field(ge=0)
    availability: GoalFundAvailability


class FinancialContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    has_connected_accounts: bool = Field(alias="hasConnectedAccounts")
    ready_amount: int = Field(alias="readyAmount", ge=0)
    conditional_amount: int = Field(alias="conditionalAmount", ge=0)
    risk_asset_amount: int = Field(alias="riskAssetAmount", ge=0)
    excluded_amount: int = Field(alias="excludedAmount", ge=0)
    unknown_amount: int = Field(alias="unknownAmount", ge=0)
    debt_amount: int = Field(alias="debtAmount", ge=0)
    accounts: list[GoalAccountContext] = Field(default_factory=list)


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


class ChatResponse(BaseModel):
    answer: str
    title: str | None = None


class SummarizeConversationRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    existing_summary: str | None = Field(default=None, alias="existingSummary")
    messages: list[ChatHistoryMessage] = Field(min_length=1)


class SummarizeConversationResponse(BaseModel):
    summary: str
