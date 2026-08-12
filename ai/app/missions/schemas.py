from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class MissionGenerateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    userId: int = Field(gt=0)
    analysisResultId: int | None = Field(default=None, gt=0)
    consumptionAnalysis: dict[str, Any]


class GeneratedMission(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=100)
    description: str = Field(min_length=1, max_length=500)
    category: str = Field(min_length=1, max_length=30)
    rewardPoint: Literal[10]
    verificationType: Literal[
        "MEDIA_AI", "TRANSACTION", "HYBRID", "SELF_CHECK", "MANUAL"
    ]
    verificationRule: dict[str, Any] | None = None
    evidenceGuide: str | None = Field(default=None, max_length=500)

    @field_validator("title", "description", "category")
    @classmethod
    def normalize_required_text(cls, value: str) -> str:
        normalized = " ".join(value.split())
        if not normalized:
            raise ValueError("mission text must not be blank")
        return normalized


class MissionGenerateResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    missions: list[GeneratedMission] = Field(min_length=10, max_length=20)
    promptVersion: str = Field(min_length=1, max_length=50)

    @model_validator(mode="before")
    @classmethod
    def keep_first_twenty_unique_missions(cls, value):
        if not isinstance(value, dict) or not isinstance(value.get("missions"), list):
            return value
        unique = []
        title_keys = set()
        for mission in value["missions"]:
            title = (mission.get("title") if isinstance(mission, dict)
                     else getattr(mission, "title", None))
            key = "".join(str(title or "").lower().split())
            if key and key not in title_keys:
                title_keys.add(key)
                unique.append(mission)
            if len(unique) == 20:
                break
        return {**value, "missions": unique}

    @model_validator(mode="after")
    def missions_must_be_unique(self):
        keys = {
            "".join(mission.title.lower().split())
            for mission in self.missions
        }
        if len(keys) != len(self.missions):
            raise ValueError("missions must be unique within a cycle")
        return self


class MissionBatchResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    missions: list[GeneratedMission] = Field(min_length=1, max_length=12)
    promptVersion: str = Field(min_length=1, max_length=50)
