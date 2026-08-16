import re
import logging

from groq import Groq

from app.agents.financial.agent import ASSET_ANALYSIS_TOOL, FinancialAgent
from app.agents.financial.tools.financial_goal import NAME as FINANCIAL_GOAL_TOOL
from app.agents.goal.agent import GoalAgent
from app.agents.goal.models import GoalDraft, GoalInterviewAction, InterviewState
from app.agents.goal.service import calculate_feasibility
from app.agents.roadmap.generator import generate_goal_roadmap
from app.agents.roadmap.models import GoalRoadmap, RoadmapGoal
from app.chat.schemas import ChatRequest, ChatResponse, GoalInterviewResponse
from app.chat.title_service import generate_conversation_title


logger = logging.getLogger("wallo_ai")
GOAL_SETTING_MODE = "GOAL_SETTING"


class ChatService:
    def __init__(self, client: Groq):
        self.client = client

    def chat(self, request: ChatRequest) -> ChatResponse:
        history = [message.model_dump() for message in request.history]
        consumption_analysis = None
        asset_analysis = None
        if request.goal_draft is not None:
            answer, goal_interview = self._continue_goal_interview(request)
        elif self._is_goal_setting_mode(request.chat_mode):
            if request.goal_already_exists:
                answer = self._existing_goal_message()
                goal_interview = None
            else:
                answer, goal_interview = self._run_goal_agent(request, None)
        else:
            normalized_message = self._normalize_message(request.message)
            if self._is_confirmation(normalized_message):
                answer = self._no_active_goal_message(request.goal_already_exists)
                goal_interview = None
            elif self._is_cancellation(normalized_message):
                answer = "현재 진행 중인 목표 설정이 없습니다."
                goal_interview = None
            elif (
                request.goal_already_exists
                and self._is_goal_creation_request(normalized_message)
            ):
                answer = self._existing_goal_message()
                goal_interview = None
            else:
                financial_agent = FinancialAgent(self.client)
                previous_period = (
                    request.previous_consumption_period.model_dump(by_alias=True)
                    if request.previous_consumption_period is not None
                    else None
                )
                answer = financial_agent.run(
                    request.message,
                    history,
                    request.summary,
                    request.financial_context,
                    request.consumption_context,
                    previous_period,
                )
                if financial_agent.selected_tool == "coach_spending":
                    consumption_analysis = financial_agent.selected_tool_result
                if financial_agent.selected_tool == ASSET_ANALYSIS_TOOL:
                    asset_analysis = financial_agent.selected_tool_result
                goal_interview = None
                if financial_agent.selected_tool == FINANCIAL_GOAL_TOOL:
                    if request.goal_already_exists:
                        answer = self._existing_goal_message()
                    else:
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
            consumption_analysis=consumption_analysis,
            asset_analysis=asset_analysis,
        )

    def _continue_goal_interview(
        self,
        request: ChatRequest,
    ) -> tuple[str, GoalInterviewResponse]:
        draft = request.goal_draft
        assert draft is not None
        normalized_message = self._normalize_message(request.message)
        if self._is_cancellation(normalized_message):
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
            roadmap, roadmap_error = self._generate_confirmed_goal_roadmap(confirmed)
            roadmap_message = (
                f"AI 로드맵 {len(roadmap.steps)}단계를 생성했습니다."
                if roadmap is not None
                else "AI 로드맵 생성에 실패했지만 목표는 정상적으로 확정됩니다."
            )
            return (
                f"'{confirmed.title}' 목표를 확정했습니다. {roadmap_message}",
                GoalInterviewResponse(
                    action=GoalInterviewAction.CONFIRM,
                    active=False,
                    draft=confirmed,
                    roadmap=roadmap,
                    roadmap_error=roadmap_error,
                ),
            )
        return self._run_goal_agent(request, draft)

    def _generate_confirmed_goal_roadmap(
        self,
        draft: GoalDraft,
    ) -> tuple[GoalRoadmap | None, str | None]:
        try:
            feasibility = calculate_feasibility(draft)
            if feasibility.required_monthly_amount is None:
                raise ValueError("월 필요 저축액을 계산할 수 없습니다.")
            if draft.title is None or draft.goal_type is None:
                raise ValueError("목표 제목과 유형이 필요합니다.")
            if draft.target_amount is None or draft.current_amount is None:
                raise ValueError("목표 금액과 현재 준비금이 필요합니다.")
            if draft.target_date is None:
                raise ValueError("목표일이 필요합니다.")

            roadmap = generate_goal_roadmap(
                self.client,
                RoadmapGoal(
                    title=draft.title,
                    goalType=draft.goal_type.value,
                    targetAmount=draft.target_amount,
                    currentAmount=draft.current_amount,
                    targetDate=draft.target_date,
                    requiredMonthlyAmount=feasibility.required_monthly_amount,
                    motivation=draft.motivation,
                ),
            )
            logger.info(
                "[AI ROADMAP] generated steps=%s",
                len(roadmap.steps),
            )
            return roadmap, None
        except Exception as error:
            logger.exception("[AI ROADMAP] generation failed after goal confirmation")
            return None, str(error)[:500]

    def _normalize_message(self, message: str) -> str:
        return re.sub(r"[\s.!?~]+", "", message).lower()

    def _is_goal_setting_mode(self, chat_mode: str | None) -> bool:
        return (chat_mode or "").strip().upper() == GOAL_SETTING_MODE

    def _is_cancellation(self, normalized_message: str) -> bool:
        return "취소" in normalized_message or normalized_message in {"그만", "그만할래"}

    def _is_goal_creation_request(self, normalized_message: str) -> bool:
        if "목표" not in normalized_message:
            return False
        return any(
            term in normalized_message
            for term in ("새", "다른", "추가", "만들", "설정", "세우", "바꾸")
        )

    def _existing_goal_message(self) -> str:
        return "이미 금융 목표가 설정되어 있습니다. 한 사람당 하나의 목표만 설정할 수 있어 새 목표를 추가할 수 없습니다."

    def _no_active_goal_message(self, goal_already_exists: bool) -> str:
        if goal_already_exists:
            return self._existing_goal_message()
        return "현재 확정할 진행 중인 목표가 없습니다. 새 목표를 설정하려면 목표 내용을 말씀해 주세요."

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
                "이대로확정할게",
                "확정",
                "확정할게",
            }
            or "확정해" in normalized_message
        )
