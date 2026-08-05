from enum import Enum

from pydantic import BaseModel, Field, field_validator


class ExpenseCategory(str, Enum):
    FOOD = "FOOD"
    CAFE = "CAFE"
    TRANSPORT = "TRANSPORT"
    SHOPPING = "SHOPPING"
    DELIVERY = "DELIVERY"
    HOUSING = "HOUSING"
    LIVING = "LIVING"
    CULTURE = "CULTURE"
    HEALTH = "HEALTH"
    EDUCATION = "EDUCATION"
    ETC = "ETC"


class CategoryClassificationRequest(BaseModel):
    merchantName: str = Field(min_length=1, max_length=100)
    merchantSector: str | None = Field(default=None, max_length=100)
    amount: int = Field(gt=0)

    @field_validator("merchantName")
    @classmethod
    def merchant_name_must_not_be_blank(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("merchantName must not be blank")
        return normalized

    @field_validator("merchantSector")
    @classmethod
    def normalize_merchant_sector(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class CategoryClassification(BaseModel):
    category: ExpenseCategory
    confidence: float = Field(ge=0.0, le=1.0)


class CategoryClassificationResponse(BaseModel):
    category: ExpenseCategory
    confidence: float = Field(ge=0.0, le=1.0)


class CategoryClassificationBatchRequest(BaseModel):
    items: list[CategoryClassificationRequest] = Field(min_length=1, max_length=50)


class CategoryClassificationBatch(BaseModel):
    results: list[CategoryClassification] = Field(min_length=1, max_length=50)


class CategoryClassificationBatchResponse(BaseModel):
    results: list[CategoryClassificationResponse] = Field(min_length=1, max_length=50)
