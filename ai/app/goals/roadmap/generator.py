import re

from groq import Groq

from app.goals.roadmap.models import GoalRoadmap, RoadmapGoal
from app.core.ai_timing import timed_groq_completion
from app.core.config import get_groq_model


SYSTEM_PROMPT = """
당신은 금융 목표 실행 로드맵 설계자입니다. 제공된 확정 목표를 현실적인 단계로 나누세요.
매월 같은 행동을 반복하는 납입 일정표를 만들지 말고 의미가 달라지는 핵심 마일스톤만 만드세요.
6개월 이하는 3~5개, 7~24개월은 4~8개, 24개월 초과는 6~10개를 권장합니다.
각 targetAmount는 해당 시점까지
모아야 할 누적 금액입니다. 날짜와 금액은 계속 증가해야 하며 마지막 단계는 반드시 입력의
targetDate와 targetAmount와 정확히 같아야 합니다. 입력에 없는 수익률은 가정하지 마세요.
각 단계에는 stepNumber, description, targetDate, targetAmount, actionItems를 반드시 작성하세요.
title과 monthlyContribution도 가능한 한 작성하고, 모든 설명과 actionItems는 한국어로 작성하세요.
금액을 제목·설명·행동 항목에 쓸 때 M, K 같은 영문 약어를 사용하지 말고
`3M`은 `3백만원`, `1M`은 `1백만원`처럼 한국어 단위로 작성하세요.
각 단계의 제목·목적·행동은 서로 달라야 하며 동일한 문구를 반복하지 마세요.

goalType이 EMERGENCY_FUND이면 다음 행동을 단계별로 자연스럽게 배치하세요.
- 생활비 계좌와 분리된 비상금 전용 계좌 선택
- 급여일 직후 자동이체 설정
- 월 필수생활비를 기준으로 비상금 보장 개월 수 점검
- 비상금 사용 가능 조건과 사용 금지 항목 정의
- 중간 인출이 생겼을 때 원래 금액으로 복구하는 원칙 수립
- 목표 달성 후 유지·보충 점검 주기 결정
비상금을 소비하거나 수익 추구형 투자자산에 넣도록 권하지 마세요.
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

ROADMAP_MAX_COMPLETION_TOKENS = 1024
MILLION_AMOUNT_PATTERN = re.compile(
    r"(?<![A-Za-z0-9])(?P<amount>\d+(?:[.,]\d+)?)\s*"
    r"(?:[mM]|million)(?:\s*원)?(?![A-Za-z0-9])"
)


def normalize_roadmap_text(value: str | None) -> str | None:
    if value is None:
        return None
    return MILLION_AMOUNT_PATTERN.sub(r"\g<amount>백만원", value)


def generate_goal_roadmap(
    client: Groq,
    goal: RoadmapGoal,
    model: str | None = None,
) -> GoalRoadmap:
    resolved_model = model or get_groq_model()
    requested_completion_tokens = ROADMAP_MAX_COMPLETION_TOKENS
    goal_payload = goal.model_dump_json(by_alias=True, exclude_none=True)
    with timed_groq_completion(
        client,
        operation="goal.roadmap",
        model=resolved_model,
        requested_completion_tokens=requested_completion_tokens,
    ) as timing:
        completion = timing.create(
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": "다음 확정 목표의 로드맵을 생성하세요.\n"
                    + goal_payload,
                },
            ],
            tools=[TOOL_SCHEMA],
            tool_choice={"type": "function", "function": {"name": TOOL_NAME}},
            temperature=0,
            reasoning_effort="low",
            include_reasoning=False,
            max_completion_tokens=requested_completion_tokens,
        )
        tool_calls = completion.choices[0].message.tool_calls
        if not tool_calls:
            raise ValueError("AI가 로드맵 도구 응답을 반환하지 않았습니다.")
        roadmap = GoalRoadmap.model_validate_json(tool_calls[0].function.arguments)
        normalized_steps = [
            step.model_copy(update={
                "title": normalize_roadmap_text(
                    step.title or f"{step.sequence}단계 목표"
                ),
                "description": normalize_roadmap_text(step.description),
                "action_items": [
                    normalize_roadmap_text(action_item)
                    for action_item in step.action_items
                ],
                "monthly_contribution": (
                    step.monthly_contribution
                    if step.monthly_contribution is not None
                    else goal.required_monthly_amount
                ),
            })
            for step in roadmap.steps
        ]
        roadmap = roadmap.model_copy(update={
            "summary": normalize_roadmap_text(roadmap.summary),
            "strategy": normalize_roadmap_text(roadmap.strategy),
            "steps": normalized_steps,
        })
        return roadmap.validate_for(goal)
