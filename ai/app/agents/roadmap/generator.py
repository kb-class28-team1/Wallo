import json

from groq import Groq

from app.agents.roadmap.models import GoalRoadmap, RoadmapGoal
from app.core.config import get_groq_model


SYSTEM_PROMPT = """
당신은 금융 목표 실행 로드맵 설계자입니다. 제공된 확정 목표를 현실적인 단계로 나누세요.
단계 수를 고정하지 말고 목표 기간에 맞추되 2~24개로 작성하세요. 단기 목표는 월별,
중장기 목표는 분기·반기별 핵심 점검 시점을 사용하세요. 각 targetAmount는 해당 시점까지
모아야 할 누적 금액입니다. 날짜와 금액은 계속 증가해야 하며 마지막 단계는 반드시 입력의
targetDate와 targetAmount와 정확히 같아야 합니다. 입력에 없는 수익률은 가정하지 마세요.
각 단계에는 stepNumber, description, targetDate, targetAmount, actionItems를 반드시 작성하세요.
title과 monthlyContribution은 선택 항목입니다. 모든 설명과 actionItems는 한국어로 작성하세요.
""".strip()

TOOL_NAME = "submit_goal_roadmap"
TOOL_SCHEMA = {
    "type": "function",
    "function": {
        "name": TOOL_NAME,
        "description": "기간에 맞는 가변 개수의 금융 목표 로드맵을 제출한다.",
        "parameters": GoalRoadmap.model_json_schema(by_alias=True),
    },
}


def generate_goal_roadmap(client: Groq, goal: RoadmapGoal, model: str | None = None) -> GoalRoadmap:
    completion = client.chat.completions.create(
        model=model or get_groq_model(),
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": "다음 확정 목표의 로드맵을 생성하세요.\n"
                + goal.model_dump_json(by_alias=True),
            },
        ],
        tools=[TOOL_SCHEMA],
        tool_choice={"type": "function", "function": {"name": TOOL_NAME}},
        temperature=0,
        reasoning_effort="low",
        include_reasoning=False,
        max_completion_tokens=4000,
    )
    tool_calls = completion.choices[0].message.tool_calls
    if not tool_calls:
        raise ValueError("AI가 로드맵 도구 응답을 반환하지 않았습니다.")
    roadmap = GoalRoadmap.model_validate_json(tool_calls[0].function.arguments)
    normalized_steps = [
        step.model_copy(update={
            "title": step.title or f"{step.sequence}단계 목표",
            "monthly_contribution": (
                step.monthly_contribution
                if step.monthly_contribution is not None
                else goal.required_monthly_amount
            ),
        })
        for step in roadmap.steps
    ]
    roadmap = roadmap.model_copy(update={"steps": normalized_steps})
    return roadmap.validate_for(goal)
