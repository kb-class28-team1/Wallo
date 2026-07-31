"""merge_financial_terms.py 단위 테스트."""

from scripts.merge_financial_terms import (
    SourceTerm,
    build_insert_sql,
    merge_records,
    normalize_term,
    read_source_csv,
)


def _term(term, definition="정의", source="금융감독원", source_url="url"):
    return SourceTerm(term=term, definition=definition, source=source, source_url=source_url)


# normalized_term 생성 규칙 (spec 예시 그대로 검증)
def test_normalize_term_removes_space():
    assert normalize_term("실업 급여") == "실업급여"


def test_normalize_term_lowercases_english():
    assert normalize_term("GDP") == "gdp"


def test_normalize_term_removes_hyphen_and_space():
    assert normalize_term("CAMEL-IR 방식") == "camelir방식"


def test_normalize_term_removes_brackets_slash_dot_comma():
    assert normalize_term("환매청구권(풋백옵션)") == "환매청구권풋백옵션"
    assert normalize_term("환매조건부매매/RP/Repo") == "환매조건부매매rprepo"
    assert normalize_term("근로소득·사업소득") == "근로소득사업소득"
    assert normalize_term("가, 나") == "가나"


# 1. 단일 파일 병합 (중복 없음, 다른 항목들과 섞이지 않음)
def test_merge_single_source_no_duplicates():
    records = [_term("가계수지", source="한국은행"), _term("가계순저축률", source="한국은행")]

    final_rows, duplicate_rows, group_sizes = merge_records(records)

    assert len(final_rows) == 2
    assert duplicate_rows == []
    assert all(size == 1 for _, size in group_sizes)


# 2. 3개 출처 병합 (겹치지 않는 용어는 모두 살아남는다)
def test_merge_three_sources_without_overlap():
    records = [
        _term("가계수지", source="한국은행"),
        _term("휴면예금", source="금융감독원"),
        _term("0.5인 가구", source="재정경제부"),
    ]

    final_rows, duplicate_rows, _ = merge_records(records)

    assert len(final_rows) == 3
    assert duplicate_rows == []
    sources = {row["source"] for row in final_rows}
    assert sources == {"한국은행", "금융감독원", "재정경제부"}


# 3. 동일 용어 (완전히 같은 문자열)
def test_merge_detects_exact_duplicate_term():
    records = [_term("국내총생산", source="한국은행"), _term("국내총생산", source="금융감독원")]

    final_rows, duplicate_rows, group_sizes = merge_records(records)

    assert len(final_rows) == 1
    assert len(duplicate_rows) == 1
    assert group_sizes[0][1] == 2


# 4. 공백만 다른 용어
def test_merge_detects_duplicate_differing_only_by_whitespace():
    records = [_term("실업급여", source="한국은행"), _term("실업 급여", source="금융감독원")]

    final_rows, duplicate_rows, _ = merge_records(records)

    assert len(final_rows) == 1
    assert len(duplicate_rows) == 1
    assert final_rows[0]["normalized_term"] == "실업급여"


# 5. 괄호 차이
def test_merge_detects_duplicate_differing_only_by_brackets():
    records = [
        _term("환매청구권(풋백옵션)", source="한국은행"),
        _term("환매청구권 풋백옵션", source="금융감독원"),
    ]

    final_rows, duplicate_rows, _ = merge_records(records)

    assert len(final_rows) == 1
    assert len(duplicate_rows) == 1


# 6. source 우선순위 (한국은행 > 금융감독원 > 재정경제부)
def test_merge_prefers_bok_over_fss_and_moef():
    records = [
        _term("기준금리", definition="MOEF 정의", source="재정경제부"),
        _term("기준금리", definition="FSS 정의", source="금융감독원"),
        _term("기준금리", definition="BOK 정의", source="한국은행"),
    ]

    final_rows, duplicate_rows, _ = merge_records(records)

    assert len(final_rows) == 1
    assert final_rows[0]["source"] == "한국은행"
    assert final_rows[0]["definition"] == "BOK 정의"
    assert len(duplicate_rows) == 2
    assert all(row["selected_source"] == "한국은행" for row in duplicate_rows)
    removed_sources = {row["removed_source"] for row in duplicate_rows}
    assert removed_sources == {"재정경제부", "금융감독원"}


def test_merge_prefers_fss_over_moef_when_bok_absent():
    records = [
        _term("가산금리", definition="MOEF 정의", source="재정경제부"),
        _term("가산금리", definition="FSS 정의", source="금융감독원"),
    ]

    final_rows, duplicate_rows, _ = merge_records(records)

    assert final_rows[0]["source"] == "금융감독원"
    assert final_rows[0]["definition"] == "FSS 정의"
    assert duplicate_rows[0]["removed_source"] == "재정경제부"


# 7. SQL 생성
def test_build_insert_sql_generates_valid_insert_statements():
    rows = [
        {"term": "가계수지", "normalized_term": "가계수지", "definition": "정의1", "source": "한국은행", "source_url": ""},
        {"term": "O'Brien 지수", "normalized_term": "obrien지수", "definition": "따옴표's 포함", "source": "금융감독원", "source_url": ""},
    ]

    sql = build_insert_sql(rows)

    assert "INSERT INTO financial_term (term_name, description, source) VALUES" in sql
    assert "'가계수지'" in sql
    assert "'정의1'" in sql
    assert "'한국은행'" in sql
    # 작은따옴표는 SQL 표준 방식으로 이스케이프되어야 한다 (' -> '')
    assert "O''Brien 지수" in sql
    assert "따옴표''s 포함" in sql
    assert sql.count("INSERT INTO financial_term") == 2


def test_read_source_csv_reads_common_columns(tmp_path):
    path = tmp_path / "sample.csv"
    path.write_text(
        "source_id,term,english_term,definition,source,source_url\n"
        "1,가계수지,,정의입니다,한국은행,http://example.com\n",
        encoding="utf-8-sig",
    )

    records = read_source_csv(path)

    assert len(records) == 1
    assert records[0].term == "가계수지"
    assert records[0].definition == "정의입니다"
    assert records[0].source == "한국은행"
    assert records[0].source_url == "http://example.com"
