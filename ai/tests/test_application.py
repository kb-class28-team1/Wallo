import json
import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from app.application import (
    build_demo_asset_facts,
    compact_demo_profile,
    generate_answer,
    generate_conversation_title,
    generate_demo_asset_analysis,
    list_demo_profiles,
    load_demo_profiles,
)


def _completion(message):
    return SimpleNamespace(choices=[SimpleNamespace(message=message)])


class GenerateAnswerTest(unittest.TestCase):
    def test_returns_direct_answer_when_model_does_not_select_tool(self):
        client = Mock()
        client.chat.completions.create.return_value = _completion(
            SimpleNamespace(content="안녕하세요!", tool_calls=None)
        )

        answer = generate_answer(client, "안녕")

        self.assertEqual("안녕하세요!", answer)
        self.assertEqual(1, client.chat.completions.create.call_count)

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

        answer = generate_answer(client, "내 자산을 분석해줘")

        self.assertEqual("자산 분석 기능을 선택했습니다.", answer)
        self.assertEqual(2, client.chat.completions.create.call_count)
        second_messages = client.chat.completions.create.call_args_list[1].kwargs["messages"]
        tool_result = json.loads(second_messages[-1]["content"])
        self.assertEqual("analyze_assets", tool_result["tool"])
        self.assertEqual("pending_integration", tool_result["status"])

    def test_generates_title_through_required_tool_call(self):
        client = Mock()
        title_call = SimpleNamespace(
            function=SimpleNamespace(
                arguments=json.dumps({"title": "3년 전세자금 계획"}),
            )
        )
        client.chat.completions.create.return_value = _completion(
            SimpleNamespace(content=None, tool_calls=[title_call])
        )

        title = generate_conversation_title(
            client,
            "3년 뒤 전세 자금을 마련하고 싶어",
            "매달 필요한 저축 금액을 계산해볼게요.",
        )

        self.assertEqual("3년 전세자금 계획", title)
        call_arguments = client.chat.completions.create.call_args.kwargs
        self.assertEqual(
            "generate_conversation_title",
            call_arguments["tool_choice"]["function"]["name"],
        )
        self.assertEqual(
            "generate_conversation_title",
            call_arguments["tools"][0]["function"]["name"],
        )


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


if __name__ == "__main__":
    unittest.main()
