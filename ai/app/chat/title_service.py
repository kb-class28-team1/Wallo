"""LLM 호출 없이 대화 제목을 결정적으로 생성한다."""

import re


MAX_TITLE_LENGTH = 30
DEFAULT_TITLE = "금융 상담"

_TITLE_RULES = (
    (("비상금", "비상 자금"), "비상금 마련"),
    (("전세", "월세", "주거 자금"), "주거 자금 마련"),
    (("여행", "유럽", "해외여행"), "여행 자금 마련"),
    (("예금", "적금", "저축상품", "금융상품", "상품 추천", "상품추천", "금리"), "금융상품 추천"),
    (("자산", "순자산", "부채", "재무"), "자산·재무 점검"),
    (("소비", "지출", "가계부", "예산"), "소비·예산 점검"),
    (("목표", "저축"), "재무 목표 설정"),
)

_GREETING_PATTERN = re.compile(
    r"^(안녕(?:하세요)?|반가워요?|hello|hi)[,!?.~\s]*$",
    re.IGNORECASE,
)
_REQUEST_SUFFIX_PATTERN = re.compile(
    r"(?:해줘|해주세요|알려줘|알려주세요|도와줘|도와주세요|부탁해|궁금해요?)$",
    re.IGNORECASE,
)


def _normalize_message(user_message: str) -> str:
    return re.sub(r"\s+", " ", user_message or "").strip(" \t\r\n\"'")


def _fallback_title(message: str) -> str:
    sentence = re.split(r"[.!?。！？]+", message, maxsplit=1)[0].strip()
    sentence = _REQUEST_SUFFIX_PATTERN.sub("", sentence).strip()
    sentence = re.sub(r"\s+", " ", sentence)
    if sentence.endswith("하고 싶어") or sentence.endswith("하고 싶어요"):
        sentence = sentence.rsplit("하고", 1)[0].strip() + " 계획"
    if len(sentence) > MAX_TITLE_LENGTH:
        sentence = sentence[:MAX_TITLE_LENGTH].rstrip()
    return sentence or DEFAULT_TITLE


def build_conversation_title(user_message: str) -> str:
    """첫 사용자 메시지에서 추가 AI 호출 없이 대화 제목을 만든다."""
    message = _normalize_message(user_message)
    if not message or _GREETING_PATTERN.fullmatch(message):
        return DEFAULT_TITLE

    normalized = message.casefold()
    for keywords, title in _TITLE_RULES:
        if any(keyword.casefold() in normalized for keyword in keywords):
            return title
    return _fallback_title(message)
