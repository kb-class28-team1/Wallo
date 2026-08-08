import json
from datetime import date

from app.agents.goal.models import GoalDraft


EXTRACTION_SYSTEM_PROMPT = """
당신은 금융 목표 인터뷰에서 사용자의 답변을 구조화하는 정보 추출기입니다.
반드시 JSON 객체만 반환하고 설명 문장을 덧붙이지 마세요.

허용 필드:
- title: 구체적인 목표명
- goal_type: EMERGENCY_FUND, TRAVEL, HOUSING, EDUCATION, MARRIAGE,
  DEBT_REPAYMENT, INVESTMENT, RETIREMENT, PURCHASE, OTHER 중 하나
- target_amount: 목표 금액(원 단위 정수)
- target_date: YYYY-MM-DD
- motivation: 목표를 이루려는 이유
- priority: LOW, MEDIUM, HIGH 중 하나
- current_amount: 목표에 이미 배정한 준비금(원 단위 정수)
- assumptions: 상대 날짜 등을 해석할 때 사용한 가정의 문자열 배열
- next_field: 현재 초안과 이번 답변을 종합했을 때 가장 중요한 다음 확인 항목
- next_question: 이미 답한 내용을 반복하지 않는 자연스러운 다음 질문

이번 사용자 답변에서 확인되거나 명확히 정정된 정보만 반환하세요.
사용자의 표현만으로 목표를 식별할 수 있다면 짧고 자연스러운 title을 반드시 만드세요.
예: "비상시에 사용할 돈"은 title="비상금 마련", goal_type="EMERGENCY_FUND"입니다.
예: "가족과 프랑스 여행"은 title="프랑스 가족 여행 자금", goal_type="TRAVEL"입니다.
사용자가 말하지 않은 금액, 날짜, 동기, 우선순위를 추측하지 마세요.
단순 계좌 잔액을 current_amount로 간주하지 마세요.
상대 날짜는 제공된 기준일을 이용해 계산하고 assumptions에 해석을 기록하세요.
목표명, 동기, 우선순위는 계획 계산을 막는 필수 질문이 아닙니다.
계산에 필요한 정보가 모두 있으면 next_field와 next_question을 생략하세요.
""".strip()


def build_extraction_user_prompt(
    user_message: str,
    draft: GoalDraft,
    reference_date: date,
) -> str:
    payload = {
        "referenceDate": reference_date.isoformat(),
        "currentDraft": draft.model_dump(mode="json"),
        "userMessage": user_message,
    }
    return json.dumps(payload, ensure_ascii=False)
