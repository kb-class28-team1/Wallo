import json
import logging
from typing import Any

from groq import Groq

from app.agents.financial.prompts import SYSTEM_PROMPT
from app.agents.financial.tools.registry import TOOL_SCHEMAS, execute_tool
from app.core.config import get_groq_model

logger = logging.getLogger("wallo_ai")


def parse_tool_arguments(raw_arguments: str) -> dict[str, Any]:
    try:
        arguments = json.loads(raw_arguments or "{}")
    except json.JSONDecodeError:
        return {"request": raw_arguments}
    return arguments if isinstance(arguments, dict) else {"request": str(arguments)}


class FinancialAgent:
    def __init__(self, client: Groq, model: str | None = None):
        self.client = client
        self.model = model or get_groq_model()

    def run(self, user_message: str) -> str:
        messages: list[dict[str, Any]] = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ]
        completion = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            tools=TOOL_SCHEMAS,
            tool_choice="auto",
            max_completion_tokens=500,
        )
        assistant_message = completion.choices[0].message
        if not assistant_message.tool_calls:
            logger.info("[AI ROUTING] direct_response")
            return assistant_message.content or "답변을 생성하지 못했습니다."

        tool_call = assistant_message.tool_calls[0]
        tool_result = execute_tool(
            tool_call.function.name,
            parse_tool_arguments(tool_call.function.arguments),
        )
        logger.info("[AI TOOL] selected=%s status=%s", tool_call.function.name, tool_result.status)
        messages.extend([
            {
                "role": "assistant",
                "content": assistant_message.content,
                "tool_calls": [tool_call.model_dump(exclude_none=True)],
            },
            {
                "role": "tool",
                "tool_call_id": tool_call.id,
                "content": json.dumps(tool_result.to_dict(), ensure_ascii=False),
            },
        ])
        final_completion = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            max_completion_tokens=500,
        )
        return final_completion.choices[0].message.content or "도구 호출 결과를 정리하지 못했습니다."


def generate_answer(client: Groq, user_message: str) -> str:
    """기존 호출부와 테스트를 위한 얇은 호환 함수."""
    return FinancialAgent(client).run(user_message)
