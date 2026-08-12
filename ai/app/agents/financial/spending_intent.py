from __future__ import annotations

import re
from typing import Any


DIRECT_TERMS = (
    "소비", "지출", "과소비", "쓴 돈", "돈 쓴", "카드값", "결제 내역",
    "결제내역", "사용 금액", "사용금액", "예산", "정기결제", "충동구매",
    "배달", "카페", "쇼핑", "식비", "교통비", "문화비", "주말 소비",
    "평일 소비", "아끼고", "절약하고", "썼", "돈을 많이", "돈 많이",
)
FOLLOW_UP_TERMS = (
    "습관", "패턴", "많이", "가장 많이", "줄었", "늘었", "증가", "감소",
    "아끼", "절약", "어디에", "주말", "평일", "요일", "시간대", "그때",
    "그 기간", "그중", "거기서", "정기결제", "충동구매",
)


def detect_spending_focus(message: str) -> str:
    normalized = re.sub(r"\s+", "", message or "").lower()
    if "예산" in normalized:
        return "BUDGET"
    if "정기결제" in normalized or "구독" in normalized:
        return "SUBSCRIPTION"
    if any(term in normalized for term in ("습관", "패턴", "주말", "평일", "요일", "시간대")):
        return "PATTERN"
    if any(term in normalized for term in ("아끼", "절약", "잘하고", "개선")):
        return "IMPROVEMENT"
    if any(term in normalized for term in ("어디에", "카테고리", "배달", "카페", "쇼핑", "식비", "교통비", "문화비")):
        return "CATEGORY"
    if any(term in normalized for term in ("늘었", "줄었", "증가", "감소", "비교", "차이")):
        return "CHANGE"
    if any(term in normalized for term in ("충동구매", "고액", "새로생긴")):
        return "SIGNALS"
    return "OVERVIEW"


def is_spending_request(
    message: str,
    previous_period: dict[str, Any] | None = None,
) -> bool:
    normalized = re.sub(r"\s+", " ", message or "").strip().lower()
    if any(term in normalized for term in DIRECT_TERMS):
        return True
    return bool(previous_period) and (
        _explicit_period(message) is not None
        or any(term in normalized for term in FOLLOW_UP_TERMS)
    )


def _explicit_period(message: str) -> dict[str, Any] | None:
    normalized = re.sub(r"\s+", "", message or "")
    iso_dates = re.findall(r"(20\d{2}-\d{1,2}-\d{1,2})", normalized)
    if len(iso_dates) >= 2:
        return {
            "periodType": "CUSTOM",
            "startDate": iso_dates[0],
            "endDate": iso_dates[1],
        }

    korean_dates = re.findall(
        r"(20\d{2})년(\d{1,2})월(\d{1,2})일", normalized
    )
    if len(korean_dates) >= 2:
        formatted = [f"{int(y):04d}-{int(m):02d}-{int(d):02d}"
                     for y, m, d in korean_dates[:2]]
        return {
            "periodType": "CUSTOM",
            "startDate": formatted[0],
            "endDate": formatted[1],
        }

    month = re.search(r"(?:(20\d{2})년)?(1[0-2]|0?[1-9])월", normalized)
    if month:
        result: dict[str, Any] = {
            "periodType": "MONTHLY",
            "targetMonth": int(month.group(2)),
        }
        if month.group(1):
            result["targetYear"] = int(month.group(1))
        return result

    if any(term in normalized for term in ("전전월", "지지난달", "두달전")):
        return {"periodType": "MONTHLY", "periodOffset": -2}
    if any(term in normalized for term in ("지난달", "저번달", "전월")):
        return {"periodType": "MONTHLY", "periodOffset": -1}
    if any(term in normalized for term in ("이번달", "당월")):
        return {"periodType": "MONTHLY", "periodOffset": 0}
    if any(term in normalized for term in ("지지난주", "전전주")):
        return {"periodType": "WEEKLY", "periodOffset": -2}
    if any(term in normalized for term in ("지난주", "저번주")):
        return {"periodType": "WEEKLY", "periodOffset": -1}
    if "이번주" in normalized:
        return {"periodType": "WEEKLY", "periodOffset": 0}
    return None


def build_spending_arguments(
    message: str,
    previous_period: dict[str, Any] | None = None,
) -> dict[str, Any]:
    arguments = _explicit_period(message)
    if arguments is None and previous_period:
        arguments = {
            "periodType": "CUSTOM",
            "startDate": previous_period.get("startDate"),
            "endDate": previous_period.get("endDate"),
            "comparisonStartDate": previous_period.get("compareStart"),
            "comparisonEndDate": previous_period.get("compareEnd"),
            "periodLabel": previous_period.get("label"),
        }
    if arguments is None:
        arguments = {"periodType": "MONTHLY", "periodOffset": 0}
    return {"request": message, "focus": detect_spending_focus(message), **arguments}
