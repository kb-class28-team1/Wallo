"""금융감독원(FSS) 파인 금융용어사전 크롤러.

대상: https://fine.fss.or.kr/fine/fnctip/fncDicary/list.do?menuNo=900021

실제 사이트 분석 결과:
- 페이지 이동은 `javascript:fnSearch(pageIndex)`가 호출되며, 실제 구현은
  `$('#pageIndex').val(pageIndex); $('#frm').submit();` 이다.
  `#frm`은 GET 방식이며 파라미터는 menuNo, pageIndex, src, kind, searchCnd, searchStr 이다.
- 목록은 `<div class="bd-list result-list">` 안에 `<dl><dt>...</dt><dd>...</dd></dl>`이 반복되는 구조이다.
- 마지막 페이지 번호는 `title="마지막 목록"` 링크의 `data-pageindex` 속성에서 읽는다(하드코딩 금지).
"""

from __future__ import annotations

import csv
import html
import json
import random
import re
import time
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Optional

import requests
from bs4 import BeautifulSoup

BASE_URL = "https://fine.fss.or.kr"
LIST_PATH = "/fine/fnctip/fncDicary/list.do"
LIST_URL = f"{BASE_URL}{LIST_PATH}"
SOURCE_NAME = "금융감독원"

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
REQUEST_HEADERS = {"User-Agent": USER_AGENT}
REQUEST_TIMEOUT_SECONDS = 10
MAX_RETRIES = 3
RETRY_SLEEP_SECONDS = 1.0
REQUEST_SLEEP_RANGE = (0.3, 1.0)

DATA_DIR = Path(__file__).resolve().parent.parent / "data" / "processed"
CSV_PATH = DATA_DIR / "fss_terms.csv"
JSON_PATH = DATA_DIR / "fss_terms.json"

CSV_FIELDNAMES = ["source_id", "term", "english_term", "definition", "source", "source_url"]

DT_ID_PATTERN = re.compile(r"^\s*(\d+)\.\s*(.*)$", re.DOTALL)
DT_ENGLISH_PATTERN = re.compile(r"^(.*?)\[(.*)\]\s*$", re.DOTALL)


@dataclass
class FssTerm:
    source_id: Optional[int]
    term: str
    english_term: str
    definition: str
    source: str = SOURCE_NAME
    source_url: str = LIST_URL


def normalize_whitespace(text: str) -> str:
    """줄바꿈과 연속 공백(개행 포함)을 단일 공백으로 정규화한다."""
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def build_list_params(page_index: int) -> dict:
    return {
        "menuNo": "900021",
        "pageIndex": page_index,
        "src": "",
        "kind": "",
        "searchCnd": "1",
        "searchStr": "",
    }


def fetch_page(session: requests.Session, page_index: int) -> str:
    """페이지 HTML을 가져온다. 네트워크 오류 시 최대 MAX_RETRIES회 재시도한다."""
    last_error: Optional[Exception] = None
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            response = session.get(
                LIST_URL,
                params=build_list_params(page_index),
                headers=REQUEST_HEADERS,
                timeout=REQUEST_TIMEOUT_SECONDS,
            )
            response.raise_for_status()
            response.encoding = "utf-8"
            return response.text
        except requests.RequestException as error:
            last_error = error
            if attempt < MAX_RETRIES:
                time.sleep(RETRY_SLEEP_SECONDS)
    assert last_error is not None
    raise last_error


def parse_last_page(soup: BeautifulSoup) -> int:
    """`title="마지막 목록"` 링크의 data-pageindex에서 마지막 페이지 번호를 읽는다."""
    end_link = soup.select_one('a[title="마지막 목록"]')
    if end_link is not None and end_link.get("data-pageindex", "").isdigit():
        return int(end_link["data-pageindex"])

    page_indexes = [
        int(tag["data-pageindex"])
        for tag in soup.select("[data-pageindex]")
        if tag.get("data-pageindex", "").isdigit()
    ]
    return max(page_indexes) if page_indexes else 1


def parse_dt(dt_text: str) -> tuple[Optional[int], str, str]:
    """dt 텍스트를 (source_id, term, english_term)으로 분리한다.

    예) "538.\\n휴면예금\\n[Dormant Deposit]" -> (538, "휴면예금", "Dormant Deposit")
    """
    id_match = DT_ID_PATTERN.match(dt_text)
    if id_match is None:
        return None, normalize_whitespace(dt_text), ""

    source_id = int(id_match.group(1))
    remainder = id_match.group(2)

    english_match = DT_ENGLISH_PATTERN.match(remainder)
    if english_match is None:
        return source_id, normalize_whitespace(remainder), ""

    term = normalize_whitespace(english_match.group(1))
    english_term = normalize_whitespace(english_match.group(2))
    return source_id, term, english_term


def parse_terms_from_page(soup: BeautifulSoup) -> list[FssTerm]:
    container = soup.select_one("div.bd-list.result-list")
    if container is None:
        return []

    terms: list[FssTerm] = []
    for dl in container.find_all("dl"):
        dt = dl.find("dt")
        dd = dl.find("dd")
        if dt is None or dd is None:
            continue

        source_id, term, english_term = parse_dt(dt.get_text())
        definition = normalize_whitespace(dd.get_text())

        terms.append(
            FssTerm(
                source_id=source_id,
                term=term,
                english_term=english_term,
                definition=definition,
            )
        )
    return terms


def crawl_all_terms(session: Optional[requests.Session] = None) -> tuple[list[FssTerm], list[int]]:
    """전체 페이지를 순회하며 모든 용어를 수집한다.

    반환값: (전체 용어 목록, 페이지별 수집 개수 리스트)
    """
    owns_session = session is None
    session = session or requests.Session()
    try:
        first_page_html = fetch_page(session, 1)
        first_soup = BeautifulSoup(first_page_html, "html.parser")
        last_page = parse_last_page(first_soup)

        all_terms: list[FssTerm] = []
        per_page_counts: list[int] = []

        first_page_terms = parse_terms_from_page(first_soup)
        all_terms.extend(first_page_terms)
        per_page_counts.append(len(first_page_terms))

        for page_index in range(2, last_page + 1):
            time.sleep(random.uniform(*REQUEST_SLEEP_RANGE))
            page_html = fetch_page(session, page_index)
            page_soup = BeautifulSoup(page_html, "html.parser")
            page_terms = parse_terms_from_page(page_soup)
            all_terms.extend(page_terms)
            per_page_counts.append(len(page_terms))

        return all_terms, per_page_counts
    finally:
        if owns_session:
            session.close()


def compute_stats(terms: list[FssTerm], per_page_counts: list[int]) -> dict:
    ids = [term.source_id for term in terms if term.source_id is not None]
    id_counts = Counter(ids)
    duplicate_count = sum(count - 1 for count in id_counts.values() if count > 1)

    return {
        "total_pages": len(per_page_counts),
        "per_page_counts": per_page_counts,
        "total_terms": len(terms),
        "source_id_min": min(ids) if ids else None,
        "source_id_max": max(ids) if ids else None,
        "missing_source_id_count": sum(1 for term in terms if term.source_id is None),
        "duplicate_source_id_count": duplicate_count,
        "empty_term_count": sum(1 for term in terms if not term.term),
        "empty_definition_count": sum(1 for term in terms if not term.definition),
        "no_english_term_count": sum(1 for term in terms if not term.english_term),
    }


def print_report(stats: dict) -> None:
    print("===== FSS 금융용어사전 크롤링 결과 =====")
    print(f"총 페이지 수: {stats['total_pages']}")
    print(f"페이지별 수집 개수: {stats['per_page_counts']}")
    print(f"전체 수집 개수: {stats['total_terms']}")
    print(f"source_id 최소값: {stats['source_id_min']}")
    print(f"source_id 최대값: {stats['source_id_max']}")
    print(f"source_id 누락 개수: {stats['missing_source_id_count']}")
    print(f"source_id 중복 개수: {stats['duplicate_source_id_count']}")
    print(f"빈 용어 개수: {stats['empty_term_count']}")
    print(f"빈 정의 개수: {stats['empty_definition_count']}")
    print(f"영문 용어 없는 개수: {stats['no_english_term_count']}")


def save_csv(terms: list[FssTerm], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDNAMES)
        writer.writeheader()
        for term in terms:
            writer.writerow(asdict(term))


def save_json(terms: list[FssTerm], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        json.dump([asdict(term) for term in terms], f, ensure_ascii=False, indent=2)


def main() -> None:
    terms, per_page_counts = crawl_all_terms()
    save_csv(terms, CSV_PATH)
    save_json(terms, JSON_PATH)
    stats = compute_stats(terms, per_page_counts)
    print_report(stats)
    print(f"CSV 저장 경로: {CSV_PATH}")
    print(f"JSON 저장 경로: {JSON_PATH}")


if __name__ == "__main__":
    main()
