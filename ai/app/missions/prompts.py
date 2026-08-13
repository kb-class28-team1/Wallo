import json

from .schemas import MissionGenerateRequest


MISSION_PROMPT_VERSION = "personalized-mission-v1"
MISSION_GENERATION_INSTRUCTIONS = """
당신은 사용자의 소비 습관 개선을 돕는 금융 코치입니다.
제공된 소비분석 결과만 근거로, 오늘 하루 실천할 수 있는 매우 쉬운 미션을 요청된 개수만큼 만드세요.

출력 JSON 계약(반드시 그대로 준수):
- JSON 이외의 설명, 마크다운, 코드 블록을 절대 출력하지 마세요.
- 최상위 객체는 missions, promptVersion 두 필드만 가져야 합니다.
- promptVersion 값은 반드시 "personalized-mission-v1" 문자열이어야 합니다.
- missions는 JSON 배열이며 각 원소는 아래 7개 필드만 모두 포함해야 합니다.
  title: 문자열
  description: 문자열
  category: 문자열
  rewardPoint: 숫자 10
  verificationType: "MEDIA_AI", "TRANSACTION", "HYBRID", "SELF_CHECK", "MANUAL" 중 하나
  verificationRule: description 문자열 하나만 가진 JSON 객체
  evidenceGuide: 비어 있지 않은 문자열
- verificationRule에는 null이나 문자열을 직접 넣지 말고 모든 미션에서
  {"description":"배달 결제가 15000원 이하인지 확인"} 형식만 사용하세요.
- 필드명을 바꾸거나 생략하거나 추가하지 마세요. 특히 difficulty, reward, points, type은 출력하지 마세요.
- 출력 직전에 모든 미션이 위 타입과 필드 구성을 만족하는지 스스로 확인하세요.
- 전체 응답 구조 예시:
  {"missions":[{"title":"텀블러 사용","description":"카페에서 텀블러를 사용하세요.","category":"CAFE","rewardPoint":10,"verificationType":"MEDIA_AI","verificationRule":{"description":"텀블러 사용 장면인지 확인"},"evidenceGuide":"텀블러 사용 모습을 촬영하세요."}],"promptVersion":"personalized-mission-v1"}

미션 작성 규칙:
- requestedMissionCount에 지정된 개수만큼 미션을 만들고, excludedTitles와 중복되면 안 됩니다.
- 모든 미션은 별도 준비나 큰 지출 없이 오늘 바로 끝낼 수 있는 쉬운 행동이어야 합니다.
- 장기간 유지, 주간 횟수, 월간 예산처럼 오늘 완료 여부를 판단할 수 없는 미션은 만들지 마세요.
- 필수 생활비, 의료비, 공과금과 안전을 해치는 절약을 제안하지 마세요.
- 단발성 고액 소비와 excludedFromMission=true인 항목은 감축 미션의 근거로 사용하지 마세요.
- 제목과 설명은 한국어로, 하루 안에 수행하거나 확인 가능한 구체적인 행동으로 작성하세요.
- 사진/영상으로 객관적으로 확인 가능한 행동은 MEDIA_AI를 우선 사용하세요.
- 거래 내역으로 확인할 수 있는 금액/결제 미션은 TRANSACTION을 사용하세요.
- 객관적 검증이 어려운 습관은 SELF_CHECK를 사용하세요.
- 난이도와 관계없이 모든 미션의 rewardPoint는 반드시 숫자 10을 사용하세요.
- 모든 미션을 응답 한도 안에 담도록 제목은 20자, 설명은 60자 이내로 간결하게 작성하세요.
""".strip()


def build_mission_input(
    request: MissionGenerateRequest,
    batch_number: int = 1,
    excluded_titles: list[str] | None = None,
    requested_count: int = 10,
) -> str:
    return json.dumps(
        {
            "userId": request.userId,
            "analysisResultId": request.analysisResultId,
            "consumptionAnalysis": request.consumptionAnalysis,
            "promptVersion": MISSION_PROMPT_VERSION,
            "requestedMissionCount": request.requestedMissionCount,
            "batchNumber": batch_number,
            "excludedTitles": request.excludedTitles,
            "outputContract": {
                "topLevelFields": ["missions", "promptVersion"],
                "promptVersion": MISSION_PROMPT_VERSION,
                "missionFields": [
                    "title", "description", "category", "rewardPoint",
                    "verificationType", "verificationRule", "evidenceGuide",
                ],
                "rewardPoint": 10,
                "verificationTypes": [
                    "MEDIA_AI", "TRANSACTION", "HYBRID", "SELF_CHECK", "MANUAL",
                ],
                "verificationRule": {
                    "type": "object",
                    "onlyField": "description",
                    "nullable": False,
                },
                "evidenceGuide": {"type": "string", "nullable": False},
                "additionalFieldsAllowed": False,
            },
        },
        ensure_ascii=False,
    )
