"""재정경제부 시사경제용어사전 Excel -> 공통 CSV 변환기.

실제 확인 결과 (2026-07-31, 업로드된 원본 파일 기준):
- 시트: 단일 시트 "Sheet1"
- 컬럼: 순번, 주제, 용어, 설명 (헤더가 1행에 그대로 존재)
- 전체 데이터 행 수: 3030
"""

from __future__ import annotations

import argparse
import csv
import html
import re
from collections import Counter
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Optional

import pandas as pd

DEFAULT_EXCEL_PATH = Path(r"C:\Users\HYUNJI\Downloads\20260731_시사경제용어사전.xlsx")
SOURCE_NAME = "재정경제부"

REQUIRED_COLUMNS = ["순번", "주제", "용어", "설명"]

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "processed"
CSV_PATH = DATA_DIR / "moef_terms.csv"
INVALID_ROWS_PATH = DATA_DIR / "moef_invalid_rows.csv"
DUPLICATE_TERMS_PATH = DATA_DIR / "moef_duplicate_terms.csv"

CSV_FIELDNAMES = ["source_id", "category", "term", "english_term", "definition", "source", "source_url"]
INVALID_ROW_FIELDNAMES = ["source_id_raw", "category", "term", "definition", "reasons"]
DUPLICATE_ROW_FIELDNAMES = [
    "source_id",
    "category",
    "term",
    "term_no_space",
    "normalized_term",
    "duplicate_by_source_id",
    "duplicate_by_term_exact",
    "duplicate_by_term_no_space",
    "duplicate_by_normalized_term",
]

# 괄호류 문자와 일부 구분 기호(가운뎃점, 하이픈, 밑줄, 슬래시)·공백을 제거해 비교용 term을 만든다.
DEDUP_SYMBOL_PATTERN = re.compile(r"[()\[\]{}\s·ㆍ\-_/]")


@dataclass
class MoefTerm:
    source_id: Optional[int]
    category: str
    term: str
    definition: str
    english_term: str = ""
    source: str = SOURCE_NAME
    source_url: str = ""


@dataclass
class ExtractionResult:
    valid_terms: list[MoefTerm]
    invalid_rows: list[dict]
    total_rows: int
    empty_term_count: int
    empty_definition_count: int
    empty_category_count: int
    source_id_warning_count: int


@dataclass
class DuplicateStats:
    source_id_duplicate_count: int
    term_exact_duplicate_count: int
    term_no_space_duplicate_count: int
    normalized_term_duplicate_count: int
    duplicate_rows: list[dict] = field(default_factory=list)


def normalize_whitespace(value) -> str:
    """앞뒤 공백 제거, 탭/줄바꿈을 공백으로 치환, 연속 공백 축소, HTML entity 복원."""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return ""
    text = html.unescape(str(value))
    text = text.replace("\t", " ")
    return re.sub(r"\s+", " ", text).strip()


def build_normalized_term(term: str) -> str:
    return DEDUP_SYMBOL_PATTERN.sub("", term)


def parse_source_id(raw) -> Optional[int]:
    if raw is None or (isinstance(raw, float) and pd.isna(raw)):
        return None
    text = str(raw).strip()
    if text == "":
        return None
    try:
        return int(float(text))
    except ValueError:
        return None


def list_sheets(path: Path) -> list[str]:
    with pd.ExcelFile(path) as excel_file:
        return excel_file.sheet_names


def select_sheet(path: Path, sheet_names: list[str]) -> tuple[str, str]:
    """용어 데이터가 들어있는 시트를 선택하고 (시트명, 선택 근거)를 반환한다."""
    if len(sheet_names) == 1:
        return sheet_names[0], "시트가 1개뿐이므로 자동 선택"

    for name in sheet_names:
        header = pd.read_excel(path, sheet_name=name, nrows=0).columns.tolist()
        if all(col in header for col in REQUIRED_COLUMNS):
            return name, f"필수 컬럼({', '.join(REQUIRED_COLUMNS)})을 모두 포함하는 시트를 자동 선택"

    raise ValueError(
        f"필수 컬럼({', '.join(REQUIRED_COLUMNS)})을 포함하는 시트를 찾지 못했습니다. "
        f"시트 목록: {sheet_names}"
    )


def validate_required_columns(columns: list[str]) -> None:
    missing = [col for col in REQUIRED_COLUMNS if col not in columns]
    if missing:
        raise ValueError(f"필수 컬럼이 존재하지 않습니다: {missing}. 실제 컬럼: {columns}")


def load_excel_rows(path: Path, sheet_name: str) -> pd.DataFrame:
    return pd.read_excel(path, sheet_name=sheet_name, dtype=str)


def extract_terms(df: pd.DataFrame, source_url: str) -> ExtractionResult:
    validate_required_columns(df.columns.tolist())

    valid_terms: list[MoefTerm] = []
    invalid_rows: list[dict] = []
    empty_term_count = 0
    empty_definition_count = 0
    empty_category_count = 0
    source_id_warning_count = 0

    for _, row in df.iterrows():
        raw_source_id = row.get("순번")
        category = normalize_whitespace(row.get("주제"))
        term = normalize_whitespace(row.get("용어"))
        definition = normalize_whitespace(row.get("설명"))
        source_id = parse_source_id(raw_source_id)

        reasons = []
        if term == "":
            empty_term_count += 1
            reasons.append("term_empty")
        if definition == "":
            empty_definition_count += 1
            reasons.append("definition_empty")
        if category == "":
            empty_category_count += 1
        if source_id is None:
            source_id_warning_count += 1
            reasons.append("source_id_invalid")

        if reasons:
            invalid_rows.append(
                {
                    "source_id_raw": "" if raw_source_id is None or pd.isna(raw_source_id) else str(raw_source_id),
                    "category": category,
                    "term": term,
                    "definition": definition,
                    "reasons": ";".join(reasons),
                }
            )

        if "term_empty" in reasons or "definition_empty" in reasons:
            continue

        valid_terms.append(
            MoefTerm(
                source_id=source_id,
                category=category,
                term=term,
                definition=definition,
                source_url=source_url,
            )
        )

    return ExtractionResult(
        valid_terms=valid_terms,
        invalid_rows=invalid_rows,
        total_rows=len(df),
        empty_term_count=empty_term_count,
        empty_definition_count=empty_definition_count,
        empty_category_count=empty_category_count,
        source_id_warning_count=source_id_warning_count,
    )


def compute_duplicates(terms: list[MoefTerm]) -> DuplicateStats:
    source_ids = [t.source_id for t in terms if t.source_id is not None]
    id_counter = Counter(source_ids)
    id_dup_count = sum(c - 1 for c in id_counter.values() if c > 1)

    non_empty_terms = [t for t in terms if t.term]
    exact_counter = Counter(t.term for t in non_empty_terms)
    exact_dup_count = sum(c - 1 for c in exact_counter.values() if c > 1)

    no_space_values = [re.sub(r"\s+", "", t.term) for t in non_empty_terms]
    no_space_counter = Counter(no_space_values)
    no_space_dup_count = sum(c - 1 for c in no_space_counter.values() if c > 1)

    normalized_values = [build_normalized_term(t.term) for t in non_empty_terms]
    normalized_counter = Counter(normalized_values)
    normalized_dup_count = sum(c - 1 for c in normalized_counter.values() if c > 1)

    duplicate_rows: list[dict] = []
    for term_obj, no_space, normalized in zip(non_empty_terms, no_space_values, normalized_values):
        dup_by_id = term_obj.source_id is not None and id_counter[term_obj.source_id] > 1
        dup_by_exact = exact_counter[term_obj.term] > 1
        dup_by_no_space = no_space_counter[no_space] > 1
        dup_by_normalized = normalized_counter[normalized] > 1

        if dup_by_id or dup_by_exact or dup_by_no_space or dup_by_normalized:
            duplicate_rows.append(
                {
                    "source_id": term_obj.source_id,
                    "category": term_obj.category,
                    "term": term_obj.term,
                    "term_no_space": no_space,
                    "normalized_term": normalized,
                    "duplicate_by_source_id": dup_by_id,
                    "duplicate_by_term_exact": dup_by_exact,
                    "duplicate_by_term_no_space": dup_by_no_space,
                    "duplicate_by_normalized_term": dup_by_normalized,
                }
            )

    return DuplicateStats(
        source_id_duplicate_count=id_dup_count,
        term_exact_duplicate_count=exact_dup_count,
        term_no_space_duplicate_count=no_space_dup_count,
        normalized_term_duplicate_count=normalized_dup_count,
        duplicate_rows=duplicate_rows,
    )


def save_csv(terms: list[MoefTerm], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDNAMES)
        writer.writeheader()
        for term in terms:
            writer.writerow(asdict(term))


def save_invalid_rows(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=INVALID_ROW_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def save_duplicate_terms(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=DUPLICATE_ROW_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def print_report(
    excel_path: Path,
    sheet_names: list[str],
    sheet_name: str,
    selection_reason: str,
    columns: list[str],
    result: ExtractionResult,
    dup_stats: DuplicateStats,
    output_paths: list[Path],
) -> None:
    print("===== MOEF Excel 확인 결과 =====")
    print(f"실제 파일 경로: {excel_path}")
    print(f"시트 목록: {sheet_names}")
    print(f"사용한 시트명: {sheet_name} ({selection_reason})")
    print(f"실제 컬럼명: {columns}")
    print(f"전체 행 수: {result.total_rows}")

    valid_ids = [t.source_id for t in result.valid_terms if t.source_id is not None]

    print("===== MOEF 용어 변환 결과 =====")
    print(f"원본 전체 행 수: {result.total_rows}")
    print(f"정상 변환 행 수: {len(result.valid_terms)}")
    print(f"제외 또는 경고 행 수: {len(result.invalid_rows)}")
    print(f"빈 term 개수: {result.empty_term_count}")
    print(f"빈 definition 개수: {result.empty_definition_count}")
    print(f"빈 category 개수: {result.empty_category_count}")
    print(f"source_id 중복 개수: {dup_stats.source_id_duplicate_count}")
    print(f"term 완전 일치 중복 개수: {dup_stats.term_exact_duplicate_count}")
    print(f"normalized_term 중복 개수: {dup_stats.normalized_term_duplicate_count}")
    print(f"source_id 최소값: {min(valid_ids) if valid_ids else None}")
    print(f"source_id 최대값: {max(valid_ids) if valid_ids else None}")
    print("생성 파일 경로:")
    for path in output_paths:
        print(f"  - {path}")


def run(excel_path: Path) -> None:
    if not excel_path.exists():
        raise FileNotFoundError(f"Excel 파일을 찾을 수 없습니다: {excel_path}")

    sheet_names = list_sheets(excel_path)
    sheet_name, selection_reason = select_sheet(excel_path, sheet_names)

    df = load_excel_rows(excel_path, sheet_name)
    validate_required_columns(df.columns.tolist())

    source_url = excel_path.name
    result = extract_terms(df, source_url)
    dup_stats = compute_duplicates(result.valid_terms)

    save_csv(result.valid_terms, CSV_PATH)
    save_invalid_rows(result.invalid_rows, INVALID_ROWS_PATH)
    save_duplicate_terms(dup_stats.duplicate_rows, DUPLICATE_TERMS_PATH)

    print_report(
        excel_path=excel_path,
        sheet_names=sheet_names,
        sheet_name=sheet_name,
        selection_reason=selection_reason,
        columns=df.columns.tolist(),
        result=result,
        dup_stats=dup_stats,
        output_paths=[CSV_PATH, INVALID_ROWS_PATH, DUPLICATE_TERMS_PATH],
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="MOEF 시사경제용어사전 Excel -> CSV 변환기")
    parser.add_argument("--excel-path", type=Path, default=DEFAULT_EXCEL_PATH)
    args = parser.parse_args()
    run(args.excel_path)


if __name__ == "__main__":
    main()
