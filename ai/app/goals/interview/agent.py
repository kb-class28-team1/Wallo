from datetime import date

from groq import Groq

from app.goals.interview.context import FinancialContext
from app.goals.interview.extractor import GoalExtractor
from app.goals.interview.models import GoalDraft, GoalInterviewResult
from app.goals.interview.service import GoalInterviewService


class GoalAgent:
    """목표 정보 추출과 인터뷰 규칙을 조합하는 목표 설정 Agent."""

    def __init__(self, client: Groq, model: str | None = None):
        self.interview_service = GoalInterviewService(
            GoalExtractor(client, model=model),
        )

    def run(
        self,
        user_message: str,
        draft: GoalDraft | None = None,
        financial_context: FinancialContext | None = None,
        reference_date: date | None = None,
    ) -> GoalInterviewResult:
        return self.interview_service.process(
            user_message,
            draft,
            financial_context,
            reference_date,
        )
