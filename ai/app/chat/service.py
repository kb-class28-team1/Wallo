import re

from groq import Groq

from app.agents.financial.agent import FinancialAgent
from app.agents.financial.tools.financial_goal import NAME as FINANCIAL_GOAL_TOOL
from app.agents.goal.agent import GoalAgent
from app.agents.goal.models import GoalDraft, GoalInterviewAction, InterviewState
from app.chat.schemas import ChatRequest, ChatResponse, GoalInterviewResponse
from app.chat.title_service import generate_conversation_title


class ChatService:
    def __init__(self, client: Groq):
        self.client = client

    def chat(self, request: ChatRequest) -> ChatResponse:
        history = [message.model_dump() for message in request.history]
        if request.goal_draft is not None:
            answer, goal_interview = self._continue_goal_interview(request)
        else:
            financial_agent = FinancialAgent(self.client)
            answer = financial_agent.run(
                request.message,
                history,
                request.summary,
                request.financial_context,
            )
            goal_interview = None
            if financial_agent.selected_tool == FINANCIAL_GOAL_TOOL:
                answer, goal_interview = self._run_goal_agent(request, None)
        title = (
            generate_conversation_title(self.client, request.message, answer)
            if request.generate_title
            else None
        )
        return ChatResponse(
            answer=answer,
            title=title,
            goal_interview=goal_interview,
        )

    def _continue_goal_interview(
        self,
        request: ChatRequest,
    ) -> tuple[str, GoalInterviewResponse]:
        draft = request.goal_draft
        assert draft is not None
        normalized_message = re.sub(r"[\s.!?~]+", "", request.message).lower()
        if "취소" in normalized_message or normalized_message in {"그만", "그만할래"}:
            cancelled = draft.model_copy(
                update={"state": InterviewState.CANCELLED, "confirmed": False},
            )
            return (
                "진행 중인 목표 설정을 취소했습니다.",
                GoalInterviewResponse(
                    action=GoalInterviewAction.CANCEL,
                    active=False,
                    draft=cancelled,
                ),
            )
        if draft.state in {
            InterviewState.CONFIRMATION,
            InterviewState.REVIEW,
            InterviewState.FEASIBILITY_REVIEW,
        } and self._is_confirmation(
            normalized_message,
        ):
            confirmed = draft.model_copy(
                update={"state": InterviewState.COMPLETED, "confirmed": True},
            )
            return (
                f"'{confirmed.title}' 목표를 확정했습니다.",
                GoalInterviewResponse(
                    action=GoalInterviewAction.CONFIRM,
                    active=False,
                    draft=confirmed,
                ),
            )
        return self._run_goal_agent(request, draft)

    def _run_goal_agent(
        self,
        request: ChatRequest,
        draft: GoalDraft | None,
    ) -> tuple[str, GoalInterviewResponse]:
        result = GoalAgent(self.client).run(
            request.message,
            draft,
            request.financial_context,
        )
        return (
            result.next_question,
            GoalInterviewResponse(
                action=GoalInterviewAction.CONTINUE,
                active=True,
                draft=result.draft,
                feasibility=result.feasibility,
            ),
        )

    def _is_confirmation(self, normalized_message: str) -> bool:
        return (
            normalized_message in {
                "응",
                "네",
                "예",
                "좋아",
                "맞아",
                "맞습니다",
                "이대로할게",
                "확정",
                "확정할게",
            }
            or "확정해" in normalized_message
        )
