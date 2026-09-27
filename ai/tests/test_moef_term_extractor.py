"""extract_moef_terms.py 단위 테스트. 임시 Excel 파일을 생성해 검증하며 실제 업로드 파일은 사용하지 않는다."""

import csv

import pandas as pd
import pytest

from scripts.extract_moef_terms import (
    build_normalized_term,
    compute_duplicates,
    extract_terms,
    load_excel_rows,
    normalize_whitespace,
    parse_source_id,
    save_csv,
    validate_required_columns,
)


def _write_excel(tmp_path, rows, columns=("순번", "주제", "용어", "설명"), filename="sample.xlsx"):
    df = pd.DataFrame(rows, columns=list(columns))
    path = tmp_path / filename
    df.to_excel(path, sheet_name="Sheet1", index=False)
    return path


# 1. 정상 변환
def test_extracts_valid_rows_with_all_fields():
    df = pd.DataFrame(
        {
            "순번": ["1", "2"],
            "주제": ["경제", "사회"],
            "용어": ["1인당 국민소득", "0.5인 가구"],
            "설명": ["국민소득을 총국민 수로 나눈 값.", "잦은 여행 등으로 집을 오래 비우는 사람."],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")

    assert result.total_rows == 2
    assert len(result.valid_terms) == 2
    assert result.invalid_rows == []

    first = result.valid_terms[0]
    assert first.source_id == 1
    assert first.category == "경제"
    assert first.term == "1인당 국민소득"
    assert first.definition == "국민소득을 총국민 수로 나눈 값."
    assert first.english_term == ""
    assert first.source == "재정경제부"
    assert first.source_url == "sample.xlsx"


# 2. 필수 컬럼 누락
def test_validate_required_columns_raises_value_error_when_missing():
    with pytest.raises(ValueError):
        validate_required_columns(["순번", "주제", "용어"])  # 설명 누락


def test_extract_terms_raises_value_error_when_required_column_missing():
    df = pd.DataFrame({"순번": ["1"], "주제": ["경제"], "용어": ["용어1"]})  # 설명 컬럼 없음

    with pytest.raises(ValueError):
        extract_terms(df, source_url="sample.xlsx")


# 3. 줄바꿈/연속 공백 정규화
def test_normalize_whitespace_collapses_newlines_and_tabs():
    text = "  희토류(Rare Earth)\r\n\r\n  화학적으로   안정된   \t원소이다.  "
    assert normalize_whitespace(text) == "희토류(Rare Earth) 화학적으로 안정된 원소이다."


def test_normalize_whitespace_restores_html_entities():
    assert normalize_whitespace("연구개발(R&amp;D)") == "연구개발(R&D)"


def test_normalize_whitespace_handles_missing_value():
    assert normalize_whitespace(None) == ""
    assert normalize_whitespace(float("nan")) == ""


# 4. 빈 용어 행
def test_row_with_empty_term_is_excluded_and_recorded_as_invalid():
    df = pd.DataFrame(
        {
            "순번": ["1"],
            "주제": ["경제"],
            "용어": ["   "],
            "설명": ["정상적인 설명입니다."],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")

    assert len(result.valid_terms) == 0
    assert result.empty_term_count == 1
    assert len(result.invalid_rows) == 1
    assert "term_empty" in result.invalid_rows[0]["reasons"]


# 5. 빈 설명 행
def test_row_with_empty_definition_is_excluded_and_recorded_as_invalid():
    df = pd.DataFrame(
        {
            "순번": ["1"],
            "주제": ["경제"],
            "용어": ["정상 용어"],
            "설명": [""],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")

    assert len(result.valid_terms) == 0
    assert result.empty_definition_count == 1
    assert "definition_empty" in result.invalid_rows[0]["reasons"]


# 6. 중복 source_id
def test_duplicate_source_id_is_detected_but_not_removed():
    df = pd.DataFrame(
        {
            "순번": ["1", "1"],
            "주제": ["경제", "사회"],
            "용어": ["용어A", "용어B"],
            "설명": ["설명 A", "설명 B"],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")
    dup_stats = compute_duplicates(result.valid_terms)

    assert len(result.valid_terms) == 2  # 원본 보존, 삭제하지 않음
    assert dup_stats.source_id_duplicate_count == 1
    assert any(row["duplicate_by_source_id"] for row in dup_stats.duplicate_rows)


def test_missing_or_non_numeric_source_id_is_flagged_as_warning():
    df = pd.DataFrame(
        {
            "순번": ["", "abc"],
            "주제": ["경제", "사회"],
            "용어": ["용어A", "용어B"],
            "설명": ["설명 A", "설명 B"],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")

    assert result.source_id_warning_count == 2
    assert len(result.valid_terms) == 2  # term/definition은 유효하므로 삭제되지 않음
    assert all("source_id_invalid" in row["reasons"] for row in result.invalid_rows)


def test_parse_source_id_handles_various_inputs():
    assert parse_source_id("42") == 42
    assert parse_source_id(None) is None
    assert parse_source_id("") is None
    assert parse_source_id("abc") is None
    assert parse_source_id(float("nan")) is None


# 7. 중복 term
def test_duplicate_term_exact_and_normalized_are_detected():
    df = pd.DataFrame(
        {
            "순번": ["1", "2", "3"],
            "주제": ["경제", "경제", "경제"],
            "용어": ["환매청구권(풋백옵션)", "환매청구권 (풋백옵션)", "다른 용어"],
            "설명": ["설명1", "설명2", "설명3"],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")
    dup_stats = compute_duplicates(result.valid_terms)

    assert dup_stats.term_exact_duplicate_count == 0  # 괄호 앞 공백이 달라 완전히 같지는 않음
    assert dup_stats.normalized_term_duplicate_count == 1
    normalized_rows = [r for r in dup_stats.duplicate_rows if r["duplicate_by_normalized_term"]]
    assert len(normalized_rows) == 2


def test_build_normalized_term_strips_brackets_and_symbols():
    assert build_normalized_term("환매청구권 (풋백옵션)") == build_normalized_term("환매청구권(풋백옵션)")
    assert build_normalized_term("1인 가구") == build_normalized_term("1인-가구")


# 8. category 빈값 허용
def test_empty_category_is_allowed_and_still_valid():
    df = pd.DataFrame(
        {
            "순번": ["1"],
            "주제": [""],
            "용어": ["정상 용어"],
            "설명": ["정상 설명"],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")

    assert len(result.valid_terms) == 1
    assert result.valid_terms[0].category == ""
    assert result.empty_category_count == 1
    assert result.invalid_rows == []  # category만 비어있는 경우 무효 행이 아님


# 9. CSV 생성 + UTF-8-SIG 저장
def test_save_csv_round_trip(tmp_path):
    df = pd.DataFrame(
        {
            "순번": ["1"],
            "주제": ["경제"],
            "용어": ["원화"],
            "설명": ["대한민국의 통화."],
        }
    )
    result = extract_terms(df, source_url="sample.xlsx")

    csv_path = tmp_path / "moef_terms.csv"
    save_csv(result.valid_terms, csv_path)

    with csv_path.open("rb") as f:
        raw_bytes = f.read()
    assert raw_bytes.startswith(b"\xef\xbb\xbf")  # utf-8-sig BOM

    with csv_path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        csv_rows = list(reader)
    assert csv_rows[0]["term"] == "원화"
    assert csv_rows[0]["source"] == "재정경제부"


def test_load_excel_rows_reads_temp_file(tmp_path):
    path = _write_excel(
        tmp_path,
        {
            "순번": ["1", "2"],
            "주제": ["경제", "사회"],
            "용어": ["용어1", "용어2"],
            "설명": ["설명1", "설명2"],
        },
    )
    df = load_excel_rows(path, "Sheet1")

    assert list(df.columns) == ["순번", "주제", "용어", "설명"]
    assert len(df) == 2
