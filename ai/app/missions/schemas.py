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
    category: str = Field(default="GENERAL", min_length=1, max_length=30)
    difficulty: Literal["EASY", "NORMAL", "HARD"]
    rewardPoint: int = Field(ge=0, le=100)
    verificationType: Literal[
        "MEDIA_AI", "TRANSACTION", "HYBRID", "SELF_CHECK", "MANUAL"
    ]
    verificationRule: dict[str, Any] | None = None
    evidenceGuide: str | None = Field(default=None, max_length=500)

    @model_validator(mode="before")
    @classmethod
    def normalize_common_ai_field_names(cls, value):
        if not isinstance(value, dict):
            return value
        normalized = dict(value)
        if "verificationType" not in normalized and "type" in normalized:
            normalized["verificationType"] = normalized.pop("type")
        if "rewardPoint" not in normalized and "points" in normalized:
            normalized["rewardPoint"] = normalized.pop("points")
        return normalized

    @field_validator("title", "description", "category")
    @classmethod
    def normalize_required_text(cls, value: str) -> str:
        normalized = " ".join(value.split())
        if not normalized:
            raise ValueError("mission text must not be blank")
        return normalized


class MissionGenerateResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    missions: list[GeneratedMission] = Field(min_length=30)
    promptVersion: str = Field(min_length=1, max_length=50)

    @model_validator(mode="before")
    @classmethod
    def keep_first_thirty_unique_missions(cls, value):
        if not isinstance(value, dict) or not isinstance(value.get("missions"), list):
            return value
        unique = []
        title_keys = set()
        for mission in value["missions"]:
            title = mission.get("title") if isinstance(mission, dict) else None
            key = "".join(str(title or "").lower().split())
            if key and key not in title_keys:
                title_keys.add(key)
                unique.append(mission)
            if len(unique) == 30:
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
