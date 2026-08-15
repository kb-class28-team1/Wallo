import json
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
    "annuitySavingProductsSearch.json"
)

# 금융상품 한눈에 연금저축 API는 금융투자 권역으로 조회합니다.
TOP_FINANCIAL_GROUPS = {
    "060000": "금융투자",
}

EXPECTED_PRODUCT_DIVISION = "P"

CSV_COLUMNS = [
    "권역코드",
    "권역명",
    "공시월",
    "금융회사 코드",
    "금융회사",
    "금융상품 코드",
    "상품명",
    "가입방법",
    "연금종류 코드",
    "연금종류명",
    "상품유형 코드",
    "상품유형명",
    "평균 수익률",
    "공시이율",
    "최저보증이율",
    "과거 수익률 1",
    "과거 수익률 2",
    "과거 수익률 3",
    "판매 시작일",
    "유지 건수",
    "기타 정보",
    "판매회사",
    "연금 수령기간 코드",
    "연금 수령기간",
    "연금 가입연령 코드",
    "연금 가입연령",
    "월 납입금액 코드",
    "월 납입금액",
    "납입기간 코드",
    "납입기간",
    "연금 개시연령 코드",
    "연금 개시연령",
    "연금 수령금액",
    "공시 시작일",
    "공시 종료일",
    "금융회사 제출일",
    "기본정보 원본JSON",
    "옵션정보 원본JSON",
    "데이터 출처",
    "수집일시",
]

AI_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_FILE = (
    AI_ROOT / "data" / "raw" / "finlife_annuity_saving_products.csv"
)

REQUEST_TIMEOUT_SECONDS = 30
REQUEST_DELAY_SECONDS = 0.5
MAX_RETRIES = 3


# =========================================================
# 공통 함수
# =========================================================

def clean_text(value: Any) -> str:
    if value is None:
        return ""

    return " ".join(
        str(value).replace("\xa0", " ").split()
    ).strip()


def safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def format_number(value: Any) -> str:
    text = clean_text(value)

    if not text:
        return ""

    try:
        number = float(text)
    except ValueError:
        return text

    if number.is_integer():
        return f"{int(number):,}"

    return f"{number:,.4f}".rstrip("0").rstrip(".")


def raw_json(item: dict[str, Any]) -> str:
    return json.dumps(
        item,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )


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
            "시스템 환경변수 또는 ai/.env에 인증키를 입력하세요."
        )

    return api_key


def validate_annuity_result(
        result: dict[str, Any],
) -> None:
    product_division = clean_text(
        result.get("prdt_div")
    )

    if product_division != EXPECTED_PRODUCT_DIVISION:
        raise RuntimeError(
            "연금저축 API가 아닌 응답을 받았습니다. "
            f"prdt_div={product_division or '없음'}"
        )

    base_list = result.get("baseList") or []
    option_list = result.get("optionList") or []
    total_count = safe_int(result.get("total_count"))

    if total_count > 0 and not base_list and not option_list:
        raise RuntimeError(
            "금융감독원 연금저축 API가 "
            f"total_count={total_count}이라고 응답했지만 "
            "baseList와 optionList를 비워서 반환했습니다. "
            "빈 CSV 저장을 중단합니다."
        )

    if not base_list:
        return

    first_base = base_list[0]

    if not isinstance(first_base, dict):
        raise RuntimeError(
            "연금저축 기본상품 응답 형식이 올바르지 않습니다."
        )

    if (
            "pnsn_kind" not in first_base
            or "loan_type" in first_base
            or "loan_term" in first_base
    ):
        raise RuntimeError(
            "연금저축 대신 다른 금융상품 데이터가 "
            "반환되어 저장을 중단합니다."
        )


# =========================================================
# 금융감독원 연금저축 Open API 호출
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

            validate_annuity_result(result)

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
    return {
        "권역코드": group_no,
        "권역명": group_name,
        "공시월": clean_text(base.get("dcls_month")),
        "금융회사 코드": clean_text(base.get("fin_co_no")),
        "금융회사": clean_text(base.get("kor_co_nm")),
        "금융상품 코드": clean_text(base.get("fin_prdt_cd")),
        "상품명": clean_text(base.get("fin_prdt_nm")),
        "가입방법": clean_text(base.get("join_way")),
        "연금종류 코드": clean_text(base.get("pnsn_kind")),
        "연금종류명": clean_text(base.get("pnsn_kind_nm")),
        "상품유형 코드": clean_text(base.get("prdt_type")),
        "상품유형명": clean_text(base.get("prdt_type_nm")),
        "평균 수익률": clean_text(base.get("avg_prft_rate")),
        "공시이율": clean_text(base.get("dcls_rate")),
        "최저보증이율": clean_text(base.get("guar_rate")),
        "과거 수익률 1": clean_text(base.get("btrm_prft_rate_1")),
        "과거 수익률 2": clean_text(base.get("btrm_prft_rate_2")),
        "과거 수익률 3": clean_text(base.get("btrm_prft_rate_3")),
        "판매 시작일": clean_text(base.get("sale_strt_day")),
        "유지 건수": format_number(base.get("mntn_cnt")),
        "기타 정보": clean_text(base.get("etc")),
        "판매회사": clean_text(base.get("sale_co")),
        "연금 수령기간 코드": clean_text(
            option.get("pnsn_recp_trm")
        ),
        "연금 수령기간": clean_text(
            option.get("pnsn_recp_trm_nm")
        ),
        "연금 가입연령 코드": clean_text(
            option.get("pnsn_entr_age")
        ),
        "연금 가입연령": clean_text(
            option.get("pnsn_entr_age_nm")
        ),
        "월 납입금액 코드": clean_text(
            option.get("mon_paym_atm")
        ),
        "월 납입금액": clean_text(
            option.get("mon_paym_atm_nm")
        ),
        "납입기간 코드": clean_text(option.get("paym_prd")),
        "납입기간": clean_text(option.get("paym_prd_nm")),
        "연금 개시연령 코드": clean_text(
            option.get("pnsn_strt_age")
        ),
        "연금 개시연령": clean_text(
            option.get("pnsn_strt_age_nm")
        ),
        "연금 수령금액": format_number(option.get("pnsn_recp_amt")),
        "공시 시작일": clean_text(base.get("dcls_strt_day")),
        "공시 종료일": clean_text(base.get("dcls_end_day")),
        "금융회사 제출일": clean_text(
            base.get("fin_co_subm_day")
        ),
        "기본정보 원본JSON": raw_json(base),
        "옵션정보 원본JSON": raw_json(option),
        "데이터 출처": "금융감독원 금융상품한눈에 연금저축 Open API",
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
    if not products:
        raise RuntimeError(
            "수집된 연금저축 데이터가 없어 "
            "기존 CSV를 덮어쓰지 않습니다."
        )

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
    os.replace(temporary_file, OUTPUT_FILE)

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
            print(f"{group_name} 연금저축 수집 시작 ({group_no})")

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

                base_count = len(result.get("baseList") or [])
                option_count = len(result.get("optionList") or [])

                print(
                    f"[{group_name}] "
                    f"{page_number}/{max_page_number}페이지, "
                    f"기본상품={base_count}개, "
                    f"옵션={option_count}개, "
                    f"저장행={len(page_products)}개, "
                    f"누적행={len(all_products)}개"
                )

                save_products(all_products)

                page_number += 1

                if page_number <= max_page_number:
                    time.sleep(REQUEST_DELAY_SECONDS)

    return all_products


# =========================================================
# 메인 실행
# =========================================================

def main() -> int:
    try:
        api_key = get_api_key()
        products = crawl_all_products(api_key)

        print()
        print("=" * 80)
        print(f"전체 연금저축 옵션 {len(products)}개 수집 완료")
        return 0

    except KeyboardInterrupt:
        print()
        print("[사용자 중단]")
        return 130

    except Exception as error:
        print()
        print(
            f"[프로그램 오류] "
            f"{type(error).__name__}: {error}"
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
