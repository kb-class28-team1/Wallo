import json

from groq import Groq

from app.chat.schemas import SummarizeConversationRequest
from app.core.ai_timing import timed_groq_completion
from app.core.config import get_groq_model


SUMMARY_SYSTEM_PROMPT = """
당신은 금융 상담 채팅의 장기 기억을 관리하는 요약기입니다.
기존 요약과 새 대화를 합쳐 간결한 한국어 요약을 작성하세요.
사용자의 재무 목표, 금액, 기간, 선호, 제약조건, 확정된 결정과 미해결 질문을 우선 보존하세요.
추측하거나 새로운 사실을 추가하지 마세요. 결과는 요약 본문만 반환하세요.
""".strip()


def summarize_conversation(client: Groq, request: SummarizeConversationRequest) -> str:
    payload = {
        "existing_summary": request.existing_summary or "",
        "new_messages": [message.model_dump() for message in request.messages],
    }
    model = get_groq_model()
    with timed_groq_completion(
        client,
        operation="conversation.summary",
        model=model,
        requested_completion_tokens=500,
    ) as timing:
        completion = timing.create(
            messages=[
                {"role": "system", "content": SUMMARY_SYSTEM_PROMPT},
                {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
            ],
            max_completion_tokens=500,
        )
        summary = completion.choices[0].message.content or ""
        if not summary.strip():
            raise RuntimeError("대화 요약을 생성하지 못했습니다.")
        return summary.strip()
