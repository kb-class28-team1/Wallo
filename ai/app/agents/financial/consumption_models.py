from datetime import date, time

from pydantic import BaseModel, ConfigDict, Field


class ConsumptionTransaction(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    date: date
    time: time
    category: str
    amount: int = Field(ge=0)
    merchant_name: str | None = Field(default=None, alias="merchantName")


class ConsumptionBudget(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    target_month: str = Field(alias="targetMonth")
    total_amount: int = Field(alias="totalAmount", gt=0)


class ConsumptionContext(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    transactions: list[ConsumptionTransaction] = Field(default_factory=list)
    budget: ConsumptionBudget | None = None
