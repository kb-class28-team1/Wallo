from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ChatHistoryMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1)


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    message: str = Field(min_length=1)
    generate_title: bool = Field(default=False, alias="generateTitle")
    summary: str | None = None
    history: list[ChatHistoryMessage] = Field(default_factory=list, max_length=20)


class ChatResponse(BaseModel):
    answer: str
    title: str | None = None


class SummarizeConversationRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    existing_summary: str | None = Field(default=None, alias="existingSummary")
    messages: list[ChatHistoryMessage] = Field(min_length=1)


class SummarizeConversationResponse(BaseModel):
    summary: str
