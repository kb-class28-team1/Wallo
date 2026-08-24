"""application.py 구조 테스트: 앱 생성과 router 등록을 확인한다. 실제 AI API를 호출하지 않는다.

app.routes를 직접 순회하는 대신 app.openapi()로 실제 노출되는 경로를 확인한다 — include_router로
등록된 하위 라우터는 Starlette/FastAPI 내부 표현상 app.routes에 곧바로 펼쳐지지 않기 때문에,
실제로 서비스되는 경로 목록(OpenAPI 스키마)을 기준으로 검증하는 편이 더 안정적이다.
"""

import ast
import importlib
from pathlib import Path

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.application import app, create_app
from app.chat.router import router as chat_router
from app.core.ai_guard import (
    ApplicationConcurrencyLimitExceeded,
    ApplicationQueueFullError,
    ApplicationQueueTimeoutError,
    ApplicationTokenBudgetExceeded,
)


def _registered_paths_with_method(method: str) -> set[str]:
    schema = app.openapi()
    return {
        path
        for path, operations in schema.get("paths", {}).items()
        if method.lower() in operations
    }


def test_app_is_a_fastapi_instance():
    assert isinstance(app, FastAPI)


def test_chat_endpoint_is_registered():
    assert "/api/chat" in _registered_paths_with_method("POST")


def test_chat_endpoint_is_not_registered_twice():
    matching_routes = [
        route
        for route in chat_router.routes
        if getattr(route, "path", None) == "/api/chat" and "POST" in getattr(route, "methods", set())
    ]
    assert len(matching_routes) == 1


def test_category_classification_endpoints_are_registered():
    post_paths = _registered_paths_with_method("POST")

    assert "/api/category/classify" in post_paths
    assert "/api/category/classify/batch" in post_paths


def test_financial_report_generate_endpoint_is_registered():
    assert "/api/reports/generate" in _registered_paths_with_method("POST")


def test_consumption_insight_generate_endpoint_is_registered():
    assert "/api/asset-reports/insights/generate" in _registered_paths_with_method("POST")


def test_demo_endpoints_are_not_registered():
    schema = app.openapi()

    assert not any(path.startswith("/api/demo") for path in schema.get("paths", {}))


def test_specialized_agents_are_not_registered_directly_in_application():
    application_module = importlib.import_module("app.application")

    assert not hasattr(application_module, "CategoryAgent")
    assert not hasattr(application_module, "ConsumptionInsightAgent")


def test_financial_ai_does_not_import_a_database_client():
    financial_root = Path(__file__).parents[1] / "app" / "financial_assistant"
    assert financial_root.is_dir()
    forbidden_modules = {
        "asyncpg",
        "databases",
        "mysql",
        "psycopg",
        "psycopg2",
        "pymysql",
        "sqlite3",
        "sqlalchemy",
    }
    imported_modules = set()

    for source_path in financial_root.rglob("*.py"):
        tree = ast.parse(source_path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                imported_modules.update(alias.name.split(".")[0] for alias in node.names)
            elif isinstance(node, ast.ImportFrom) and node.module:
                imported_modules.add(node.module.split(".")[0])

    assert imported_modules.isdisjoint(forbidden_modules)


def test_health_endpoint_is_registered():
    assert "/api/health" in _registered_paths_with_method("GET")


def test_no_duplicated_api_prefix_in_registered_paths():
    """router prefix가 중복 등록되어 /api/api/...가 되지 않았는지 확인한다."""
    schema = app.openapi()
    for path in schema.get("paths", {}):
        assert "/api/api" not in path


def test_application_token_guard_returns_429_with_retry_after():
    guard_error = ApplicationTokenBudgetExceeded(
        requested_tokens=100,
        available_tokens=0,
        retry_after_seconds=2.5,
    )

    with patch("app.chat.router.create_groq_client", return_value=object()), patch(
        "app.chat.router.ChatService.chat",
        side_effect=guard_error,
    ):
        response = TestClient(create_app()).post(
            "/api/chat",
            json={"message": "hello"},
        )

    assert response.status_code == 429
    assert response.headers["Retry-After"] == "3"
    assert response.json() == {
        "detail": (
            "AI application token budget is temporarily exhausted; "
            "retry after 3 seconds"
        ),
        "errorCode": "AI_TOKEN_BUDGET_EXCEEDED",
        "retryable": True,
    }


def test_application_concurrency_guard_returns_429():
    test_app = create_app()

    def reject_request():
        raise ApplicationConcurrencyLimitExceeded(max_in_flight_requests=2)

    test_app.add_api_route("/test/application-concurrency", reject_request, methods=["GET"])

    response = TestClient(test_app).get("/test/application-concurrency")

    assert response.status_code == 429
    assert response.headers["Retry-After"] == "1"
    assert response.json()["errorCode"] == "AI_CONCURRENCY_LIMIT_EXCEEDED"


@pytest.mark.parametrize(
    ("guard_error", "expected_error_code", "expected_retry_after"),
    [
        (
            ApplicationQueueFullError(queue_size=8, max_queue_size=8),
            "AI_QUEUE_FULL",
            "1",
        ),
        (
            ApplicationQueueTimeoutError(
                retry_after_seconds=4.2,
                waited_seconds=30.0,
            ),
            "AI_QUEUE_TIMEOUT",
            "5",
        ),
    ],
)
def test_application_queue_errors_return_429_with_retry_after(
    guard_error,
    expected_error_code,
    expected_retry_after,
):
    with patch("app.chat.router.create_groq_client", return_value=object()), patch(
        "app.chat.router.ChatService.chat",
        side_effect=guard_error,
    ):
        response = TestClient(create_app()).post(
            "/api/chat",
            json={"message": "hello"},
        )

    assert response.status_code == 429
    assert response.headers["Retry-After"] == expected_retry_after
    assert response.json()["errorCode"] == expected_error_code
    assert response.json()["retryable"] is True
import json
import unittest
from types import SimpleNamespace
from unittest.mock import Mock
from unittest.mock import patch

from app.financial_assistant.agent import generate_answer
from app.financial_assistant.asset_analysis_cache import clear_asset_analysis_cache
from app.financial_assistant.tools.asset_analysis import (
    build_metrics,
    execute as execute_asset_analysis,
)
from app.chat.schemas import AssetAnalysisContext
from app.chat.title_service import build_conversation_title


def _completion(message):
    return SimpleNamespace(choices=[SimpleNamespace(message=message)])


def _asset_analysis_context() -> AssetAnalysisContext:
    return AssetAnalysisContext.model_validate({
        "totalAssets": 182_600_000,
        "totalDebt": 12_600_000,
        "netAssets": 170_000_000,
        "monthlyIncome": 5_500_000,
        "monthlyExpense": 2_000_000,
        "monthlySaving": 3_500_000,
        "savingRatePercent": 63.6,
        "assetComposition": [
            {
                "category": "DEPOSIT",
                "amount": 120_000_000,
                "sharePercent": 65.7,
            },
        ],
        "asOf": "2026-08-20T12:00:00+09:00",
    })


class GenerateAnswerTest(unittest.TestCase):
    def setUp(self):
        clear_asset_analysis_cache()

    def tearDown(self):
        clear_asset_analysis_cache()

    def test_returns_direct_answer_when_model_does_not_select_tool(self):
        client = Mock()
        client.chat.completions.create.return_value = _completion(
            SimpleNamespace(content="안녕하세요!", tool_calls=None)
        )

        answer = generate_answer(client, "안녕")

        self.assertEqual("안녕하세요!", answer)
        self.assertEqual(1, client.chat.completions.create.call_count)
        route_call = client.chat.completions.create.call_args.kwargs
        self.assertNotIn("tools", route_call)
        self.assertNotIn("tool_choice", route_call)
        self.assertEqual("low", route_call["reasoning_effort"])
        self.assertEqual(256, route_call["max_completion_tokens"])

    def test_dispatches_selected_tool_and_returns_final_answer(self):
        client = Mock()
        tool_call = SimpleNamespace(
            id="call-1",
            function=SimpleNamespace(
                name="analyze_assets",
                arguments=json.dumps({"request": "내 자산을 분석해줘"}),
            ),
            model_dump=Mock(
                return_value={
                    "id": "call-1",
                    "type": "function",
                    "function": {
                        "name": "analyze_assets",
                        "arguments": '{"request": "내 자산을 분석해줘"}',
                    },
                }
            ),
        )
        tool_message = SimpleNamespace(
            content=None,
            tool_calls=[tool_call],
        )
        final_message = SimpleNamespace(content="자산 분석 기능을 선택했습니다.")
        client.chat.completions.create.side_effect = [
            _completion(tool_message),
            _completion(final_message),
        ]

        answer = generate_answer(
            client,
            "내 자산을 분석해줘",
            asset_analysis_context=_asset_analysis_context(),
        )

        self.assertEqual("자산 분석 기능을 선택했습니다.", answer)
        self.assertEqual(2, client.chat.completions.create.call_count)
        route_call = client.chat.completions.create.call_args_list[0].kwargs
        route_tool_names = {
            schema["function"]["name"]
            for schema in route_call["tools"]
        }
        self.assertEqual(
            {"analyze_assets", "generate_financial_report"},
            route_tool_names,
        )
        self.assertEqual("low", route_call["reasoning_effort"])
        second_messages = client.chat.completions.create.call_args_list[1].kwargs["messages"]
        tool_result = json.loads(second_messages[-1]["content"])
        self.assertEqual("analyze_assets", tool_result["tool"])
        self.assertEqual("success", tool_result["status"])
        self.assertEqual("database", tool_result["data"]["dataMode"])
        self.assertNotIn("profileId", tool_result["data"])
        self.assertEqual(
            170_000_000,
            tool_result["data"]["calculatedMetrics"]["netAssetsKrw"],
        )
        self.assertEqual(
            182_600_000,
            tool_result["data"]["profile"]["assets"]["total_assets_krw"],
        )
        final_call = client.chat.completions.create.call_args_list[1].kwargs
        self.assertEqual(1000, final_call["max_completion_tokens"])
        self.assertEqual("low", final_call["reasoning_effort"])

    def test_requires_database_context_without_demo_fallback(self):
        client = Mock()
        tool_call = SimpleNamespace(
            id="call-asset",
            function=SimpleNamespace(
                name="analyze_assets",
                arguments=json.dumps({"request": "내 자산을 분석해줘"}),
            ),
            model_dump=Mock(return_value={
                "id": "call-asset",
                "type": "function",
                "function": {
                    "name": "analyze_assets",
                    "arguments": '{"request": "내 자산을 분석해줘"}',
                },
            }),
        )
        selected_tool_message = SimpleNamespace(content=None, tool_calls=[tool_call])
        client.chat.completions.create.side_effect = [
            _completion(selected_tool_message),
            _completion(SimpleNamespace(content="자산 컨텍스트가 필요합니다.")),
        ]

        answer = generate_answer(client, "내 자산을 분석해줘")

        self.assertEqual("자산 컨텍스트가 필요합니다.", answer)
        self.assertEqual(2, client.chat.completions.create.call_count)
        tool_result = json.loads(
            client.chat.completions.create.call_args_list[1].kwargs["messages"][-1][
                "content"
            ]
        )
        self.assertEqual("needs_input", tool_result["status"])
        self.assertEqual("database", tool_result["data"]["dataMode"])
        self.assertEqual({}, tool_result["data"]["calculatedMetrics"])
        self.assertEqual([], tool_result["data"]["profile"]["assets"]["items"])

    def test_builds_title_without_an_ai_call(self):
        title = build_conversation_title("3년 뒤 전세 자금을 마련하고 싶어")

        self.assertEqual("주거 자금 마련", title)


class ChatAssetAnalysisToolTest(unittest.TestCase):
    def test_returns_calculated_database_profile_for_asset_analysis(self):
        result = execute_asset_analysis(
            "analyze_assets",
            {"request": "내 자산을 분석해줘"},
            _asset_analysis_context(),
        )

        self.assertEqual("success", result.status)
        self.assertEqual("database", result.data["dataMode"])
        self.assertNotIn("profileId", result.data)
        self.assertEqual(170_000_000, result.data["calculatedMetrics"]["netAssetsKrw"])
        self.assertEqual(
            "DEPOSIT",
            result.data["profile"]["assets"]["items"][0]["category"],
        )

    def test_calculates_metrics_without_llm_arithmetic(self):
        metrics = build_metrics(_asset_analysis_context())

        self.assertEqual(3_500_000, metrics["monthlySurplusKrw"])
        self.assertEqual(42_000_000, metrics["annualSavingKrw"])
        self.assertEqual(63.6, metrics["savingRatePercent"])
        self.assertEqual(170_000_000, metrics["netAssetsKrw"])

    def test_requires_database_context(self):
        result = execute_asset_analysis(
            "analyze_assets",
            {"request": "내 자산을 분석해줘"},
        )

        self.assertEqual("needs_input", result.status)
        self.assertEqual("database", result.data["dataMode"])
        self.assertEqual({}, result.data["calculatedMetrics"])
        self.assertEqual({}, result.data["profile"])


if __name__ == "__main__":
    unittest.main()
