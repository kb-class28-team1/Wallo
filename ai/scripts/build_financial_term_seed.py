"""병합 금융용어와 짧은 설명 배치를 하나의 팀 공유용 seed SQL로 만든다."""

from __future__ import annotations

import csv
import re
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[2]
PROCESSED_DIR = ROOT_DIR / "ai" / "data" / "processed"
SOURCE_CSV = PROCESSED_DIR / "financial_terms_merged.csv"
SHORT_DEFINITION_DIR = PROCESSED_DIR / "short_definitions"
OUTPUT_SQL = ROOT_DIR / "database" / "seed" / "financial_term_data.sql"
BATCH_SIZE = 100


def sql_literal(value: str | None) -> str:
    if value is None:
        return "NULL"
    escaped = value.replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def load_source_rows() -> list[dict[str, str]]:
    with SOURCE_CSV.open(encoding="utf-8-sig", newline="") as file:
        rows = list(csv.DictReader(file))
    if not rows:
        raise RuntimeError("병합 금융용어 원본이 비어 있습니다.")
    return rows


def load_short_definitions(source_rows: list[dict[str, str]]) -> tuple[dict[int, str], list[int]]:
    term_count = len(source_rows)
    string_value = r"((?:''|\\.|[^'])*)"
    case_pattern = re.compile(rf"WHEN\s+(\d+)\s+THEN\s+'{string_value}'", re.DOTALL)
    update_pattern = re.compile(
        rf"SET\s+short_definition\s*=\s*'{string_value}'\s+WHERE\s+term_id\s*=\s*(\d+)",
        re.DOTALL | re.IGNORECASE,
    )

    def decode(value: str) -> str:
        return value.replace("''", "'").replace("\\'", "'").replace("\\\\", "\\")

    definitions: dict[int, str] = {}
    batch_files = sorted(SHORT_DEFINITION_DIR.glob("codex_batch_*.sql"))
    if not batch_files:
        raise RuntimeError("가공된 금융용어 SQL 배치를 찾지 못했습니다.")

    correction_file = SHORT_DEFINITION_DIR / "manual_corrections_876_1000.sql"
    ordered_files = batch_files + ([correction_file] if correction_file.exists() else [])
    for path in ordered_files:
        sql = path.read_text(encoding="utf-8-sig")
        for match in case_pattern.finditer(sql):
            term_id = int(match.group(1))
            if term_id <= term_count:
                definitions[term_id] = decode(match.group(2)).strip()
        for match in update_pattern.finditer(sql):
            term_id = int(match.group(2))
            if term_id <= term_count:
                definitions[term_id] = decode(match.group(1)).strip()

    fallback_ids = [term_id for term_id in range(1, term_count + 1) if not definitions.get(term_id)]
    for term_id in fallback_ids:
        definitions[term_id] = source_rows[term_id - 1]["definition"].strip()
    return definitions, fallback_ids


def build_seed_sql(
    source_rows: list[dict[str, str]],
    definitions: dict[int, str],
    fallback_ids: list[int],
) -> str:
    lines = [
        "-- Wallo 금융용어 전체 seed 데이터",
        "-- 생성: ai/scripts/build_financial_term_seed.py",
        f"-- 총 {len(source_rows)}건. 원본 설명과 가공된 short_definition을 한 번에 적재한다.",
        f"-- 가공 누락으로 원본 설명을 사용한 term_id: {fallback_ids or '없음'}",
        "-- database 전체 초기화 SQL 실행 후 이 파일을 한 번 실행한다.",
        "USE wallo;",
        "",
        "START TRANSACTION;",
        "",
    ]

    for offset in range(0, len(source_rows), BATCH_SIZE):
        batch = source_rows[offset : offset + BATCH_SIZE]
        lines.append(
            "INSERT INTO financial_term "
            "(term_id, term_name, description, short_definition, source) VALUES"
        )
        values: list[str] = []
        for index, row in enumerate(batch, start=offset + 1):
            values.append(
                "(" + ", ".join(
                    [
                        str(index),
                        sql_literal(row["term"].strip()),
                        sql_literal(row["definition"].strip()),
                        sql_literal(definitions[index]),
                        sql_literal(row["source"].strip()),
                    ]
                ) + ")"
            )
        lines.append(",\n".join(values))
        lines.extend(
            [
                "ON DUPLICATE KEY UPDATE",
                "    term_name = VALUES(term_name),",
                "    description = VALUES(description),",
                "    short_definition = VALUES(short_definition),",
                "    source = VALUES(source);",
                "",
            ]
        )

    lines.extend(
        [
            "COMMIT;",
            "",
            "-- 기대값: total_count=4149, missing_short_definition=0",
            "SELECT COUNT(*) AS total_count,",
            "       SUM(short_definition IS NULL OR TRIM(short_definition) = '')",
            "           AS missing_short_definition",
            "FROM financial_term;",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    source_rows = load_source_rows()
    definitions, fallback_ids = load_short_definitions(source_rows)
    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_SQL.write_text(
        build_seed_sql(source_rows, definitions, fallback_ids),
        encoding="utf-8",
    )
    print(f"생성 완료: {OUTPUT_SQL}")
    print(f"금융용어: {len(source_rows)}건, 짧은 설명: {len(definitions)}건")
    print(f"원본 설명 대체 term_id: {fallback_ids or '없음'}")


if __name__ == "__main__":
    main()
