import unittest
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient

from ai.app.application import CategoryClassification, app


class FakeResponses:
    def __init__(self, parsed=None):
        self.parsed = parsed
        self.kwargs = None

    def parse(self, **kwargs):
        self.kwargs = kwargs
        return SimpleNamespace(output_parsed=self.parsed)


class FakeOpenAiClient:
    def __init__(self, parsed=None):
        self.responses = FakeResponses(parsed)


class CategoryClassificationApiTest(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(app)

    def test_classifies_category_with_structured_response(self):
        fake_client = FakeOpenAiClient(
            CategoryClassification(category="LIVING", confidence=0.86)
        )

        with patch(
            "ai.app.application.get_openai_client",
            return_value=fake_client,
        ):
            response = self.client.post(
                "/api/category/classify",
                json={
                    "merchantName": "알 수 없는 생활용품점",
                    "merchantSector": "기타",
                    "amount": 12000,
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json(),
            {"category": "LIVING", "confidence": 0.86},
        )
        self.assertEqual(
            fake_client.responses.kwargs["model"],
            "gpt-4o-mini",
        )
        self.assertFalse(fake_client.responses.kwargs["store"])

    def test_rejects_invalid_request(self):
        response = self.client.post(
            "/api/category/classify",
            json={
                "merchantName": "   ",
                "merchantSector": "기타",
                "amount": 0,
            },
        )

        self.assertEqual(response.status_code, 422)

    def test_returns_bad_gateway_when_ai_returns_no_result(self):
        fake_client = FakeOpenAiClient(parsed=None)

        with patch(
            "ai.app.application.get_openai_client",
            return_value=fake_client,
        ):
            response = self.client.post(
                "/api/category/classify",
                json={
                    "merchantName": "분류 불가 상점",
                    "merchantSector": None,
                    "amount": 1000,
                },
            )

        self.assertEqual(response.status_code, 502)


if __name__ == "__main__":
    unittest.main()
