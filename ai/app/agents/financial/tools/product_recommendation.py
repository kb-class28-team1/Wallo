import csv
import re
from pathlib import Path
from typing import Any

from app.agents.base import ToolResult


NAME = "recommend_financial_products"
SCHEMA = {
    "type": "function",
    "function": {
        "name": NAME,
        "description": (
            "사용자의 금액, 기간, 가입 방식에 맞는 정기예금 또는 적금 상품을 "
            "금융감독원 수집 데이터에서 탐색하고 비교한다."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "request": {
                    "type": "string",
                    "description": "상품 추천에 관한 사용자의 원래 요청",
                },
                "productType": {
                    "type": ["string", "null"],
                    "enum": ["deposit", "saving", None],
                    "description": "정기예금은 deposit, 적금은 saving",
                },
                "termMonths": {
                    "type": ["integer", "null"],
                    "minimum": 1,
                    "maximum": 120,
                    "description": "희망 가입기간(개월)",
                },
                "amountKrw": {
                    "type": ["integer", "null"],
                    "minimum": 1,
                    "description": "예금은 총 예치금, 적금은 월 납입금(원)",
                },
                "joinPreference": {
                    "type": ["string", "null"],
                    "enum": ["any", "online", "branch", None],
                    "description": (
                        "가입 방식 무관은 any, 인터넷·스마트폰은 online, "
                        "은행 방문·영업점은 branch"
                    ),
                },
                "includeRestricted": {
                    "type": ["boolean", "null"],
                    "description": "가입 대상이 제한된 상품도 포함할지 여부",
                },
                "topN": {
                    "type": ["integer", "null"],
                    "minimum": 1,
                    "maximum": 5,
                    "description": "반환할 최대 상품 수",
                },
            },
            "required": ["request"],
            "additionalProperties": False,
        },
    },
}

AI_ROOT = Path(__file__).resolve().parents[4]
DATA_FILES = {
    "deposit": AI_ROOT / "data" / "raw" / "finlife_deposit_products.csv",
    "saving": AI_ROOT / "data" / "raw" / "finlife_saving_products.csv",
}
PRODUCT_TYPE_NAMES = {"deposit": "정기예금", "saving": "적금"}
ONLINE_KEYWORDS = ("인터넷", "스마트폰")
DEFAULT_TOP_N = 3
MAX_TOP_N = 5
INTEREST_TAX_RATE = 0.154


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    return " ".join(str(value).replace("\xa0", " ").split()).strip()


def parse_int(value: Any) -> int | None:
    text = clean_text(value).replace(",", "")
    if not text:
        return None
    try:
        return int(float(text))
    except ValueError:
        return None


def parse_rate(value: Any) -> float | None:
    text = clean_text(value).replace("%", "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def parse_max_limit(value: Any) -> int | None:
    text = clean_text(value).replace(",", "")
    if not text or text == "0":
        return None
    match = re.search(r"\d+", text)
    return int(match.group()) if match else None


def load_products(product_type: str) -> list[dict[str, str]]:
    data_file = DATA_FILES[product_type]
    try:
        with data_file.open(encoding="utf-8-sig", newline="") as file:
            return list(csv.DictReader(file))
    except OSError as error:
        raise RuntimeError(
            f"{PRODUCT_TYPE_NAMES[product_type]} CSV를 읽지 못했습니다."
        ) from error


def is_online_product(row: dict[str, str]) -> bool:
    join_way = clean_text(row.get("가입방법"))
    return any(keyword in join_way for keyword in ONLINE_KEYWORDS)


def is_branch_product(row: dict[str, str]) -> bool:
    return "영업점" in clean_text(row.get("가입방법"))


def is_restricted_product(row: dict[str, str]) -> bool:
    code = clean_text(row.get("가입제한 코드"))
    return bool(code and code != "1")


def build_score(row: dict[str, str], join_preference: str) -> float:
    base_rate = parse_rate(row.get("세전 이자율")) or 0.0
    preferential_rate = parse_rate(row.get("최고 우대금리"))
    bonus_rate = max(0.0, (preferential_rate or base_rate) - base_rate)
    # 달성 여부가 불확실한 우대금리는 일부만 반영하고 기본금리를 우선한다.
    score = base_rate + min(bonus_rate, 3.0) * 0.2
    if join_preference == "online" and is_online_product(row):
        score += 0.05
    elif join_preference == "branch" and is_branch_product(row):
        score += 0.05
    return round(score, 6)


def build_candidate(
        row: dict[str, str],
        product_type: str,
        score: float,
        amount_krw: int,
) -> dict[str, Any]:
    term_months = parse_int(row.get("저축기간(개월)")) or 0
    base_rate = parse_rate(row.get("세전 이자율")) or 0.0
    rate_type = clean_text(row.get("금리유형 코드")).upper()

    if product_type == "deposit":
        if rate_type == "M":
            gross_interest = amount_krw * (
                (1 + base_rate / 100 / 12) ** term_months - 1
            )
        else:
            gross_interest = (
                amount_krw * base_rate / 100 * term_months / 12
            )
        principal = amount_krw
    else:
        gross_interest = (
            amount_krw
            * base_rate
            / 100
            / 12
            * term_months
            * (term_months + 1)
            / 2
        )
        principal = amount_krw * term_months

    after_tax_interest = round(
        gross_interest * (1 - INTEREST_TAX_RATE)
    )

    candidate = {
        "productType": PRODUCT_TYPE_NAMES[product_type],
        "financialGroup": clean_text(row.get("권역명")),
        "companyCode": clean_text(row.get("금융회사 코드")),
        "companyName": clean_text(row.get("금융회사")),
        "productCode": clean_text(row.get("금융상품 코드")),
        "productName": clean_text(row.get("상품명")),
        "termMonths": term_months,
        "interestCalculation": clean_text(row.get("이자계산방식"))
        or clean_text(row.get("금리유형명")),
        "baseRatePercent": parse_rate(row.get("세전 이자율")),
        "afterTaxRatePercent": parse_rate(row.get("세후 이자율")),
        "preferentialRatePercent": parse_rate(row.get("최고 우대금리")),
        "joinWay": clean_text(row.get("가입방법")),
        "joinTarget": clean_text(row.get("가입 대상")),
        "preferentialConditions": clean_text(row.get("우대조건")),
        "maturityInterest": clean_text(row.get("만기 후 이자율")),
        "maximumLimitKrw": parse_max_limit(row.get("최고한도")),
        "disclosureMonth": clean_text(row.get("공시월")),
        "collectedAt": clean_text(row.get("수집일시")),
        "rankingScore": score,
        "estimatedAfterTaxInterestKrw": after_tax_interest,
        "estimatedMaturityAmountKrw": principal + after_tax_interest,
        "estimateAssumption": (
            "일반과세 15.4%, 기본금리 적용 단순 예시"
        ),
    }
    if product_type == "deposit":
        candidate["depositAmountKrw"] = amount_krw
    else:
        candidate.update({
            "savingType": clean_text(row.get("적립유형명")),
            "monthlyPaymentKrw": amount_krw,
        })
    return candidate


def recommend_products(
        product_type: str,
        term_months: int,
        amount_krw: int,
        join_preference: str = "any",
        include_restricted: bool = False,
        top_n: int = DEFAULT_TOP_N,
) -> tuple[list[dict[str, Any]], dict[str, int]]:
    rows = load_products(product_type)
    matched: list[tuple[float, dict[str, str]]] = []

    for row in rows:
        if parse_int(row.get("저축기간(개월)")) != term_months:
            continue
        if parse_rate(row.get("세전 이자율")) is None:
            continue
        if not include_restricted and is_restricted_product(row):
            continue
        if join_preference == "online" and not is_online_product(row):
            continue
        if join_preference == "branch" and not is_branch_product(row):
            continue
        maximum_limit = parse_max_limit(row.get("최고한도"))
        if maximum_limit is not None and amount_krw > maximum_limit:
            continue
        matched.append((build_score(row, join_preference), row))

    matched.sort(
        key=lambda item: (
            item[0],
            parse_rate(item[1].get("세전 이자율")) or 0.0,
            clean_text(item[1].get("금융회사")),
            clean_text(item[1].get("상품명")),
        ),
        reverse=True,
    )

    selected: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()
    for score, row in matched:
        key = (
            clean_text(row.get("금융회사 코드")),
            clean_text(row.get("금융상품 코드")),
        )
        if key in seen:
            continue
        seen.add(key)
        selected.append(
            build_candidate(row, product_type, score, amount_krw)
        )
        if len(selected) >= min(max(1, top_n), MAX_TOP_N):
            break

    return selected, {
        "loadedRows": len(rows),
        "matchedRows": len(matched),
        "returnedRows": len(selected),
    }


def execute(tool_name: str, arguments: dict[str, Any]) -> ToolResult:
    product_type = clean_text(arguments.get("productType"))
    term_months = parse_int(arguments.get("termMonths"))
    amount_krw = parse_int(arguments.get("amountKrw"))

    missing_fields: list[str] = []
    if product_type not in PRODUCT_TYPE_NAMES:
        missing_fields.append("예금 또는 적금 중 원하는 상품 유형")
    if term_months is None or term_months <= 0:
        missing_fields.append("희망 가입기간(개월)")
    if amount_krw is None or amount_krw <= 0:
        missing_fields.append("예금 총 예치금 또는 적금 월 납입금")

    if missing_fields:
        return ToolResult(
            status="needs_input",
            tool=tool_name,
            data={
                "request": clean_text(arguments.get("request")),
                "missingFields": missing_fields,
            },
            message="상품 추천에 필요한 정보를 사용자에게 간단히 질문하세요.",
        )

    try:
        join_preference = clean_text(
            arguments.get("joinPreference")
        ) or "any"
        if join_preference not in {"any", "online", "branch"}:
            join_preference = "any"

        products, search_summary = recommend_products(
            product_type=product_type,
            term_months=term_months,
            amount_krw=amount_krw,
            join_preference=join_preference,
            include_restricted=bool(arguments.get("includeRestricted", False)),
            top_n=parse_int(arguments.get("topN")) or DEFAULT_TOP_N,
        )
    except RuntimeError as error:
        return ToolResult(status="error", tool=tool_name, message=str(error))

    if not products:
        return ToolResult(
            status="no_match",
            tool=tool_name,
            data={
                "productType": PRODUCT_TYPE_NAMES[product_type],
                "termMonths": term_months,
                "amountKrw": amount_krw,
                "searchSummary": search_summary,
            },
            message=(
                "조건에 맞는 상품이 없습니다. 기간, 가입 방식 또는 제한상품 "
                "포함 여부를 조정할지 사용자에게 물어보세요."
            ),
        )

    return ToolResult(
        status="success",
        tool=tool_name,
        data={
            "dataMode": "finlife_csv",
            "productType": PRODUCT_TYPE_NAMES[product_type],
            "termMonths": term_months,
            "amountKrw": amount_krw,
            "amountMeaning": "총 예치금" if product_type == "deposit" else "월 납입금",
            "joinPreference": join_preference,
            "products": products,
            "searchSummary": search_summary,
            "instructions": [
                "기본금리를 우선하고 우대금리는 조건 충족 시에만 가능하다고 설명한다.",
                "상품별 추천 근거와 확인해야 할 우대조건을 함께 말한다.",
                "공시월과 수집시점을 밝히고 가입 전 금융회사에서 최신 조건을 재확인하도록 안내한다.",
                "수익을 보장하거나 개인 맞춤 투자자문으로 표현하지 않는다.",
            ],
        },
        message="금융감독원 수집 데이터에서 조건에 맞는 상품을 찾았습니다.",
    )
