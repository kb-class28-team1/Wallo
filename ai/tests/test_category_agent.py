import json
from types import SimpleNamespace

import pytest

from app.agents.category.agent import CategoryAgent, InvalidCategoryResponseError
from app.category.schemas import (
    CategoryClassification,
    CategoryClassificationBatch,
    CategoryClassificationBatchRequest,
    CategoryClassificationRequest,
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


def test_classify_calls_llm_once():
    client = FakeGroqClient(
        CategoryClassification(category="LIVING", confidence=0.86).model_dump_json()
    )
    agent = CategoryAgent(client, model="test-model")

    result = agent.classify(
        CategoryClassificationRequest(
            merchantName="unknown daily goods",
            merchantSector="other",
            amount=12000,
        )
    )

    assert result.category == "LIVING"
    assert result.confidence == 0.86
    assert client.chat.completions.call_count == 1
    assert client.chat.completions.calls[0]["model"] == "test-model"


def test_classify_batch_calls_llm_once_for_all_items():
    client = FakeGroqClient(
        CategoryClassificationBatch(
            results=[
                CategoryClassification(category="LIVING", confidence=0.86),
                CategoryClassification(category="FOOD", confidence=0.91),
            ]
        ).model_dump_json()
    )
    agent = CategoryAgent(client, model="test-model")

    result = agent.classify_batch(
        CategoryClassificationBatchRequest(
            items=[
                CategoryClassificationRequest(
                    merchantName="unknown one",
                    merchantSector=None,
                    amount=12000,
                ),
                CategoryClassificationRequest(
                    merchantName="unknown two",
                    merchantSector="restaurant",
                    amount=18000,
                ),
            ]
        )
    )

    assert [item.category for item in result.results] == ["LIVING", "FOOD"]
    assert client.chat.completions.call_count == 1
    assert client.chat.completions.calls[0]["max_completion_tokens"] == 512
    assert client.chat.completions.calls[0]["messages"][1]["content"].count(
        "unknown"
    ) == 2


def test_classify_batch_wraps_top_level_array_response():
    client = FakeGroqClient(json.dumps([
        {"category": "LIVING", "confidence": 0.86},
        {"category": "FOOD", "confidence": 0.91},
    ]))
    agent = CategoryAgent(client, model="test-model")

    result = agent.classify_batch(
        CategoryClassificationBatchRequest(
            items=[
                CategoryClassificationRequest(
                    merchantName="unknown one",
                    merchantSector=None,
                    amount=12000,
                ),
                CategoryClassificationRequest(
                    merchantName="unknown two",
                    merchantSector="restaurant",
                    amount=18000,
                ),
            ]
        )
    )

    assert [item.category for item in result.results] == ["LIVING", "FOOD"]
    assert [item.confidence for item in result.results] == [0.86, 0.91]


def test_classify_batch_rejects_invalid_result_count():
    client = FakeGroqClient(
        CategoryClassificationBatch(
            results=[CategoryClassification(category="LIVING", confidence=0.86)]
        ).model_dump_json()
    )
    agent = CategoryAgent(client, model="test-model")
    request = CategoryClassificationBatchRequest(
        items=[
            CategoryClassificationRequest(
                merchantName="unknown one",
                merchantSector=None,
                amount=12000,
            ),
            CategoryClassificationRequest(
                merchantName="unknown two",
                merchantSector=None,
                amount=18000,
            ),
        ]
    )

    with pytest.raises(InvalidCategoryResponseError):
        agent.classify_batch(request)

    assert client.chat.completions.call_count == 1
