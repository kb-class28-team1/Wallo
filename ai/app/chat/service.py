from groq import Groq

from app.agents.financial.agent import FinancialAgent
from app.chat.schemas import ChatRequest, ChatResponse
from app.chat.title_service import generate_conversation_title


class ChatService:
    def __init__(self, client: Groq):
        self.client = client

    def chat(self, request: ChatRequest) -> ChatResponse:
        history = [message.model_dump() for message in request.history]
        answer = FinancialAgent(self.client).run(
            request.message,
            history,
            request.summary,
            request.financial_context,
        )
        title = (
            generate_conversation_title(self.client, request.message, answer)
            if request.generate_title
            else None
        )
        return ChatResponse(answer=answer, title=title)
