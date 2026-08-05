from pydantic import BaseModel, Field, field_validator


class ConsumptionInsightGenerateRequest(BaseModel):
    """Spring 백엔드가 집계한 카테고리별 소비 데이터."""

    category: str = Field(min_length=1, max_length=50)
    categoryLabel: str = Field(min_length=1, max_length=50)
    currentAmount: int = Field(gt=0)
    previousAmount: int = Field(ge=0)
    currentTotalAmount: int = Field(default=0, ge=0)
    previousTotalAmount: int = Field(default=0, ge=0)
    monthlyBudget: int = Field(default=0, ge=0)

    @field_validator("category", "categoryLabel")
    @classmethod
    def text_must_not_be_blank(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("category and categoryLabel must not be blank")
        return normalized


class ConsumptionInsightGenerateResponse(BaseModel):
    reportTitle: str = Field(min_length=1, max_length=15)
    reportContent: str = Field(min_length=1, max_length=50)

    @field_validator("reportTitle", "reportContent")
    @classmethod
    def validate_text(cls, value: str) -> str:
        stripped = value.strip()
        if not stripped:
            raise ValueError("report text must not be blank")
        if stripped.startswith("```"):
            raise ValueError("report text must not contain a markdown code block")
        if stripped.startswith("{") and stripped.endswith("}"):
            raise ValueError("report text must not be a JSON-wrapped string")
        return stripped
