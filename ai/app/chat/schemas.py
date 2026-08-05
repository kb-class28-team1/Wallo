from pydantic import BaseModel, ConfigDict, Field


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1)
    generate_title: bool = Field(default=False, alias="generateTitle")


class ChatResponse(BaseModel):
    answer: str
    title: str | None = None
