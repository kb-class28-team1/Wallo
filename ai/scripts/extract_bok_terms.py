"""한국은행 「2026 경제금융용어 800선」 PDF -> 공통 CSV 변환기.

실제 원본 PDF(PyMuPDF `get_text("dict")` 기준)를 직접 분석해 확인한 구조:

- 총 428페이지(0-index). 목차: pdf_page_index 3~16 (2단 구성). 본문: pdf_page_index 18~422.
  423~427페이지는 집필자/발행 정보 등 부록성 콜로폰 페이지로 본문이 아니다.
- 본문 한 페이지 안에 여러 용어가 이어질 수 있고, 한 용어의 정의가 다음 페이지로 이어질 수 있다.
- 레이아웃 판별 기준(폰트 이름은 원본 PDF에 실제로 내장된 값):
  - 제목: size≈14.0, font에 "SUIT-ExtraBold" 포함(영문·숫자가 섞이면 "DIN-Bold"도 함께 나타남)
  - 본문 문단/산식: font "KoPubBatangLight"(10.5) 또는 "KoPubDotumLight"(10.0)
  - 연관검색어 줄: font에 "KoPubDotumBold" 또는 "KoPubDotumMedium" 포함(size 9.0~9.5)
  - 책자 표시 페이지 번호: font "Dinlig", 페이지 하단(y0>675) 고정 위치
  - 머리말(러닝헤더)·색인 탭 등 잡음: y0<80, y0>675(페이지번호 제외), 또는 x0>300
- 목차는 2단 구성(왼쪽 컬럼 x0≈70.9, 오른쪽 컬럼 x0≈275)이며, 각 항목은
  "용어명 + 점선 리더 + 페이지번호" 한 줄이 기본이지만 긴 용어명은 다음 줄(들여쓰기 됨)로
  이어지고, 이어지는 줄에는 페이지번호가 없다. 한글은 하이픈 없이 줄바꿈되므로 이어붙일 때
  공백을 넣지 않는다(예: "경제활동인구/비경제활동인구/" + "경제활동참가율").
"""

from __future__ import annotations

import argparse
import csv
import html
import re
import statistics
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import fitz  # PyMuPDF

DEFAULT_PDF_PATH = Path(r"C:\Users\HYUNJI\Downloads\2026_경제금융용어 800선 (1).pdf")
SOURCE_NAME = "한국은행"

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "processed"
CSV_PATH = DATA_DIR / "bok_terms.csv"
INVALID_PATH = DATA_DIR / "bok_invalid_terms.csv"
DUPLICATE_PATH = DATA_DIR / "bok_duplicate_terms.csv"
WARNING_PATH = DATA_DIR / "bok_parse_warnings.csv"

CSV_FIELDNAMES = ["source_id", "term", "english_term", "definition", "related_terms", "source", "source_url"]
INVALID_FIELDNAMES = ["source_id", "term", "reason", "page", "details"]
WARNING_FIELDNAMES = ["source_id", "term", "warning_type", "page", "details"]
DUPLICATE_FIELDNAMES = [
    "source_id",
    "term",
    "term_no_space",
    "normalized_term",
    "duplicate_by_term_exact",
    "duplicate_by_term_no_space",
    "duplicate_by_normalized_term",
]

# ---- 레이아웃 판별 상수 (실제 PDF 분석 결과) ----
# 제목은 보통 한글=SUIT-ExtraBold + 영문/숫자=DIN-Bold가 같은 줄에 섞여 나오지만,
# "Nowcasting", "OIS"처럼 한글이 전혀 없는 순수 영문 제목은 DIN-Bold만 단독으로 쓰인다.
TITLE_FONT_MARKERS = {"SUIT-ExtraBold", "DIN-Bold"}
TITLE_SIZE = 14.0
RELATED_FONT_MARKERS = {"KoPubDotumBold", "KoPubDotumMedium"}
PAGE_NUMBER_FONT = "Dinlig"

NOISE_Y_TOP = 80.0
NOISE_Y_BOTTOM = 675.0
NOISE_X_RIGHT = 300.0

TOC_COLUMN_RANGES = [(60.0, 95.0), (265.0, 300.0)]
TOC_Y_MIN = 70.0
TOC_MIN_ENTRIES_PER_PAGE = 5

RELATED_TERMS_PREFIX = "연관검색어"

SHORT_DEFINITION_THRESHOLD = 15
LONG_DEFINITION_THRESHOLD = 2000

TOC_TERMINAL_PATTERN = re.compile(r"^(.*?)[\s.·]{5,}(\d+)\s*$")
TOC_TRAILING_LEADER_PATTERN = re.compile(r"[\s.·]+$")
BRACKET_PATTERN = re.compile(r"\(([^()]*)\)")
HANGUL_PATTERN = re.compile(r"[\uac00-\ud7a3]")
LATIN_PATTERN = re.compile(r"[A-Za-z]")
DEDUP_SYMBOL_PATTERN = re.compile(r"[()\[\]{}\s·ㆍ\-–—_/]")


@dataclass
class TocEntry:
    source_id: int
    term: str
    toc_page_number: int
    order: int


@dataclass
class BokTerm:
    source_id: Optional[int]
    term: str
    english_term: str
    definition: str
    related_terms: str
    source: str = SOURCE_NAME
    source_url: str = ""
    pdf_page_index: int = -1
    booklet_page: Optional[int] = None

    def to_csv_row(self) -> dict:
        return {key: getattr(self, key) for key in CSV_FIELDNAMES}


@dataclass
class DuplicateStats:
    term_exact_duplicate_count: int
    term_no_space_duplicate_count: int
    normalized_term_duplicate_count: int
    duplicate_rows: list = field(default_factory=list)


# =========================================================================
# 순수 텍스트/데이터 변환 함수 (PDF 없이 단위 테스트 가능)
# =========================================================================

def normalize_whitespace(value) -> str:
    if value is None:
        return ""
    text = html.unescape(str(value))
    text = text.replace("\t", " ")
    return re.sub(r"\s+", " ", text).strip()


def build_normalized_term(term: str) -> str:
    return DEDUP_SYMBOL_PATTERN.sub("", term)


QUOTE_NORMALIZE_TABLE = str.maketrans({"‘": "'", "’": "'", "“": '"', "”": '"'})


def build_match_key(term: str) -> str:
    """TOC와 본문 제목을 매칭하기 위한 키. 괄호/기호/공백 차이와 스마트따옴표 표기 차이를 흡수한다."""
    normalized = term.translate(QUOTE_NORMALIZE_TABLE)
    return DEDUP_SYMBOL_PATTERN.sub("", normalized).lower()


def extract_english_term(term_text: str) -> str:
    """term 안의 괄호 내용 중 한글이 없고 영문이 포함된 것만 english_term으로 추출한다."""
    for match in BRACKET_PATTERN.finditer(term_text):
        content = match.group(1).strip()
        if not content:
            continue
        if HANGUL_PATTERN.search(content):
            continue
        if LATIN_PATTERN.search(content):
            return normalize_whitespace(content)
    return ""


def split_related_terms(related_line_texts: list[str]) -> tuple[str, bool]:
    """연관검색어 줄(들)을 '|' 구분 문자열로 분리한다. 반환값: (related_terms, parse_failed)."""
    if not related_line_texts:
        return "", False
    joined = " ".join(related_line_texts)
    joined = joined.replace(RELATED_TERMS_PREFIX, " ")
    joined = normalize_whitespace(joined)
    if not joined:
        return "", True
    parts = [normalize_whitespace(p) for p in joined.split(",")]
    parts = [p for p in parts if p]
    if not parts:
        return "", True
    return "|".join(parts), False


def _in_toc_column(x0: float) -> Optional[int]:
    for idx, (lo, hi) in enumerate(TOC_COLUMN_RANGES):
        if lo <= x0 <= hi:
            return idx
    return None


def parse_toc_pages(pages: list[list[dict]]) -> tuple[list[TocEntry], list[str]]:
    """목차 페이지들(페이지별 원시 line dict 리스트)에서 TocEntry 목록을 추출한다.

    각 line dict는 최소한 {"text": str, "x0": float, "y0": float} 키를 가져야 한다.
    긴 용어명이 다음 줄로 이어지는 경우 페이지번호가 없는 줄이 나오는데, 이런 줄은 공백 없이
    다음 줄과 이어붙인다(한글 줄바꿈은 하이픈이나 공백을 쓰지 않기 때문).
    """
    entries: list[TocEntry] = []
    pending = ""
    order = 0

    for page_lines in pages:
        candidates = []
        for line in page_lines:
            if line["y0"] < TOC_Y_MIN:
                continue
            col = _in_toc_column(line["x0"])
            if col is None:
                continue
            candidates.append((col, line["y0"], line["text"]))
        candidates.sort(key=lambda c: (c[0], c[1]))

        for _, _, text in candidates:
            match = TOC_TERMINAL_PATTERN.match(text.strip())
            if match:
                fragment, page_num = match.group(1), int(match.group(2))
                term_text = normalize_whitespace(pending + fragment)
                pending = ""
                if not term_text:
                    continue
                order += 1
                entries.append(TocEntry(source_id=order, term=term_text, toc_page_number=page_num, order=order))
            else:
                pending += TOC_TRAILING_LEADER_PATTERN.sub("", text)

    unresolved = [pending] if pending.strip() else []
    return entries, unresolved


def classify_line(line: dict) -> str:
    """line dict(x0,y0,fonts,sizes,text)를 noise/pagenum/title/related/content 중 하나로 분류한다."""
    fonts = line.get("fonts", frozenset())
    sizes = line.get("sizes", frozenset())
    x0, y0 = line["x0"], line["y0"]

    if PAGE_NUMBER_FONT in fonts and y0 > NOISE_Y_BOTTOM:
        return "pagenum"
    if y0 < NOISE_Y_TOP or y0 > NOISE_Y_BOTTOM or x0 > NOISE_X_RIGHT:
        return "noise"
    if fonts & TITLE_FONT_MARKERS and any(abs(s - TITLE_SIZE) < 0.6 for s in sizes):
        return "title"
    if fonts & RELATED_FONT_MARKERS:
        return "related"
    return "content"


def segment_body_into_chunks(classified_lines: list[dict]) -> list[dict]:
    """분류된 본문 line들(순서대로, 페이지 경계 포함)을 용어 단위 청크로 나눈다.

    각 line dict는 {"text","kind","pdf_page_index","booklet_page"}를 가져야 하며
    kind가 "title"인 줄부터 다음 title 직전까지를 하나의 청크로 묶는다. 페이지가 바뀌어도
    새 title이 나오기 전까지는 같은 청크로 이어붙여 정의가 다음 페이지로 넘어가는 경우를 처리한다.
    """
    chunks: list[dict] = []
    current: Optional[dict] = None

    for line in classified_lines:
        kind = line["kind"]
        if kind == "title":
            if current is not None:
                chunks.append(current)
            current = {
                "title_text": line["text"],
                "pdf_page_index": line["pdf_page_index"],
                "booklet_page": line.get("booklet_page"),
                "def_line_texts": [],
                "related_line_texts": [],
            }
        elif current is None:
            continue
        elif kind == "related":
            current["related_line_texts"].append(line["text"])
        else:
            current["def_line_texts"].append(line["text"])

    if current is not None:
        chunks.append(current)
    return chunks


def match_chunks_to_toc(
    chunks: list[dict], toc_entries: list[TocEntry], source_url: str
) -> tuple[list[BokTerm], list[dict]]:
    match_index: dict[str, list[TocEntry]] = {}
    for entry in toc_entries:
        match_index.setdefault(build_match_key(entry.term), []).append(entry)

    terms: list[BokTerm] = []
    warnings: list[dict] = []
    matched_source_ids: set[int] = set()

    for chunk in chunks:
        title_text = normalize_whitespace(chunk["title_text"])
        candidates = match_index.get(build_match_key(title_text))

        definition = normalize_whitespace(" ".join(chunk["def_line_texts"]))
        related_terms, related_failed = split_related_terms(chunk["related_line_texts"])

        if not candidates:
            warnings.append(
                {
                    "source_id": "",
                    "term": title_text,
                    "warning_type": "unmatched_body_title",
                    "page": chunk["booklet_page"],
                    "details": f"TOC에서 일치하는 용어를 찾지 못함 (pdf_page={chunk['pdf_page_index']})",
                }
            )
            continue

        entry = candidates[0]
        if entry.source_id in matched_source_ids:
            warnings.append(
                {
                    "source_id": entry.source_id,
                    "term": entry.term,
                    "warning_type": "duplicate_title_match",
                    "page": chunk["booklet_page"],
                    "details": f"이미 매칭된 TOC 항목이 본문에서 다시 발견됨 (pdf_page={chunk['pdf_page_index']})",
                }
            )
        else:
            matched_source_ids.add(entry.source_id)

        if related_failed:
            warnings.append(
                {
                    "source_id": entry.source_id,
                    "term": entry.term,
                    "warning_type": "related_terms_parse_failed",
                    "page": chunk["booklet_page"],
                    "details": "연관검색어 줄은 감지되었으나 항목을 추출하지 못함",
                }
            )

        if not definition:
            warnings.append(
                {
                    "source_id": entry.source_id,
                    "term": entry.term,
                    "warning_type": "empty_definition",
                    "page": chunk["booklet_page"],
                    "details": f"pdf_page={chunk['pdf_page_index']}",
                }
            )
        elif len(definition) < SHORT_DEFINITION_THRESHOLD:
            warnings.append(
                {
                    "source_id": entry.source_id,
                    "term": entry.term,
                    "warning_type": "suspiciously_short_definition",
                    "page": chunk["booklet_page"],
                    "details": f"definition_length={len(definition)}",
                }
            )
        elif len(definition) > LONG_DEFINITION_THRESHOLD:
            warnings.append(
                {
                    "source_id": entry.source_id,
                    "term": entry.term,
                    "warning_type": "suspiciously_long_definition",
                    "page": chunk["booklet_page"],
                    "details": f"definition_length={len(definition)}",
                }
            )

        terms.append(
            BokTerm(
                source_id=entry.source_id,
                term=entry.term,
                english_term=extract_english_term(entry.term),
                definition=definition,
                related_terms=related_terms,
                source_url=source_url,
                pdf_page_index=chunk["pdf_page_index"],
                booklet_page=chunk["booklet_page"],
            )
        )

    for entry in toc_entries:
        if entry.source_id not in matched_source_ids:
            warnings.append(
                {
                    "source_id": entry.source_id,
                    "term": entry.term,
                    "warning_type": "title_not_found",
                    "page": entry.toc_page_number,
                    "details": "본문에서 해당 제목의 용어를 찾지 못함",
                }
            )

    return terms, warnings


def compute_duplicates(terms: list[BokTerm]) -> DuplicateStats:
    exact_counter = Counter(t.term for t in terms)
    no_space_values = [re.sub(r"\s+", "", t.term) for t in terms]
    no_space_counter = Counter(no_space_values)
    normalized_values = [build_normalized_term(t.term) for t in terms]
    normalized_counter = Counter(normalized_values)

    exact_dup = sum(c - 1 for c in exact_counter.values() if c > 1)
    no_space_dup = sum(c - 1 for c in no_space_counter.values() if c > 1)
    normalized_dup = sum(c - 1 for c in normalized_counter.values() if c > 1)

    rows = []
    for term, no_space, normalized in zip(terms, no_space_values, normalized_values):
        by_exact = exact_counter[term.term] > 1
        by_no_space = no_space_counter[no_space] > 1
        by_normalized = normalized_counter[normalized] > 1
        if by_exact or by_no_space or by_normalized:
            rows.append(
                {
                    "source_id": term.source_id,
                    "term": term.term,
                    "term_no_space": no_space,
                    "normalized_term": normalized,
                    "duplicate_by_term_exact": by_exact,
                    "duplicate_by_term_no_space": by_no_space,
                    "duplicate_by_normalized_term": by_normalized,
                }
            )

    return DuplicateStats(
        term_exact_duplicate_count=exact_dup,
        term_no_space_duplicate_count=no_space_dup,
        normalized_term_duplicate_count=normalized_dup,
        duplicate_rows=rows,
    )


def build_invalid_rows(terms: list[BokTerm], toc_entries: list[TocEntry]) -> list[dict]:
    matched_ids = {t.source_id for t in terms}
    rows = []
    for term in terms:
        if not term.definition:
            rows.append(
                {
                    "source_id": term.source_id,
                    "term": term.term,
                    "reason": "empty_definition",
                    "page": term.booklet_page,
                    "details": f"pdf_page={term.pdf_page_index}",
                }
            )
    for entry in toc_entries:
        if entry.source_id not in matched_ids:
            rows.append(
                {
                    "source_id": entry.source_id,
                    "term": entry.term,
                    "reason": "title_not_found",
                    "page": entry.toc_page_number,
                    "details": "본문 미매칭",
                }
            )
    return rows


# =========================================================================
# PDF I/O 계층 (PyMuPDF 직접 사용)
# =========================================================================

def iter_page_lines(page, pdf_page_index: int):
    d = page.get_text("dict")
    for b_i, block in enumerate(d.get("blocks", [])):
        if block.get("type") != 0:
            continue
        for l_i, line in enumerate(block.get("lines", [])):
            spans = line.get("spans", [])
            text = "".join(s["text"] for s in spans)
            if not text.strip():
                continue
            bbox = line["bbox"]
            yield {
                "text": text,
                "x0": bbox[0],
                "y0": bbox[1],
                "x1": bbox[2],
                "y1": bbox[3],
                "sizes": frozenset(round(s["size"], 1) for s in spans),
                "fonts": frozenset(s["font"] for s in spans),
                "pdf_page_index": pdf_page_index,
                "block": b_i,
                "line": l_i,
            }


def _is_title_line(line: dict) -> bool:
    return classify_line(line) == "title"


def analyze_pdf_structure(doc) -> dict:
    page_text_lengths = []
    toc_candidate_counts = []
    has_title_flags = []

    for i in range(doc.page_count):
        page_text_lengths.append(len(doc[i].get_text("text")))
        lines = list(iter_page_lines(doc[i], i))
        toc_count = sum(
            1
            for line in lines
            if line["y0"] >= TOC_Y_MIN
            and _in_toc_column(line["x0"]) is not None
            and TOC_TERMINAL_PATTERN.match(line["text"].strip())
        )
        toc_candidate_counts.append(toc_count)
        has_title_flags.append(any(_is_title_line(line) for line in lines))

    toc_pages = [i for i, c in enumerate(toc_candidate_counts) if c >= TOC_MIN_ENTRIES_PER_PAGE]
    if not toc_pages:
        raise ValueError("목차 페이지를 찾지 못했습니다 (toc_parse_failed).")
    toc_start, toc_end = min(toc_pages), max(toc_pages)

    title_pages = [i for i, flag in enumerate(has_title_flags) if flag]
    if not title_pages:
        raise ValueError("본문 페이지를 찾지 못했습니다.")
    body_start, body_end = min(title_pages), max(title_pages)

    appendix_pages = [i for i in range(body_end + 1, doc.page_count) if page_text_lengths[i] > 50]

    return {
        "page_count": doc.page_count,
        "text_extractable_pages": sum(1 for n in page_text_lengths if n > 0),
        "toc_start": toc_start,
        "toc_end": toc_end,
        "body_start": body_start,
        "body_end": body_end,
        "has_index_or_appendix": len(appendix_pages) > 0,
        "appendix_pages": appendix_pages,
        "page_text_lengths": page_text_lengths,
    }


def load_toc_pages(doc, toc_start: int, toc_end: int) -> list[list[dict]]:
    return [list(iter_page_lines(doc[i], i)) for i in range(toc_start, toc_end + 1)]


def load_body_classified_lines(doc, body_start: int, body_end: int) -> list[dict]:
    result: list[dict] = []
    for i in range(body_start, body_end + 1):
        raw_lines = list(iter_page_lines(doc[i], i))
        booklet_page = None
        page_content = []
        for line in raw_lines:
            kind = classify_line(line)
            if kind == "pagenum":
                stripped = line["text"].strip()
                if stripped.isdigit():
                    booklet_page = int(stripped)
                continue
            if kind == "noise":
                continue
            annotated = dict(line)
            annotated["kind"] = kind
            page_content.append(annotated)
        for line in page_content:
            line["booklet_page"] = booklet_page
        result.extend(page_content)
    return result


# =========================================================================
# 저장 / 리포트
# =========================================================================

def save_csv(terms: list[BokTerm], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDNAMES)
        writer.writeheader()
        for term in terms:
            writer.writerow(term.to_csv_row())


def save_dict_rows(rows: list[dict], fieldnames: list[str], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


SAMPLE_TERMS_TO_VERIFY = [
    "가계부실위험지수(HDRI)",
    "가계수지",
    "가계순저축률",
    "기준금리",
    "국내총생산(GDP)",
    "경제활동인구/비경제활동인구/경제활동참가율",
    "중앙은행 디지털화폐(CBDC)",
    "환매조건부매매/RP/Repo",
    "CAMEL-IR 방식/ROCA 방식/CACREL 방식",
    "NFC(Near Field Communication) 기술",
]


def print_structure_report(structure: dict, pdf_path: Path) -> None:
    lengths = structure["page_text_lengths"]
    print("===== 1단계: PDF 메타데이터/구조 확인 =====")
    print(f"실제 PDF 경로: {pdf_path}")
    print(f"총 페이지 수: {structure['page_count']}")
    print(f"텍스트 추출 가능한 페이지 수: {structure['text_extractable_pages']}")
    print(f"목차 페이지 범위(0-index): {structure['toc_start']} ~ {structure['toc_end']}")
    print(f"본문 페이지 범위(0-index): {structure['body_start']} ~ {structure['body_end']}")
    print(f"색인/부록 존재 여부: {structure['has_index_or_appendix']} (페이지: {structure['appendix_pages']})")
    print(
        "페이지별 텍스트 길이 분포: "
        f"min={min(lengths)}, max={max(lengths)}, "
        f"mean={statistics.mean(lengths):.1f}, median={statistics.median(lengths):.1f}"
    )


def print_final_report(
    toc_entries: list[TocEntry],
    unresolved_toc_fragments: list[str],
    terms: list[BokTerm],
    valid_terms: list[BokTerm],
    warnings: list[dict],
    dup_stats: DuplicateStats,
    output_paths: list[Path],
) -> None:
    print("===== 2단계: 목차 추출 결과 =====")
    print(f"목차에서 추출한 용어 수: {len(toc_entries)} (800개에 근접해야 함)")
    if unresolved_toc_fragments:
        print(f"목차 파싱 미해결 조각: {unresolved_toc_fragments}")

    matched_source_ids = {t.source_id for t in terms}
    failed_count = sum(1 for e in toc_entries if e.source_id not in matched_source_ids)

    warning_type_counts = Counter(w["warning_type"] for w in warnings)

    definitions = [t.definition for t in valid_terms]
    lengths = [len(d) for d in definitions]

    print("===== 검증 결과 =====")
    print(f"본문에서 정상 매칭한 용어 수: {len(matched_source_ids)}")
    print(f"매칭 실패 용어 수: {failed_count}")
    print(f"정의 빈값 개수: {sum(1 for t in terms if not t.definition)}")
    print(f"연관검색어가 있는 용어 수: {sum(1 for t in valid_terms if t.related_terms)}")
    print(f"english_term이 있는 용어 수: {sum(1 for t in valid_terms if t.english_term)}")
    print(f"term 완전 일치 중복 수: {dup_stats.term_exact_duplicate_count}")
    print(f"normalized_term 중복 수: {dup_stats.normalized_term_duplicate_count}")
    if lengths:
        print(f"평균 정의 길이: {statistics.mean(lengths):.1f}")
        shortest = sorted(valid_terms, key=lambda t: len(t.definition))[:10]
        longest = sorted(valid_terms, key=lambda t: len(t.definition), reverse=True)[:10]
        print("가장 짧은 정의 10개:")
        for t in shortest:
            print(f"  - [{t.source_id}] {t.term} ({len(t.definition)}자)")
        print("가장 긴 정의 10개:")
        for t in longest:
            print(f"  - [{t.source_id}] {t.term} ({len(t.definition)}자)")
    print("경고 유형별 개수:")
    for warning_type, count in warning_type_counts.most_common():
        print(f"  - {warning_type}: {count}")
    print("생성 파일 경로:")
    for path in output_paths:
        print(f"  - {path}")


def print_sample_terms(valid_terms: list[BokTerm]) -> None:
    by_term = {t.term: t for t in valid_terms}
    by_match_key = {build_match_key(t.term): t for t in valid_terms}

    print("===== 품질 검증 샘플 =====")
    for sample in SAMPLE_TERMS_TO_VERIFY:
        term = by_term.get(sample) or by_match_key.get(build_match_key(sample))
        if term is None:
            print(f"- {sample}: 추출 결과 없음")
            continue
        print(f"- term: {term.term}")
        print(f"  english_term: {term.english_term!r}")
        print(f"  definition(앞 100자): {term.definition[:100]!r}")
        print(f"  related_terms: {term.related_terms!r}")
        print(f"  PDF 페이지 위치: pdf_page_index={term.pdf_page_index}, booklet_page={term.booklet_page}")


# =========================================================================
# 실행
# =========================================================================

def run(pdf_path: Path) -> None:
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF 파일을 찾을 수 없습니다: {pdf_path}")

    doc = fitz.open(pdf_path)
    try:
        structure = analyze_pdf_structure(doc)
        print_structure_report(structure, pdf_path)

        toc_pages = load_toc_pages(doc, structure["toc_start"], structure["toc_end"])
        toc_entries, unresolved_fragments = parse_toc_pages(toc_pages)

        body_lines = load_body_classified_lines(doc, structure["body_start"], structure["body_end"])
        chunks = segment_body_into_chunks(body_lines)

        source_url = pdf_path.name
        terms, warnings = match_chunks_to_toc(chunks, toc_entries, source_url)

        if unresolved_fragments:
            warnings.append(
                {
                    "source_id": "",
                    "term": "",
                    "warning_type": "toc_parse_failed",
                    "page": "",
                    "details": f"미해결 목차 조각: {unresolved_fragments}",
                }
            )

        valid_terms = [t for t in terms if t.definition]
        dup_stats = compute_duplicates(valid_terms)
        invalid_rows = build_invalid_rows(terms, toc_entries)

        save_csv(valid_terms, CSV_PATH)
        save_dict_rows(invalid_rows, INVALID_FIELDNAMES, INVALID_PATH)
        save_dict_rows(dup_stats.duplicate_rows, DUPLICATE_FIELDNAMES, DUPLICATE_PATH)
        save_dict_rows(warnings, WARNING_FIELDNAMES, WARNING_PATH)

        output_paths = [CSV_PATH, INVALID_PATH, DUPLICATE_PATH, WARNING_PATH]
        print_final_report(toc_entries, unresolved_fragments, terms, valid_terms, warnings, dup_stats, output_paths)
        print_sample_terms(valid_terms)
    finally:
        doc.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="한국은행 경제금융용어 800선 PDF -> CSV 변환기")
    parser.add_argument("--pdf-path", type=Path, default=DEFAULT_PDF_PATH)
    args = parser.parse_args()
    run(args.pdf_path)


if __name__ == "__main__":
    main()
