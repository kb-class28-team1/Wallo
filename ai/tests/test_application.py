import json
import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from app.application import generate_answer, generate_conversation_title


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


if __name__ == "__main__":
    unittest.main()
