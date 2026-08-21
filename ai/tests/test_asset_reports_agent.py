import json
from types import SimpleNamespace

import pytest

from app.agents.asset_reports.agent import (
    ConsumptionInsightAgent,
    InvalidConsumptionInsightResponseError,
)
from app.asset_reports.schemas import (
    ConsumptionInsightGenerateRequest,
    ConsumptionInsightGenerateResponse,
)


class FakeChatCompletions:
    def __init__(self, content):
        self.content = content
        self.call_count = 0
        self.calls = []

    def create(self, **kwargs):
        self.call_count += 1
        self.calls.append(kwargs)
        return SimpleNamespace(
            choices=[
                SimpleNamespace(
                    message=SimpleNamespace(content=self.content),
                )
            ]
        )


class FakeGroqClient:
    def __init__(self, content):
        self.chat = SimpleNamespace(
            completions=FakeChatCompletions(content),
        )


def _request() -> ConsumptionInsightGenerateRequest:
    return ConsumptionInsightGenerateRequest(
        category="CAFE",
        categoryLabel="카페",
        previousAmount=200_000,
        previousTotalAmount=600_000,
        monthlyBudget=700_000,
        categoryChangeRate=50.0,
        totalChangeRate=-16.7,
        withinBudget=True,
    )


def _response() -> ConsumptionInsightGenerateResponse:
    return ConsumptionInsightGenerateResponse(
        reportTitle="카페 지출이 가장 많아요",
        reportContent="카페 지출이 지난달보다 50% 늘었어요! 지출 내역을 점검해보세요.",
    )


def test_generate_calls_llm_once():
    client = FakeGroqClient(_response().model_dump_json())
    agent = ConsumptionInsightAgent(client, model="test-model")

    result = agent.generate(_request())

    assert result == _response()
    assert client.chat.completions.call_count == 1
    assert client.chat.completions.calls[0]["model"] == "test-model"
    assert client.chat.completions.calls[0]["response_format"] == {
        "type": "json_object",
    }
    assert client.chat.completions.calls[0]["max_completion_tokens"] == 1024


def test_generate_sends_only_aggregated_data_to_llm():
    client = FakeGroqClient(_response().model_dump_json())
    agent = ConsumptionInsightAgent(client, model="test-model")

    agent.generate(_request())

    user_prompt = client.chat.completions.calls[0]["messages"][1]["content"]
    assert '"previousAmount": 200000' in user_prompt
    assert '"categoryChangeRate": 50.0' in user_prompt
    assert "merchantName" not in user_prompt
    assert "currentAmount" not in user_prompt


def test_generate_rejects_invalid_response_after_one_call():
    client = FakeGroqClient(json.dumps({"reportTitle": "제목"}))
    agent = ConsumptionInsightAgent(client, model="test-model")

    with pytest.raises(InvalidConsumptionInsightResponseError):
        agent.generate(_request())

    assert client.chat.completions.call_count == 1
