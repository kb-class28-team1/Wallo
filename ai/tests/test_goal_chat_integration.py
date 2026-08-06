import json
from datetime import date
from types import SimpleNamespace
from unittest.mock import Mock

from app.agents.goal.models import (
    GoalDraft,
    GoalInterviewAction,
    GoalPriority,
    GoalType,
    InterviewState,
)
from app.chat.schemas import ChatRequest
from app.chat.service import ChatService


def completion(message):
    return SimpleNamespace(choices=[SimpleNamespace(message=message)])


def complete_draft(state=InterviewState.CONFIRMATION):
    return GoalDraft(
        state=state,
        title="유럽 여행 자금",
        goal_type=GoalType.TRAVEL,
        target_amount=8_000_000,
        target_date=date(2027, 6, 1),
        motivation="취업 3주년 기념 여행",
        priority=GoalPriority.HIGH,
        current_amount=2_000_000,
        monthly_contribution=700_000,
    )


def test_financial_goal_tool_starts_structured_goal_interview():
    client = Mock()
    goal_tool_call = SimpleNamespace(
        function=SimpleNamespace(
            name="set_financial_goal",
            arguments=json.dumps({"request": "여행 목표를 세우고 싶어"}),
        )
    )
    client.chat.completions.create.side_effect = [
        completion(SimpleNamespace(content=None, tool_calls=[goal_tool_call])),
        completion(SimpleNamespace(
            content=json.dumps({
                "title": "유럽 여행 자금",
                "goal_type": "TRAVEL",
                "target_amount": 8_000_000,
            }, ensure_ascii=False),
            tool_calls=None,
        )),
    ]

    response = ChatService(client).chat(
        ChatRequest(message="유럽 여행 자금 800만 원을 모으고 싶어"),
    )

    assert response.goal_interview is not None
    assert response.goal_interview.action == GoalInterviewAction.CONTINUE
    assert response.goal_interview.active is True
    assert response.goal_interview.draft.goal_type == GoalType.TRAVEL
    assert response.answer == "언제까지 이 목표를 달성하고 싶으세요?"
    assert client.chat.completions.create.call_count == 2


def test_active_confirmation_is_confirmed_without_another_llm_call():
    client = Mock()

    response = ChatService(client).chat(
        ChatRequest(message="응, 확정해줘", goal_draft=complete_draft()),
    )

    assert response.goal_interview is not None
    assert response.goal_interview.action == GoalInterviewAction.CONFIRM
    assert response.goal_interview.active is False
    assert response.goal_interview.draft.state == InterviewState.COMPLETED
    assert response.goal_interview.draft.confirmed is True
    client.chat.completions.create.assert_not_called()


def test_active_goal_interview_can_be_cancelled_without_another_llm_call():
    client = Mock()

    response = ChatService(client).chat(
        ChatRequest(
            message="목표 설정 취소할게",
            goal_draft=complete_draft(InterviewState.DETAILING),
        ),
    )

    assert response.goal_interview is not None
    assert response.goal_interview.action == GoalInterviewAction.CANCEL
    assert response.goal_interview.draft.state == InterviewState.CANCELLED
    client.chat.completions.create.assert_not_called()


def test_goal_response_serializes_with_spring_camel_case_contract():
    response = ChatService(Mock()).chat(
        ChatRequest(message="네", goal_draft=complete_draft()),
    )

    payload = response.model_dump(mode="json", by_alias=True)

    assert payload["goalInterview"]["draft"]["goalType"] == "TRAVEL"
    assert payload["goalInterview"]["draft"]["targetAmount"] == 8_000_000
    assert payload["goalInterview"]["draft"]["monthlyContribution"] == 700_000
