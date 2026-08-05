from pydantic import BaseModel, ConfigDict, Field


class DemoAssetAnalysisRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    profile_id: int = Field(gt=0, alias="profileId")
    question: str = Field(
        default="현재 자산 상태를 분석하고 우선 실행할 행동을 알려줘.",
        min_length=1,
    )


class DemoProfileSummary(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    profile_id: int = Field(alias="profileId")
    title: str
    total_assets_krw: int | None = Field(alias="totalAssetsKrw")
    monthly_net_income_krw: int | None = Field(alias="monthlyNetIncomeKrw")


class DemoAssetAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    profile_id: int = Field(alias="profileId")
    title: str
    answer: str
