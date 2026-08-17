import logging

from groq import Groq, GroqError

from app.agents.financial.agent import parse_tool_arguments
from app.chat.prompts import TITLE_PROMPT
from app.core.ai_timing import timed_groq_completion
from app.core.config import get_groq_model

logger = logging.getLogger("wallo_ai")

TITLE_TOOL = {
    "type": "function",
    "function": {
        "name": "generate_conversation_title",
        "description": "첫 사용자 메시지와 첫 AI 답변을 요약해 채팅방 제목을 생성한다.",
        "parameters": {
            "type": "object",
            "properties": {
                "title": {
                    "type": "string",
                    "description": "핵심 주제와 목적이 드러나는 8~20자의 한국어 명사형 제목",
                }
            },
            "required": ["title"],
            "additionalProperties": False,
        },
    },
}


def generate_conversation_title(
    client: Groq,
    user_message: str,
    assistant_answer: str,
) -> str | None:
    model = get_groq_model()
    with timed_groq_completion(
        client,
        operation="conversation.title",
        model=model,
        requested_completion_tokens=500,
    ) as timing:
        try:
            completion = timing.create(
                messages=[
                    {"role": "system", "content": TITLE_PROMPT},
                    {
                        "role": "user",
                        "content": f"첫 사용자 메시지:\n{user_message}\n\n첫 AI 답변:\n{assistant_answer}",
                    },
                ],
                tools=[TITLE_TOOL],
                tool_choice={
                    "type": "function",
                    "function": {"name": "generate_conversation_title"},
                },
                reasoning_effort="low",
                max_completion_tokens=500,
            )
            tool_calls = completion.choices[0].message.tool_calls
            if not tool_calls:
                logger.warning("Groq did not call the conversation title tool")
                return None
            title = str(
                parse_tool_arguments(tool_calls[0].function.arguments).get("title", "")
            ).strip(" \t\r\n\"'.")
            return title[:30] if title else None
        except GroqError:
            logger.exception("Groq title generation failed")
            return None
