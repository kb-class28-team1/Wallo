"""financial_term.description을 규칙에 맞게 압축해 short_definition에 저장한다."""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import time
from pathlib import Path

import requests


ROOT = Path(__file__).resolve().parents[2]
PROPERTIES_PATH = ROOT / "backend/src/main/resources/application.properties"
OUTPUT_DIR = ROOT / "ai/data/processed/short_definitions"
MODEL = os.getenv("GROQ_MODEL", "openai/gpt-oss-20b")
SYSTEM_PROMPT = """당신은 금융용어 사전 편집자다. 입력된 원본 설명만 압축·재구성한다.
규칙:
1. 금융 초보자가 이해하기 쉬운 한국어로 쓴다.
2. 정확히 2문장이고 전체 길이는 공백 포함 40~140자다.
3. 첫 문장은 입력의 정확한 용어명 뒤에 조사 '은' 또는 '는'을 붙여 시작한다. 따옴표는 쓰지 않는다.
4. 두 문장 모두 해당 금융용어의 정의와 개념만 설명한다.
5. 원본에 명시된 정보만 사용하고 예시, 해석, 효과, 주의사항, 행동 권유를 추가하지 않는다.
6. 원본의 의미, 수치, 조건, 적용 범위를 왜곡하지 않는다.
7. 규칙을 만족할 수 없으면 definition을 null로 반환한다.
반드시 {"items":[{"term_id":정수,"definition":문자열 또는 null}]} 형태의 JSON만 반환한다."""


def parse_properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for line in PROPERTIES_PATH.read_text(encoding="utf-8").splitlines():
        if line and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def mysql_command(sql: str) -> str:
    config = parse_properties()
    env = os.environ.copy()
    env["MYSQL_PWD"] = config["db.password"]
    process = subprocess.run(
        [
            "mysql",
            "--default-character-set=utf8mb4",
            "--batch",
            "--raw",
            "--skip-column-names",
            "-u",
            config["db.username"],
            "-D",
            "wallo",
            "-e",
            sql,
        ],
        cwd=ROOT,
        env=env,
        check=True,
        capture_output=True,
    )
    return process.stdout.decode("utf-8")


def load_terms(offset: int, limit: int) -> list[dict]:
    sql = (
        "SELECT JSON_OBJECT('term_id', term_id, 'term_name', term_name, "
        "'description', description) FROM financial_term "
        f"ORDER BY term_id LIMIT {int(limit)} OFFSET {int(offset)};"
    )
    return [json.loads(line) for line in mysql_command(sql).splitlines() if line]


def call_json(api_key: str, system: str, payload: object) -> dict:
    response = requests.post(
        "https://api.groq.com/openai/v1/chat/completions",
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        json={
            "model": MODEL,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
            ],
        },
        timeout=120,
    )
    response.raise_for_status()
    return json.loads(response.json()["choices"][0]["message"]["content"])


def local_error(term: dict, definition: object) -> str | None:
    if not isinstance(definition, str):
        return "모델이 가공 실패로 반환"
    if not 40 <= len(definition) <= 140:
        return f"길이 위반({len(definition)}자)"
    name = term["term_name"]
    if not (definition.startswith(name + "은 ") or definition.startswith(name + "는 ")):
        return "용어명은/는 시작 규칙 위반"
    sentences = re.split(r"(?<=\.)\s+", definition.strip())
    if len(sentences) != 2 or not all(sentence.endswith(".") for sentence in sentences):
        return "2문장 규칙 위반"
    return None


REVIEW_PROMPT = """금융용어 요약 검수자다. 각 항목의 원본과 가공문을 대조한다.
가공문이 정확히 2문장이고 정의·개념만 담았는지, 원본에 없는 정보·예시·해석·효과·주의·권유가 없는지,
원문의 의미·수치·조건·범위를 왜곡하지 않았는지 엄격히 판정한다.
반드시 {"items":[{"term_id":정수,"valid":참/거짓,"reason":"짧은 사유"}]} JSON만 반환한다."""


def generate_batch(api_key: str, terms: list[dict]) -> tuple[dict[int, str], dict[int, str]]:
    pending = {term["term_id"]: term for term in terms}
    accepted: dict[int, str] = {}
    failures: dict[int, str] = {}
    feedback: dict[int, str] = {}

    for attempt in range(3):
        if not pending:
            break
        payload = []
        for term in pending.values():
            item = dict(term)
            if term["term_id"] in feedback:
                item["previous_error"] = feedback[term["term_id"]]
            payload.append(item)
        try:
            generated = call_json(api_key, SYSTEM_PROMPT, {"terms": payload}).get("items", [])
        except Exception as error:
            if attempt == 2:
                for term_id in pending:
                    failures[term_id] = f"생성 호출 실패: {error}"
            else:
                time.sleep(2 ** attempt)
            continue

        by_id = {item.get("term_id"): item.get("definition") for item in generated}
        review_candidates = []
        for term_id, term in pending.items():
            definition = by_id.get(term_id)
            error = local_error(term, definition)
            if error:
                feedback[term_id] = error
            else:
                review_candidates.append({**term, "definition": definition})

        if review_candidates:
            try:
                reviewed = call_json(api_key, REVIEW_PROMPT, {"terms": review_candidates}).get("items", [])
                reviews = {item.get("term_id"): item for item in reviewed}
            except Exception as error:
                reviews = {item["term_id"]: {"valid": False, "reason": f"검수 호출 실패: {error}"} for item in review_candidates}
            for item in review_candidates:
                term_id = item["term_id"]
                review = reviews.get(term_id, {})
                if review.get("valid") is True:
                    accepted[term_id] = item["definition"]
                else:
                    feedback[term_id] = str(review.get("reason", "검수 결과 누락"))

        pending = {term_id: term for term_id, term in pending.items() if term_id not in accepted}
        if pending and attempt < 2:
            time.sleep(0.5)

    for term_id in pending:
        failures[term_id] = feedback.get(term_id, "3회 시도 후 실패")
    return accepted, failures


def sql_quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def save_results(terms: list[dict], accepted: dict[int, str]) -> None:
    ids = ",".join(str(term["term_id"]) for term in terms)
    clauses = "\n".join(
        f"WHEN {term_id} THEN {sql_quote(definition)}" for term_id, definition in sorted(accepted.items())
    )
    assignment = f"CASE term_id\n{clauses}\nELSE NULL END" if clauses else "NULL"
    sql = (
        "START TRANSACTION;\n"
        f"UPDATE financial_term SET short_definition = {assignment} WHERE term_id IN ({ids});\n"
        "COMMIT;"
    )
    mysql_command(sql)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--limit", type=int, default=1000)
    parser.add_argument("--batch-size", type=int, default=5)
    args = parser.parse_args()

    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        raise RuntimeError("GROQ_API_KEY가 설정되지 않았습니다.")
    terms = load_terms(args.offset, args.limit)
    accepted: dict[int, str] = {}
    failures: dict[int, str] = {}

    for start in range(0, len(terms), args.batch_size):
        batch = terms[start : start + args.batch_size]
        batch_accepted, batch_failures = generate_batch(api_key, batch)
        accepted.update(batch_accepted)
        failures.update(batch_failures)
        print(f"progress={min(start + len(batch), len(terms))}/{len(terms)} accepted={len(accepted)} failed={len(failures)}", flush=True)

    save_results(terms, accepted)
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    report_path = OUTPUT_DIR / f"batch_offset_{args.offset}_limit_{args.limit}.json"
    report_path.write_text(
        json.dumps(
            {
                "offset": args.offset,
                "limit": args.limit,
                "processed": len(terms),
                "saved": len(accepted),
                "failed": len(failures),
                "failures": failures,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"completed saved={len(accepted)} failed={len(failures)} report={report_path}")


if __name__ == "__main__":
    main()
