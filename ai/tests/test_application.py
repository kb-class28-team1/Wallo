"""application.py 구조 테스트: 앱 생성과 router 등록을 확인한다. 실제 AI API를 호출하지 않는다.

app.routes를 직접 순회하는 대신 app.openapi()로 실제 노출되는 경로를 확인한다 — include_router로
등록된 하위 라우터는 Starlette/FastAPI 내부 표현상 app.routes에 곧바로 펼쳐지지 않기 때문에,
실제로 서비스되는 경로 목록(OpenAPI 스키마)을 기준으로 검증하는 편이 더 안정적이다.
"""

import importlib

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.application import app, create_app
from app.chat.router import router as chat_router
from app.core.ai_guard import (
    ApplicationConcurrencyLimitExceeded,
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


def test_specialized_agents_are_not_registered_directly_in_application():
    application_module = importlib.import_module("app.application")

    assert not hasattr(application_module, "CategoryAgent")
    assert not hasattr(application_module, "ConsumptionInsightAgent")


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
import json
import unittest
from types import SimpleNamespace
from unittest.mock import Mock
from unittest.mock import patch

from app.agents.financial.agent import generate_answer
from app.agents.financial.asset_analysis_cache import clear_asset_analysis_cache
from app.agents.financial.tools.asset_analysis import (
    build_metrics,
    execute as execute_asset_analysis,
    load_selected_profiles,
)
from app.chat.title_service import build_conversation_title
from app.demo.repository import load_demo_profiles
from app.demo.service import (
    build_demo_asset_facts,
    compact_demo_profile,
    generate_demo_asset_analysis,
    list_demo_profiles,
)


def _completion(message):
    return SimpleNamespace(choices=[SimpleNamespace(message=message)])


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

        with patch.dict("os.environ", {"DEMO_ASSET_PROFILE_ID": "7"}):
            answer = generate_answer(client, "내 자산을 분석해줘")

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
        self.assertEqual("demo_json", tool_result["data"]["dataMode"])
        self.assertEqual(7, tool_result["data"]["profileId"])
        final_call = client.chat.completions.create.call_args_list[1].kwargs
        self.assertEqual(1000, final_call["max_completion_tokens"])
        self.assertEqual("low", final_call["reasoning_effort"])

    def test_reuses_cached_asset_report_for_same_profile(self):
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
            _completion(SimpleNamespace(content="캐시할 자산분석 보고서")),
            _completion(selected_tool_message),
        ]

        first_answer = generate_answer(client, "내 자산을 분석해줘")
        second_answer = generate_answer(client, "자산 상태를 다시 알려줘")

        self.assertEqual("캐시할 자산분석 보고서", first_answer)
        self.assertEqual(first_answer, second_answer)
        self.assertEqual(3, client.chat.completions.create.call_count)

    def test_builds_title_without_an_ai_call(self):
        title = build_conversation_title("3년 뒤 전세 자금을 마련하고 싶어")

        self.assertEqual("주거 자금 마련", title)


class DemoAssetAnalysisTest(unittest.TestCase):
    def test_loads_demo_profiles_from_money_log_data(self):
        profiles = load_demo_profiles()

        self.assertGreater(len(profiles), 0)
        self.assertIn(3, profiles)
        self.assertEqual(106010000, profiles[3]["assets"]["total_assets_krw"])

    def test_lists_profiles_with_financial_summary(self):
        summaries = list_demo_profiles()
        profile = next(item for item in summaries if item.profile_id == 3)

        self.assertEqual(106010000, profile.total_assets_krw)
        self.assertEqual(5500000, profile.monthly_net_income_krw)
        self.assertTrue(profile.title)

    def test_generates_analysis_from_profile_without_expert_answer(self):
        client = Mock()
        client.chat.completions.create.return_value = _completion(
            SimpleNamespace(content="가상 사용자 자산분석 결과", tool_calls=None)
        )
        profile = load_demo_profiles()[3]

        answer = generate_demo_asset_analysis(client, profile, "자산을 분석해줘")

        self.assertIn("- 총자산: 106,010,000원", answer)
        self.assertTrue(answer.endswith("가상 사용자 자산분석 결과"))
        messages = client.chat.completions.create.call_args.kwargs["messages"]
        self.assertIn("106010000", messages[1]["content"])
        self.assertIn('"saving_rate_percent": 54.5', messages[1]["content"])
        self.assertNotIn("source_expert_content", messages[1]["content"])

    def test_calculates_financial_facts_before_llm_request(self):
        facts = build_demo_asset_facts(load_demo_profiles()[3])

        self.assertEqual(36000000, facts["annual_saving_krw"])
        self.assertEqual(54.5, facts["saving_rate_percent"])
        self.assertEqual(43780000, facts["listed_asset_items_sum_krw"])
        self.assertEqual(62230000, facts["asset_detail_unexplained_gap_krw"])

    def test_compacts_duplicate_raw_content_for_llm(self):
        compacted = compact_demo_profile(load_demo_profiles()[3])

        self.assertNotIn("raw_user_content", compacted)
        self.assertNotIn("total_assets_evidence", compacted["assets"])
        self.assertEqual(106010000, compacted["assets"]["total_assets_krw"])


class ChatAssetAnalysisToolTest(unittest.TestCase):
    def test_loads_default_profile_from_selected_json(self):
        profiles = load_selected_profiles()

        self.assertEqual({3, 5, 7, 12}, set(profiles))
        self.assertEqual("시금치커리", profiles[7]["nickname"])

    def test_returns_calculated_demo_profile_for_asset_analysis(self):
        with patch.dict("os.environ", {"DEMO_ASSET_PROFILE_ID": "12"}):
            result = execute_asset_analysis(
                "analyze_assets",
                {"request": "내 자산을 분석해줘"},
            )

        self.assertEqual("success", result.status)
        self.assertEqual(12, result.data["profileId"])
        self.assertEqual("demo_json", result.data["dataMode"])
        self.assertEqual(182600000, result.data["calculatedMetrics"]["netAssetsKrw"])

    def test_calculates_metrics_without_llm_arithmetic(self):
        metrics = build_metrics(load_selected_profiles()[7])

        self.assertEqual(1560000, metrics["monthlySurplusKrw"])
        self.assertEqual(18720000, metrics["annualSavingKrw"])
        self.assertEqual(65.0, metrics["savingRatePercent"])
        self.assertEqual(27000000, metrics["netAssetsKrw"])


if __name__ == "__main__":
    unittest.main()
