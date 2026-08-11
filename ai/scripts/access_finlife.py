import os
import time
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

import pandas as pd
import requests
from dotenv import load_dotenv


# =========================================================
# 기본 설정
# =========================================================

API_URL = (
    "https://finlife.fss.or.kr/finlifeapi/"
    "depositProductsSearch.json"
)

TOP_FINANCIAL_GROUPS = {
    "020000": "은행",
    "030300": "저축은행",
}

CSV_COLUMNS = [
    "권역코드",
    "권역명",
    "공시월",
    "금융회사 코드",
    "금융회사",
    "금융상품 코드",
    "상품명",
    "저축기간(개월)",
    "금리유형 코드",
    "금리유형명",
    "세전 이자율",
    "세후 이자율",
    "세후 이자(예시)",
    "최고 우대금리",
    "가입방법",
    "가입제한 코드",
    "가입 대상",
    "이자계산방식",
    "금융상품 문의",
    "우대조건",
    "만기 후 이자율",
    "기타 유의사항",
    "최고한도",
    "공시 시작일",
    "공시 종료일",
    "금융회사 제출일",
    "데이터 출처",
    "수집일시",
]

AI_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_FILE = AI_ROOT / "data" / "raw" / "finlife_deposit_products.csv"

EXAMPLE_PRINCIPAL = 10_000_000
INTEREST_TAX_RATE = 0.154

REQUEST_TIMEOUT_SECONDS = 30
REQUEST_DELAY_SECONDS = 0.5
MAX_RETRIES = 3

JOIN_DENY_NAMES = {
    "1": "제한없음",
    "2": "서민전용",
    "3": "일부제한",
}


# =========================================================
# 공통 함수
# =========================================================

def clean_text(value: Any) -> str:
    if value is None:
        return ""

    return " ".join(
        str(value).replace("\xa0", " ").split()
    ).strip()


def safe_float(value: Any) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def format_rate(value: Any) -> str:
    rate = safe_float(value)

    if rate is None:
        return ""

    return f"{rate:.2f}%"


def calculate_after_tax_rate(value: Any) -> str:
    rate = safe_float(value)

    if rate is None:
        return ""

    after_tax_rate = rate * (1 - INTEREST_TAX_RATE)
    return f"{after_tax_rate:.2f}%"


def calculate_after_tax_interest(
        annual_rate_value: Any,
        save_term_value: Any,
        interest_rate_type: Any,
) -> str:
    annual_rate = safe_float(annual_rate_value)
    save_term = safe_int(save_term_value)

    if annual_rate is None or save_term <= 0:
        return ""

    annual_rate_decimal = annual_rate / 100
    rate_type = clean_text(interest_rate_type).upper()

    if rate_type == "M":
        gross_interest = EXAMPLE_PRINCIPAL * (
            (1 + annual_rate_decimal / 12) ** save_term - 1
        )
    else:
        gross_interest = (
            EXAMPLE_PRINCIPAL
            * annual_rate_decimal
            * save_term
            / 12
        )

    after_tax_interest = gross_interest * (
        1 - INTEREST_TAX_RATE
    )

    # 금융상품 한눈에 화면의 단리 예시는 천 원 단위로 표시됩니다.
    if rate_type == "S":
        after_tax_interest = round(after_tax_interest / 1000) * 1000
    else:
        after_tax_interest = round(after_tax_interest)

    return f"{after_tax_interest:,.0f}"


def get_api_key() -> str:
    load_dotenv(
        AI_ROOT / ".env"
    )

    api_key = clean_text(
        os.getenv("FINLIFE_API_KEY")
    )

    if not api_key:
        raise RuntimeError(
            "FINLIFE_API_KEY가 설정되지 않았습니다. "
            "ai/.env 파일에 발급받은 인증키를 입력하세요."
        )

    return api_key


# =========================================================
# 금융감독원 Open API 호출
# =========================================================

def fetch_api_page(
        session: requests.Session,
        api_key: str,
        top_financial_group_no: str,
        page_number: int,
) -> dict[str, Any]:
    last_error: Optional[Exception] = None

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = session.get(
                API_URL,
                params={
                    "auth": api_key,
                    "topFinGrpNo": top_financial_group_no,
                    "pageNo": page_number,
                },
                timeout=REQUEST_TIMEOUT_SECONDS,
            )
            response.raise_for_status()

            payload = response.json()
            result = payload.get("result")

            if not isinstance(result, dict):
                raise ValueError(
                    "API 응답에 result 객체가 없습니다."
                )

            error_code = clean_text(result.get("err_cd"))

            if error_code != "000":
                error_message = clean_text(
                    result.get("err_msg")
                )

                raise RuntimeError(
                    f"금융감독원 API 오류 "
                    f"({error_code}): {error_message}"
                )

            return result

        except (
                requests.RequestException,
                ValueError,
        ) as error:
            last_error = error

            print(
                f"[API 재시도 {attempt}/{MAX_RETRIES}] "
                f"권역={top_financial_group_no}, "
                f"페이지={page_number}"
            )
            print(
                f"원인: {type(error).__name__}: {error}"
            )

            if attempt < MAX_RETRIES:
                time.sleep(attempt * 2)

        except RuntimeError:
            # 인증키 및 요청변수 오류는 재시도해도 해결되지 않습니다.
            raise

    raise RuntimeError(
        f"권역 {top_financial_group_no}의 "
        f"{page_number}페이지를 불러오지 못했습니다."
    ) from last_error


# =========================================================
# API 응답 결합 및 CSV 행 생성
# =========================================================

def make_product_key(
        item: dict[str, Any],
) -> tuple[str, str, str]:
    return (
        clean_text(item.get("dcls_month")),
        clean_text(item.get("fin_co_no")),
        clean_text(item.get("fin_prdt_cd")),
    )


def create_product_row(
        base: dict[str, Any],
        option: dict[str, Any],
        group_no: str,
        group_name: str,
        collected_at: str,
) -> dict[str, str]:
    interest_rate = option.get("intr_rate")
    interest_rate_type = clean_text(
        option.get("intr_rate_type")
    )

    join_deny = clean_text(base.get("join_deny"))
    join_member = clean_text(base.get("join_member"))
    join_target = JOIN_DENY_NAMES.get(
        join_deny,
        join_member,
    )

    if join_member and join_member != join_target:
        join_target = f"{join_target} ({join_member})"

    return {
        "권역코드": group_no,
        "권역명": group_name,
        "공시월": clean_text(base.get("dcls_month")),
        "금융회사 코드": clean_text(base.get("fin_co_no")),
        "금융회사": clean_text(base.get("kor_co_nm")),
        "금융상품 코드": clean_text(base.get("fin_prdt_cd")),
        "상품명": clean_text(base.get("fin_prdt_nm")),
        "저축기간(개월)": clean_text(option.get("save_trm")),
        "금리유형 코드": interest_rate_type,
        "금리유형명": clean_text(
            option.get("intr_rate_type_nm")
        ),
        "세전 이자율": format_rate(interest_rate),
        "세후 이자율": calculate_after_tax_rate(interest_rate),
        "세후 이자(예시)": calculate_after_tax_interest(
            annual_rate_value=interest_rate,
            save_term_value=option.get("save_trm"),
            interest_rate_type=interest_rate_type,
        ),
        "최고 우대금리": format_rate(option.get("intr_rate2")),
        "가입방법": clean_text(base.get("join_way")),
        "가입제한 코드": join_deny,
        "가입 대상": join_target,
        "이자계산방식": clean_text(
            option.get("intr_rate_type_nm")
        ),
        # 정기예금 API에는 문의 전화 필드가 제공되지 않습니다.
        "금융상품 문의": "",
        "우대조건": clean_text(base.get("spcl_cnd")),
        "만기 후 이자율": clean_text(base.get("mtrt_int")),
        "기타 유의사항": clean_text(base.get("etc_note")),
        "최고한도": clean_text(base.get("max_limit")),
        "공시 시작일": clean_text(base.get("dcls_strt_day")),
        "공시 종료일": clean_text(base.get("dcls_end_day")),
        "금융회사 제출일": clean_text(
            base.get("fin_co_subm_day")
        ),
        "데이터 출처": "금융감독원 금융상품한눈에 Open API",
        "수집일시": collected_at,
    }


def merge_api_result(
        result: dict[str, Any],
        group_no: str,
        group_name: str,
        collected_at: str,
) -> list[dict[str, str]]:
    base_list = result.get("baseList") or []
    option_list = result.get("optionList") or []

    options_by_product: dict[
        tuple[str, str, str],
        list[dict[str, Any]],
    ] = defaultdict(list)

    for option in option_list:
        if isinstance(option, dict):
            options_by_product[
                make_product_key(option)
            ].append(option)

    rows: list[dict[str, str]] = []

    for base in base_list:
        if not isinstance(base, dict):
            continue

        options = options_by_product.get(
            make_product_key(base),
            [],
        )

        # 옵션 정보가 없는 기본상품도 누락하지 않습니다.
        if not options:
            options = [{}]

        for option in options:
            rows.append(
                create_product_row(
                    base=base,
                    option=option,
                    group_no=group_no,
                    group_name=group_name,
                    collected_at=collected_at,
                )
            )

    return rows


# =========================================================
# CSV 저장
# =========================================================

def save_products(
        products: list[dict[str, str]],
) -> None:
    dataframe = pd.DataFrame(
        products,
        columns=CSV_COLUMNS,
    )

    temporary_file = OUTPUT_FILE.with_suffix(".csv.tmp")

    dataframe.to_csv(
        temporary_file,
        index=False,
        encoding="utf-8-sig",
    )

    os.replace(
        temporary_file,
        OUTPUT_FILE,
    )

    print(f"CSV 저장 완료: {OUTPUT_FILE}")


# =========================================================
# 전체 권역 및 페이지 수집
# =========================================================

def crawl_all_products(
        api_key: str,
) -> list[dict[str, str]]:
    all_products: list[dict[str, str]] = []
    collected_at = datetime.now().isoformat(
        timespec="seconds"
    )

    with requests.Session() as session:
        for group_no, group_name in TOP_FINANCIAL_GROUPS.items():
            page_number = 1
            max_page_number = 1

            print()
            print("=" * 80)
            print(f"{group_name} 권역 수집 시작 ({group_no})")

            while page_number <= max_page_number:
                result = fetch_api_page(
                    session=session,
                    api_key=api_key,
                    top_financial_group_no=group_no,
                    page_number=page_number,
                )

                max_page_number = max(
                    1,
                    safe_int(result.get("max_page_no"), 1),
                )

                page_products = merge_api_result(
                    result=result,
                    group_no=group_no,
                    group_name=group_name,
                    collected_at=collected_at,
                )
                all_products.extend(page_products)

                base_count = len(
                    result.get("baseList") or []
                )
                option_count = len(
                    result.get("optionList") or []
                )

                print(
                    f"[{group_name}] "
                    f"{page_number}/{max_page_number}페이지, "
                    f"기본상품={base_count}개, "
                    f"옵션={option_count}개, "
                    f"저장행={len(page_products)}개, "
                    f"누적행={len(all_products)}개"
                )

                # 실행 도중 중단돼도 완료된 페이지까지 보존합니다.
                save_products(all_products)

                page_number += 1

                if page_number <= max_page_number:
                    time.sleep(REQUEST_DELAY_SECONDS)

    return all_products


# =========================================================
# 메인 실행
# =========================================================

def main() -> None:
    try:
        api_key = get_api_key()
        products = crawl_all_products(api_key)

        print()
        print("=" * 80)
        print(f"전체 금융상품 {len(products)}개 수집 완료")

    except KeyboardInterrupt:
        print()
        print("[사용자 중단]")

    except Exception as error:
        print()
        print(
            f"[프로그램 오류] "
            f"{type(error).__name__}: {error}"
        )


if __name__ == "__main__":
    main()
