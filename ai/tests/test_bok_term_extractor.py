"""extract_bok_terms.py 단위 테스트.

실제 원본 PDF는 매번 읽지 않고, PyMuPDF가 만들어내는 line dict과 동일한 형태의 fixture
데이터(plain dict/list)로 순수 파싱 로직만 검증한다. PDF 파일 자체가 필요한 두 케이스
(경로 오류, 목차를 찾지 못하는 경우)만 PyMuPDF로 즉석에서 만든 최소 PDF를 사용한다.
"""

import fitz
import pytest

from scripts.extract_bok_terms import (
    BokTerm,
    TocEntry,
    analyze_pdf_structure,
    classify_line,
    compute_duplicates,
    extract_english_term,
    match_chunks_to_toc,
    normalize_whitespace,
    parse_toc_pages,
    run,
    segment_body_into_chunks,
    split_related_terms,
)


def _toc_line(text, x0=70.9, y0=200.0):
    return {"text": text, "x0": x0, "y0": y0}


def _body_line(text, kind="content", x0=85.0, y0=300.0, pdf_page_index=0, booklet_page=1):
    return {
        "text": text,
        "kind": kind,
        "x0": x0,
        "y0": y0,
        "pdf_page_index": pdf_page_index,
        "booklet_page": booklet_page,
    }


def _raw_line(text, x0=85.0, y0=300.0, size=10.5, font="KoPubBatangLight"):
    return {"text": text, "x0": x0, "y0": y0, "sizes": frozenset([size]), "fonts": frozenset([font])}


# 1. 목차 한 줄 용어 추출
def test_parses_single_line_toc_entry():
    pages = [[_toc_line("가계부실위험지수(HDRI)·························· 1", y0=216.2)]]
    entries, unresolved = parse_toc_pages(pages)

    assert unresolved == []
    assert len(entries) == 1
    assert entries[0].term == "가계부실위험지수(HDRI)"
    assert entries[0].toc_page_number == 1
    assert entries[0].source_id == 1


# 2. 목차 여러 줄 용어 복원
def test_merges_wrapped_toc_entry_without_inserting_space():
    pages = [
        [
            _toc_line("경제활동인구/비경제활동인구/·", x0=275.0, y0=387.2),
            _toc_line("경제활동참가율································· 17", x0=283.5, y0=406.2),
        ]
    ]
    entries, unresolved = parse_toc_pages(pages)

    assert unresolved == []
    assert len(entries) == 1
    assert entries[0].term == "경제활동인구/비경제활동인구/경제활동참가율"
    assert entries[0].toc_page_number == 17


# 3. 목차 페이지 번호 제거 (점선 리더/페이지번호가 term에 섞이지 않아야 함)
def test_toc_entry_strips_dot_leaders_and_page_number():
    pages = [[_toc_line("결제····················································· 9", y0=558.2)]]
    entries, _ = parse_toc_pages(pages)

    assert entries[0].term == "결제"
    assert "9" not in entries[0].term
    assert "·" not in entries[0].term


def test_toc_lines_outside_column_or_header_band_are_ignored():
    pages = [
        [
            _toc_line("I 경제금융용어  800선", x0=70.9, y0=35.8),  # 머리말, y0<150
            _toc_line("ㄱ", x0=157.6, y0=177.6),  # 컬럼 범위 밖
            _toc_line("가계수지··············································· 1", x0=70.9, y0=235.2),
        ]
    ]
    entries, _ = parse_toc_pages(pages)

    assert len(entries) == 1
    assert entries[0].term == "가계수지"


# 4. 한 페이지에 여러 용어 분리
def test_segments_multiple_terms_on_same_page():
    lines = [
        _body_line("가계부실위험지수(HDRI)", kind="title", pdf_page_index=0),
        _body_line("정의1입니다.", kind="content", pdf_page_index=0),
        _body_line("가계수지", kind="title", pdf_page_index=0),
        _body_line("정의2입니다.", kind="content", pdf_page_index=0),
    ]
    chunks = segment_body_into_chunks(lines)

    assert len(chunks) == 2
    assert chunks[0]["title_text"] == "가계부실위험지수(HDRI)"
    assert chunks[0]["def_line_texts"] == ["정의1입니다."]
    assert chunks[1]["title_text"] == "가계수지"
    assert chunks[1]["def_line_texts"] == ["정의2입니다."]


# 5. 정의가 다음 페이지로 이어지는 경우
def test_definition_spanning_next_page_is_merged_into_one_chunk():
    lines = [
        _body_line("가계수지", kind="title", pdf_page_index=0, booklet_page=1),
        _body_line("첫 페이지 문장.", kind="content", pdf_page_index=0, booklet_page=1),
        _body_line("다음 페이지로 이어지는 문장.", kind="content", pdf_page_index=1, booklet_page=2),
        _body_line(" 연관검색어  경상수지", kind="related", pdf_page_index=1, booklet_page=2),
    ]
    chunks = segment_body_into_chunks(lines)

    assert len(chunks) == 1
    assert chunks[0]["def_line_texts"] == ["첫 페이지 문장.", "다음 페이지로 이어지는 문장."]
    assert chunks[0]["related_line_texts"] == [" 연관검색어  경상수지"]


# 6. 연관검색어 분리
def test_split_related_terms_multiple():
    related, failed = split_related_terms([" 연관검색어  경상수지, 재정수지 "])

    assert failed is False
    assert related == "경상수지|재정수지"


# 7. 연관검색어 없는 경우
def test_split_related_terms_absent():
    related, failed = split_related_terms([])

    assert related == ""
    assert failed is False


def test_split_related_terms_failure_when_no_terms_extractable():
    related, failed = split_related_terms([" 연관검색어  "])

    assert related == ""
    assert failed is True


# 8. 영문 약어 추출
def test_extract_english_term_abbreviation():
    assert extract_english_term("가계부실위험지수(HDRI)") == "HDRI"
    assert extract_english_term("국내총생산(GDP)") == "GDP"
    assert extract_english_term("중앙은행 디지털화폐(CBDC)") == "CBDC"


# 9. 영문 전체 표현 추출
def test_extract_english_term_full_phrase():
    assert extract_english_term("감독자협의회(Supervisory College)") == "Supervisory College"
    assert extract_english_term("NFC(Near Field Communication) 기술") == "Near Field Communication"


# 10. 한글 괄호는 english_term에서 제외
def test_extract_english_term_excludes_korean_bracket_content():
    assert extract_english_term("환매조건부매매(풋백옵션과 무관한 한글 표현)") == ""
    assert extract_english_term("가교은행") == ""


# 11. 반복 헤더/푸터 제거 (classify_line을 통한 위치 기반 판별)
def test_classify_line_marks_running_header_as_noise():
    header = _raw_line("I 경제금융용어  800선", x0=70.9, y0=35.8, size=8.0, font="NanumSquareOTFB")
    assert classify_line(header) == "noise"


def test_classify_line_marks_index_tab_as_noise():
    tab = _raw_line("ㄱ", x0=503.9, y0=125.3, size=14.0, font="NanumSquareOTFEB")
    assert classify_line(tab) == "noise"


def test_classify_line_extracts_page_number_from_footer():
    footer = _raw_line("1", x0=473.4, y0=688.9, size=11.0, font="Dinlig")
    assert classify_line(footer) == "pagenum"


def test_classify_line_detects_title_font():
    title = _raw_line("가계수지", x0=85.0, y0=595.8, size=14.0, font="SUIT-ExtraBold")
    assert classify_line(title) == "title"


def test_classify_line_detects_pure_english_title_with_din_bold_only():
    # "Nowcasting", "OIS"처럼 한글이 전혀 없는 제목은 DIN-Bold만 단독으로 쓰인다.
    title = _raw_line("Nowcasting", x0=85.0, y0=354.0, size=14.0, font="DIN-Bold")
    assert classify_line(title) == "title"


def test_classify_line_detects_related_terms_font():
    related = _raw_line(" 연관검색어  경상수지", x0=98.5, y0=546.1, size=9.5, font="KoPubDotumMedium")
    assert classify_line(related) == "related"


# 12. 빈 정의 경고
def test_empty_definition_produces_warning_but_keeps_entry():
    chunks = [
        {
            "title_text": "가계수지",
            "pdf_page_index": 0,
            "booklet_page": 1,
            "def_line_texts": [],
            "related_line_texts": [],
        }
    ]
    toc_entries = [TocEntry(source_id=1, term="가계수지", toc_page_number=1, order=1)]

    terms, warnings = match_chunks_to_toc(chunks, toc_entries, source_url="test.pdf")

    assert len(terms) == 1
    assert terms[0].definition == ""
    assert any(w["warning_type"] == "empty_definition" for w in warnings)


def test_unmatched_toc_entry_produces_title_not_found_warning():
    toc_entries = [
        TocEntry(source_id=1, term="가계수지", toc_page_number=1, order=1),
        TocEntry(source_id=2, term="가계순저축률", toc_page_number=2, order=2),
    ]
    chunks = [
        {
            "title_text": "가계수지",
            "pdf_page_index": 0,
            "booklet_page": 1,
            "def_line_texts": ["정의."],
            "related_line_texts": [],
        }
    ]

    terms, warnings = match_chunks_to_toc(chunks, toc_entries, source_url="test.pdf")

    assert len(terms) == 1
    assert any(w["warning_type"] == "title_not_found" and w["term"] == "가계순저축률" for w in warnings)


# 13. 중복 용어 탐지 (term 완전 일치)
def test_compute_duplicates_detects_exact_match():
    terms = [
        BokTerm(source_id=1, term="가계수지", english_term="", definition="d1", related_terms=""),
        BokTerm(source_id=2, term="가계수지", english_term="", definition="d2", related_terms=""),
    ]
    stats = compute_duplicates(terms)

    assert stats.term_exact_duplicate_count == 1
    assert len(stats.duplicate_rows) == 2


# 14. normalized_term 중복 탐지 (괄호/공백 차이)
def test_compute_duplicates_detects_normalized_match():
    terms = [
        BokTerm(source_id=1, term="환매청구권(풋백옵션)", english_term="", definition="d1", related_terms=""),
        BokTerm(source_id=2, term="환매청구권 (풋백옵션)", english_term="", definition="d2", related_terms=""),
    ]
    stats = compute_duplicates(terms)

    assert stats.term_exact_duplicate_count == 0
    assert stats.normalized_term_duplicate_count == 1


# 15. PDF 경로 오류
def test_run_raises_file_not_found_for_missing_pdf(tmp_path):
    missing_path = tmp_path / "no_such_file.pdf"
    with pytest.raises(FileNotFoundError):
        run(missing_path)


# 16. 필수 목차를 찾지 못한 경우
def test_analyze_pdf_structure_raises_when_toc_not_found(tmp_path):
    doc = fitz.open()
    page = doc.new_page()
    page.insert_text((72, 72), "Hello World, no table of contents here.")
    pdf_path = tmp_path / "no_toc.pdf"
    doc.save(pdf_path)
    doc.close()

    reopened = fitz.open(pdf_path)
    try:
        with pytest.raises(ValueError):
            analyze_pdf_structure(reopened)
    finally:
        reopened.close()


def test_normalize_whitespace_collapses_and_restores_entities():
    assert normalize_whitespace("가계부실위험지수\n\t(HDRI)  &amp; 기타") == "가계부실위험지수 (HDRI) & 기타"
