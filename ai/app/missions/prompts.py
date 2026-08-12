import json

from .schemas import MissionGenerateRequest


MISSION_PROMPT_VERSION = "personalized-mission-v1"
MISSION_GENERATION_INSTRUCTIONS = """
당신은 사용자의 소비 습관 개선을 돕는 금융 코치입니다.
제공된 소비분석 결과만 근거로, 앞으로 2주 동안 실천할 수 있는 미션을 정확히 30개 만드세요.

규칙:
- 30개 미션은 의미와 행동 조건이 서로 중복되면 안 됩니다.
- 필수 생활비, 의료비, 공과금과 안전을 해치는 절약을 제안하지 마세요.
- 단발성 고액 소비와 excludedFromMission=true인 항목은 감축 미션의 근거로 사용하지 마세요.
- 제목과 설명은 한국어로, 하루 안에 수행하거나 확인 가능한 구체적인 행동으로 작성하세요.
- 사진/영상으로 객관적으로 확인 가능한 행동은 MEDIA_AI를 우선 사용하세요.
- 거래 내역으로 확인할 수 있는 금액/결제 미션은 TRANSACTION을 사용하세요.
- 객관적 검증이 어려운 습관은 SELF_CHECK를 사용하세요.
- 난이도와 보상은 EASY=10, NORMAL=20, HARD=30 포인트를 사용하세요.
- 반드시 JSON 객체 하나만 반환하세요. 최상위 필드는 missions와 promptVersion입니다.
- 모든 미션을 응답 한도 안에 담도록 제목은 20자, 설명은 60자 이내로 간결하게 작성하세요.
""".strip()


def build_mission_input(request: MissionGenerateRequest) -> str:
    return json.dumps(
        {
            "userId": request.userId,
            "analysisResultId": request.analysisResultId,
            "consumptionAnalysis": request.consumptionAnalysis,
            "promptVersion": MISSION_PROMPT_VERSION,
        },
        ensure_ascii=False,
    )
