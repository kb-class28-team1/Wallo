import json
from datetime import date
from types import SimpleNamespace
from unittest.mock import Mock, patch

from app.agents.goal.models import (
    GoalDraft,
    GoalField,
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
    )


def test_financial_goal_tool_starts_structured_goal_interview():
    client = Mock()
    goal_tool_call = SimpleNamespace(
        function=SimpleNamespace(
            name="set_financial_goal",
            arguments=json.dumps({"request": "여행 목표를 세우고 싶어"}),
        )
    )
    extraction_tool_call = SimpleNamespace(
        function=SimpleNamespace(
            name="extract_financial_goal",
            arguments=json.dumps({
                "title": "유럽 여행 자금",
                "goal_type": "TRAVEL",
                "target_amount": 8_000_000,
            }, ensure_ascii=False),
        )
    )
    client.chat.completions.create.side_effect = [
        completion(SimpleNamespace(content=None, tool_calls=[goal_tool_call])),
        completion(SimpleNamespace(
            content=None,
            tool_calls=[extraction_tool_call],
        )),
    ]

    response = ChatService(client).chat(
        ChatRequest(message="유럽 여행 자금 800만 원을 모으고 싶어"),
    )

    assert response.goal_interview is not None
    assert response.goal_interview.action == GoalInterviewAction.CONTINUE
    assert response.goal_interview.active is True
    assert response.goal_interview.draft.goal_type == GoalType.TRAVEL
    assert response.answer == "유럽 여행 자금을 언제까지 마련하고 싶으세요?"
    assert client.chat.completions.create.call_count == 2


def test_partial_goal_tool_response_continues_with_next_question():
    client = Mock()
    routing_call = SimpleNamespace(
        function=SimpleNamespace(
            name="set_financial_goal",
            arguments=json.dumps({"request": "비상금 목표"}),
        )
    )
    extraction_call = SimpleNamespace(
        function=SimpleNamespace(
            name="extract_financial_goal",
            arguments=json.dumps({
                "title": "비상금 마련",
                "goal_type": "EMERGENCY_FUND",
                "target_amount": None,
                "target_date": None,
                "motivation": None,
                "priority": None,
                "current_amount": None,
                "assumptions": [],
                "next_field": "targetAmount",
                "next_question": "목표 금액을 알려주실 수 있나요?",
            }, ensure_ascii=False),
        )
    )
    client.chat.completions.create.side_effect = [
        completion(SimpleNamespace(content=None, tool_calls=[routing_call])),
        completion(SimpleNamespace(content=None, tool_calls=[extraction_call])),
    ]

    response = ChatService(client).chat(
        ChatRequest(message="비상금을 마련하고 싶어"),
    )

    assert response.goal_interview is not None
    assert response.goal_interview.active is True
    assert response.goal_interview.draft.title == "비상금 마련"
    assert response.goal_interview.draft.missing_fields == [
        GoalField.TARGET_AMOUNT,
        GoalField.TARGET_DATE,
        GoalField.CURRENT_AMOUNT,
    ]
    assert response.answer == "목표 금액을 알려주실 수 있나요?"
    assert client.chat.completions.create.call_count == 2


def test_explicit_goal_setting_mode_starts_goal_interview_without_financial_router():
    client = Mock()
    goal_result = SimpleNamespace(
        next_question="어떤 목표를 세우고 싶으신가요?",
        draft=complete_draft(InterviewState.DISCOVERY),
        feasibility=None,
    )

    with (
        patch("app.chat.service.FinancialAgent") as financial_agent,
        patch("app.chat.service.GoalAgent") as goal_agent,
    ):
        goal_agent.return_value.run.return_value = goal_result
        response = ChatService(client).chat(
            ChatRequest(
                message="목표를 설정하고 싶어요",
                chatMode="GOAL_SETTING",
            )
        )

    financial_agent.assert_not_called()
    goal_agent.return_value.run.assert_called_once()
    assert response.goal_interview is not None
    assert response.goal_interview.active is True
    assert response.answer == "어떤 목표를 세우고 싶으신가요?"


def test_natural_emergency_goal_reaches_review_even_when_model_extraction_fails():
    client = Mock()
    routing_call = SimpleNamespace(
        function=SimpleNamespace(
            name="set_financial_goal",
            arguments='{"request":"비상금 목표"}',
        )
    )
    invalid_extraction_call = SimpleNamespace(
        function=SimpleNamespace(
            name="extract_financial_goal",
            arguments="invalid-json",
        )
    )
    client.chat.completions.create.side_effect = [
        completion(SimpleNamespace(content=None, tool_calls=[routing_call])),
        completion(SimpleNamespace(content=None, tool_calls=[invalid_extraction_call])),
        completion(SimpleNamespace(content=None, tool_calls=[invalid_extraction_call])),
    ]

    response = ChatService(client).chat(ChatRequest(message=(
        "내년 11월까지 비상금 1000만 원을 모으고 싶고, "
        "현재 200만 원이 있으며 매달 50만 원씩 저축할 수 있어"
    )))

    assert response.goal_interview is not None
    assert response.goal_interview.draft.title == "비상금 마련"
    assert response.goal_interview.draft.state == InterviewState.CONFIRMATION
    assert response.goal_interview.draft.missing_fields == []
    assert "이 계획으로 확정" in response.answer


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
    assert client.chat.completions.create.call_count == 1
    assert "로드맵 생성에 실패" in response.answer


def test_ui_confirmation_phrase_is_confirmed_without_another_llm_call():
    client = Mock()

    response = ChatService(client).chat(
        ChatRequest(message="이대로 확정할게", goal_draft=complete_draft()),
    )

    assert response.goal_interview is not None
    assert response.goal_interview.action == GoalInterviewAction.CONFIRM
    assert response.goal_interview.active is False
    assert response.goal_interview.draft.state == InterviewState.COMPLETED
    assert response.goal_interview.draft.confirmed is True
    assert client.chat.completions.create.call_count == 1


def test_confirmation_generates_and_saves_goal_roadmap():
    client = Mock()
    roadmap_arguments = {
        "summary": "여행 목표 핵심 로드맵",
        "strategy": "전용 계좌에 적립하고 핵심 시점마다 점검한다.",
        "steps": [
            {
                "stepNumber": 1,
                "title": "여행 계좌 만들기",
                "description": "여행 자금을 분리한다.",
                "targetDate": "2026-12-01",
                "targetAmount": 4000000,
                "monthlyContribution": 600000,
                "actionItems": ["전용 계좌 선택"],
            },
            {
                "stepNumber": 2,
                "title": "여행 자금 완성",
                "description": "최종 금액을 확인한다.",
                "targetDate": "2027-06-01",
                "targetAmount": 8000000,
                "monthlyContribution": 600000,
                "actionItems": ["최종 잔액 확인"],
            },
        ],
    }
    roadmap_call = SimpleNamespace(
        function=SimpleNamespace(arguments=json.dumps(roadmap_arguments, ensure_ascii=False)),
    )
    client.chat.completions.create.return_value = completion(
        SimpleNamespace(content=None, tool_calls=[roadmap_call]),
    )

    response = ChatService(client).chat(
        ChatRequest(message="확정해줘", goal_draft=complete_draft()),
    )

    assert "AI 로드맵 2단계를 생성했습니다" in response.answer
    assert response.goal_interview.roadmap is not None
    assert len(response.goal_interview.roadmap.steps) == 2


def test_confirmation_without_active_goal_does_not_start_an_empty_interview():
    client = Mock()

    response = ChatService(client).chat(
        ChatRequest(message="확정할게", goalAlreadyExists=True),
    )

    assert response.goal_interview is None
    assert "이미 금융 목표가 설정되어 있습니다" in response.answer
    client.chat.completions.create.assert_not_called()


def test_existing_goal_blocks_a_second_goal_interview():
    client = Mock()

    response = ChatService(client).chat(
        ChatRequest(
            message="새로운 여행 목표를 만들고 싶어",
            goalAlreadyExists=True,
        ),
    )

    assert response.goal_interview is None
    assert "한 사람당 하나의 목표" in response.answer
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
    assert "monthlyContribution" not in payload["goalInterview"]["draft"]
