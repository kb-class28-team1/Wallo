import csv

from app.agents.financial.tools import product_recommendation


def write_products(path, rows):
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)


def product_row(**overrides):
    row = {
        "권역명": "은행",
        "금융회사 코드": "001",
        "금융회사": "기본은행",
        "금융상품 코드": "P1",
        "상품명": "기본금리예금",
        "저축기간(개월)": "12",
        "금리유형 코드": "S",
        "금리유형명": "단리",
        "세전 이자율": "4.00%",
        "세후 이자율": "3.38%",
        "세후 이자(예시)": "338,000",
        "최고 우대금리": "4.00%",
        "가입방법": "인터넷,스마트폰",
        "가입제한 코드": "1",
        "가입 대상": "제한없음",
        "우대조건": "없음",
        "만기 후 이자율": "보통예금 금리",
        "최고한도": "",
        "공시월": "202607",
        "수집일시": "2026-08-10T10:00:00",
    }
    row.update(overrides)
    return row


def test_execute_requests_missing_recommendation_inputs():
    result = product_recommendation.execute(
        product_recommendation.NAME,
        {"request": "금리 좋은 상품 추천해줘"},
    )

    assert result.status == "needs_input"
    assert len(result.data["missingFields"]) == 3


def test_execute_accepts_null_optional_arguments_from_model():
    result = product_recommendation.execute(
        product_recommendation.NAME,
        {
            "request": "예금을 가입하고 싶어",
            "productType": "deposit",
            "termMonths": None,
            "amountKrw": None,
            "joinPreference": None,
            "includeRestricted": None,
            "topN": None,
        },
    )

    assert result.status == "needs_input"
    assert result.data["missingFields"] == [
        "희망 가입기간(개월)",
        "예금 총 예치금 또는 적금 월 납입금",
    ]


def test_recommend_deposit_filters_online_products(tmp_path, monkeypatch):
    data_file = tmp_path / "deposit.csv"
    write_products(data_file, [
        product_row(),
        product_row(
            **{
                "금융회사 코드": "002",
                "금융회사": "영업점은행",
                "금융상품 코드": "P2",
                "상품명": "영업점예금",
                "세전 이자율": "4.50%",
                "최고 우대금리": "4.50%",
                "가입방법": "영업점",
            }
        ),
    ])
    monkeypatch.setitem(product_recommendation.DATA_FILES, "deposit", data_file)

    products, summary = product_recommendation.recommend_products(
        product_type="deposit",
        term_months=12,
        amount_krw=10_000_000,
        join_preference="online",
    )

    assert summary["matchedRows"] == 1
    assert products[0]["productName"] == "기본금리예금"
    assert products[0]["estimatedAfterTaxInterestKrw"] == 338_400


def test_recommendation_uses_base_rate_before_large_bonus(tmp_path, monkeypatch):
    data_file = tmp_path / "deposit.csv"
    write_products(data_file, [
        product_row(),
        product_row(
            **{
                "금융회사 코드": "002",
                "금융상품 코드": "P2",
                "상품명": "우대 의존 예금",
                "세전 이자율": "3.50%",
                "최고 우대금리": "5.00%",
            }
        ),
    ])
    monkeypatch.setitem(product_recommendation.DATA_FILES, "deposit", data_file)

    products, _ = product_recommendation.recommend_products(
        product_type="deposit",
        term_months=12,
        amount_krw=1_000_000,
    )

    assert products[0]["productName"] == "기본금리예금"


def test_same_product_options_are_deduplicated(tmp_path, monkeypatch):
    data_file = tmp_path / "deposit.csv"
    write_products(data_file, [
        product_row(),
        product_row(**{"금리유형 코드": "M", "금리유형명": "복리"}),
    ])
    monkeypatch.setitem(product_recommendation.DATA_FILES, "deposit", data_file)

    products, _ = product_recommendation.recommend_products(
        product_type="deposit",
        term_months=12,
        amount_krw=1_000_000,
    )

    assert len(products) == 1


def test_branch_preference_excludes_mobile_only_product(tmp_path, monkeypatch):
    data_file = tmp_path / "deposit.csv"
    write_products(data_file, [
        product_row(**{"상품명": "모바일전용", "가입방법": "스마트폰"}),
        product_row(
            **{
                "금융회사 코드": "002",
                "금융상품 코드": "P2",
                "상품명": "영업점예금",
                "가입방법": "영업점",
                "세전 이자율": "3.50%",
            }
        ),
    ])
    monkeypatch.setitem(product_recommendation.DATA_FILES, "deposit", data_file)

    products, _ = product_recommendation.recommend_products(
        product_type="deposit",
        term_months=12,
        amount_krw=1_000_000,
        join_preference="branch",
    )

    assert [product["productName"] for product in products] == ["영업점예금"]
