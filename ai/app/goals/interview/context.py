from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


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
