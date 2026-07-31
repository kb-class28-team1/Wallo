"""금융감독원/재정경제부/한국은행 금융용어 CSV 3종을 하나의 financial_term 데이터셋으로 병합한다.

입력: ai/data/processed/{fss_terms,moef_terms,bok_terms}.csv
출력: financial_terms_merged.csv/json, financial_terms_duplicates.csv,
      financial_terms_merge_report.txt, financial_term_insert.sql

중복 판정은 normalized_term(공백/괄호/슬래시/하이픈/가운데점/쉼표 제거 + 영문 소문자화) 기준이며,
동일 normalized_term을 가진 후보 중 출처 우선순위(한국은행 > 금융감독원 > 재정경제부)가 가장 높은
쪽의 정의를 채택한다. 탈락한 후보는 삭제하지 않고 financial_terms_duplicates.csv에 모두 남긴다.
"""

from __future__ import annotations

import csv
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "processed"

FSS_PATH = DATA_DIR / "fss_terms.csv"
MOEF_PATH = DATA_DIR / "moef_terms.csv"
BOK_PATH = DATA_DIR / "bok_terms.csv"

MERGED_CSV_PATH = DATA_DIR / "financial_terms_merged.csv"
MERGED_JSON_PATH = DATA_DIR / "financial_terms_merged.json"
DUPLICATES_PATH = DATA_DIR / "financial_terms_duplicates.csv"
REPORT_PATH = DATA_DIR / "financial_terms_merge_report.txt"
SQL_PATH = DATA_DIR / "financial_term_insert.sql"

MERGED_FIELDNAMES = ["term", "normalized_term", "definition", "source", "source_url"]
DUPLICATE_FIELDNAMES = ["normalized_term", "selected_source", "removed_source", "selected_term", "removed_term"]

# 출처 우선순위: 숫자가 작을수록 우선 채택된다.
SOURCE_PRIORITY = {"한국은행": 1, "금융감독원": 2, "재정경제부": 3}
DEFAULT_PRIORITY = max(SOURCE_PRIORITY.values()) + 1

NORMALIZE_STRIP_PATTERN = re.compile(r"[()\[\]{}/\-–—·ㆍ,\s]")


@dataclass
class SourceTerm:
    term: str
    definition: str
    source: str
    source_url: str


def normalize_term(term: str) -> str:
    """비교용 normalized_term 생성: 영문 소문자화 + 공백/괄호/슬래시/하이픈/가운데점/쉼표 제거."""
    return NORMALIZE_STRIP_PATTERN.sub("", term.lower())


def read_source_csv(path: Path) -> list[SourceTerm]:
    if not path.exists():
        raise FileNotFoundError(f"입력 파일을 찾을 수 없습니다: {path}")
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        return [
            SourceTerm(
                term=(row.get("term") or "").strip(),
                definition=(row.get("definition") or "").strip(),
                source=(row.get("source") or "").strip(),
                source_url=(row.get("source_url") or "").strip(),
            )
            for row in reader
        ]


def _priority(source: str) -> int:
    return SOURCE_PRIORITY.get(source, DEFAULT_PRIORITY)


def merge_records(records: list[SourceTerm]) -> tuple[list[dict], list[dict], list[tuple[str, int]]]:
    """레코드를 normalized_term 기준으로 묶어 (최종 행, 중복 행, 그룹 크기 목록)을 반환한다."""
    groups: dict[str, list[SourceTerm]] = defaultdict(list)
    for rec in records:
        groups[normalize_term(rec.term)].append(rec)

    final_rows: list[dict] = []
    duplicate_rows: list[dict] = []
    group_sizes: list[tuple[str, int]] = []

    for key, group in groups.items():
        ordered = sorted(group, key=lambda r: _priority(r.source))
        winner = ordered[0]
        losers = ordered[1:]

        final_rows.append(
            {
                "term": winner.term,
                "normalized_term": key,
                "definition": winner.definition,
                "source": winner.source,
                "source_url": winner.source_url,
            }
        )

        for loser in losers:
            duplicate_rows.append(
                {
                    "normalized_term": key,
                    "selected_source": winner.source,
                    "removed_source": loser.source,
                    "selected_term": winner.term,
                    "removed_term": loser.term,
                }
            )

        group_sizes.append((key, len(group)))

    group_sizes.sort(key=lambda x: x[1], reverse=True)
    return final_rows, duplicate_rows, group_sizes


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def build_insert_sql(final_rows: list[dict]) -> str:
    lines = [
        "-- financial_term 테이블 INSERT 문 (병합된 금융용어 데이터)",
        "-- 생성 스크립트: ai/scripts/merge_financial_terms.py",
        "USE wallo;",
        "",
    ]
    for row in final_rows:
        term_name = sql_escape(row["term"])
        description = sql_escape(row["definition"])
        source = sql_escape(row["source"])
        lines.append(
            "INSERT INTO financial_term (term_name, description, source) "
            f"VALUES ('{term_name}', '{description}', '{source}');"
        )
    return "\n".join(lines) + "\n"


def save_merged_csv(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=MERGED_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def save_merged_json(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=2)


def save_duplicates_csv(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=DUPLICATE_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def save_sql(sql_text: str, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        f.write(sql_text)


def build_report_text(
    fss_count: int,
    moef_count: int,
    bok_count: int,
    final_rows: list[dict],
    group_sizes: list[tuple[str, int]],
) -> str:
    total_before = fss_count + moef_count + bok_count
    final_count = len(final_rows)
    duplicates_removed = total_before - final_count
    source_counts = Counter(row["source"] for row in final_rows)
    top_duplicates = [g for g in group_sizes if g[1] > 1][:20]

    lines = []
    lines.append("===== 금융용어 병합 검증 결과 =====")
    lines.append(f"FSS 개수: {fss_count}")
    lines.append(f"MOEF 개수: {moef_count}")
    lines.append(f"BOK 개수: {bok_count}")
    lines.append(f"병합 전 총 개수: {total_before}")
    lines.append(f"중복 제거 개수: {duplicates_removed}")
    lines.append(f"최종 개수: {final_count}")
    lines.append("출처별 최종 개수:")
    for source in sorted(source_counts, key=lambda s: _priority(s)):
        lines.append(f"  - {source}: {source_counts[source]}")
    lines.append(f"중복 상위 20개 (normalized_term 기준, 그룹 크기 내림차순, 실제 {len(top_duplicates)}건):")
    for key, size in top_duplicates:
        lines.append(f"  - {key}: {size}건")
    return "\n".join(lines) + "\n"


def run() -> None:
    fss_records = read_source_csv(FSS_PATH)
    moef_records = read_source_csv(MOEF_PATH)
    bok_records = read_source_csv(BOK_PATH)

    all_records = fss_records + moef_records + bok_records
    final_rows, duplicate_rows, group_sizes = merge_records(all_records)

    save_merged_csv(final_rows, MERGED_CSV_PATH)
    save_merged_json(final_rows, MERGED_JSON_PATH)
    save_duplicates_csv(duplicate_rows, DUPLICATES_PATH)

    report_text = build_report_text(len(fss_records), len(moef_records), len(bok_records), final_rows, group_sizes)
    save_sql(build_insert_sql(final_rows), SQL_PATH)

    with REPORT_PATH.open("w", encoding="utf-8") as f:
        f.write(report_text)

    print(report_text)
    print("생성 파일 경로:")
    for path in [MERGED_CSV_PATH, MERGED_JSON_PATH, DUPLICATES_PATH, REPORT_PATH, SQL_PATH]:
        print(f"  - {path}")


def main() -> None:
    run()


if __name__ == "__main__":
    main()
