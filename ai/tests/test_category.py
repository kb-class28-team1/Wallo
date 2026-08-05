import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.application import app
from app.category import CategoryClassification, CategoryClassificationBatch


class FakeChatCompletions:
    def __init__(self, content=None):
        self.content = content
        self.kwargs = None

    def create(self, **kwargs):
        self.kwargs = kwargs
        return SimpleNamespace(
            choices=[
                SimpleNamespace(
                    message=SimpleNamespace(content=self.content),
                )
            ]
        )


class FakeGroqClient:
    def __init__(self, content=None):
        self.chat = SimpleNamespace(
            completions=FakeChatCompletions(content),
        )


class CategoryClassificationApiTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    def test_classifies_category_with_structured_response(self):
        fake_client = FakeGroqClient(
            CategoryClassification(
                category="LIVING",
                confidence=0.86,
            ).model_dump_json()
        )

        with patch(
            "app.category.get_groq_client",
            return_value=fake_client,
        ):
            response = self.client.post(
                "/api/category/classify",
                json={
                    "merchantName": "unknown daily goods",
                    "merchantSector": "other",
                    "amount": 12000,
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json(),
            {"category": "LIVING", "confidence": 0.86},
        )
        self.assertEqual(
            fake_client.chat.completions.kwargs["model"],
            "openai/gpt-oss-20b",
        )
        self.assertEqual(
            fake_client.chat.completions.kwargs["response_format"]["type"],
            "json_schema",
        )
        self.assertTrue(
            fake_client.chat.completions.kwargs["response_format"]["json_schema"]["strict"]
        )

    def test_rejects_invalid_request(self):
        response = self.client.post(
            "/api/category/classify",
            json={
                "merchantName": "   ",
                "merchantSector": "other",
                "amount": 0,
            },
        )

        self.assertEqual(response.status_code, 422)

    def test_classifies_category_batch_with_results_in_input_order(self):
        fake_client = FakeGroqClient(
            CategoryClassificationBatch(
                results=[
                    CategoryClassification(category="LIVING", confidence=0.86),
                    CategoryClassification(category="FOOD", confidence=0.91),
                ]
            ).model_dump_json()
        )

        with patch(
            "app.category.get_groq_client",
            return_value=fake_client,
        ):
            response = self.client.post(
                "/api/category/classify/batch",
                json={
                    "items": [
                        {"merchantName": "unknown one", "merchantSector": None, "amount": 12000},
                        {"merchantName": "unknown two", "merchantSector": "restaurant", "amount": 18000},
                    ]
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json(),
            {
                "results": [
                    {"category": "LIVING", "confidence": 0.86},
                    {"category": "FOOD", "confidence": 0.91},
                ]
            },
        )
        self.assertEqual(
            len(fake_client.chat.completions.kwargs["messages"][1]["content"].split("unknown")) - 1,
            2,
        )

    def test_returns_bad_gateway_when_ai_returns_no_result(self):
        fake_client = FakeGroqClient(content=None)

        with patch(
            "app.category.get_groq_client",
            return_value=fake_client,
        ):
            response = self.client.post(
                "/api/category/classify",
                json={
                    "merchantName": "unknown store",
                    "merchantSector": None,
                    "amount": 1000,
                },
            )

        self.assertEqual(response.status_code, 502)

    def test_returns_service_unavailable_when_groq_key_is_missing(self):
        with patch(
            "app.category.get_groq_client",
            side_effect=RuntimeError("GROQ_API_KEY is not configured"),
        ):
            response = self.client.post(
                "/api/category/classify",
                json={
                    "merchantName": "unknown store",
                    "merchantSector": None,
                    "amount": 1000,
                },
            )

        self.assertEqual(response.status_code, 503)


if __name__ == "__main__":
    unittest.main()
