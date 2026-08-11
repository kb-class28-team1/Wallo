import argparse
import os
import time
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

import pandas as pd
from bs4 import BeautifulSoup, NavigableString, Tag
from selenium import webdriver
from selenium.common.exceptions import TimeoutException, WebDriverException
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


# =========================================================
# 기본 설정
# =========================================================

BASE_URL = "https://uppity.co.kr/column-before/moneylog/"
TOTAL_PAGES = 24

# 목록 페이지의 게시글 제목 링크
POST_LINK_SELECTOR = ".uc_post_title a"

# 상세 페이지의 게시글 본문
POST_CONTENT_SELECTORS = [
    ".elementor-widget-theme-post-content .elementor-widget-container",
    "[data-widget_type='theme-post-content.default'] .elementor-widget-container",
    ".elementor-widget-container",
]

OUTPUT_FILE = (
    Path(__file__).resolve().parent.parent
    / "data"
    / "raw"
    / "money_log_raw_contents.csv"
)

REQUEST_DELAY_SECONDS = 1
MAX_RETRIES = 3
PAGE_LOAD_TIMEOUT_SECONDS = 30

# 사용자 입력과 전문가 답변을 나누는 경계 문구
EXPERT_BOUNDARY_TEXT = "어피티의 솔루션"

COMPLETED_STATUSES = {
    "success",
    "boundary_not_found",
}

RETRYABLE_STATUSES = {
    "failed",
    "container_not_found",
    "empty_content",
}


# =========================================================
# 명령행 옵션
# =========================================================

def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="어피티 머니로그 원문 수집 크롤러"
    )

    parser.add_argument(
        "--retry-failed",
        action="store_true",
        help="기존 CSV에서 실패한 게시글만 다시 수집합니다.",
    )

    parser.add_argument(
        "--page",
        type=int,
        default=None,
        help="특정 페이지 전체를 강제로 다시 수집합니다. 예: --page 1",
    )

    parser.add_argument(
        "--start-page",
        type=int,
        default=1,
        help="일반 수집을 시작할 페이지입니다.",
    )

    parser.add_argument(
        "--end-page",
        type=int,
        default=TOTAL_PAGES,
        help="일반 수집을 종료할 페이지입니다.",
    )

    return parser.parse_args()


# =========================================================
# Selenium 드라이버
# =========================================================

def create_driver() -> webdriver.Chrome:
    options = webdriver.ChromeOptions()

    # 브라우저 창을 띄우지 않으려면 아래 주석을 해제하세요.
    # options.add_argument("--headless=new")

    options.add_argument("--window-size=1920,1080")
    options.add_argument("--disable-notifications")
    options.add_argument("--disable-popup-blocking")
    options.add_argument("--disable-blink-features=AutomationControlled")

    options.add_argument(
        "--user-agent="
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/142.0.0.0 Safari/537.36"
    )

    driver = webdriver.Chrome(options=options)
    driver.set_page_load_timeout(PAGE_LOAD_TIMEOUT_SECONDS)

    return driver


# =========================================================
# 공통 함수
# =========================================================

def clean_text(value: Any) -> str:
    """
    줄 내부의 불필요한 공백만 정리합니다.
    블록 사이 줄바꿈은 별도 로직에서 유지합니다.
    """

    if value is None:
        return ""

    text = str(value).replace("\xa0", " ")

    if text.lower() == "nan":
        return ""

    return " ".join(text.split()).strip()


def normalize_url(url: Any) -> str:
    cleaned = clean_text(url)

    if not cleaned:
        return ""

    return cleaned.rstrip("/") + "/"


def safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return default


def make_page_url(page_number: int) -> str:
    if page_number == 1:
        return BASE_URL

    return f"{BASE_URL}{page_number}/"


# =========================================================
# 목록 페이지 로딩
# =========================================================

def load_list_page(
        driver: webdriver.Chrome,
        page_number: int,
) -> None:
    page_url = make_page_url(page_number)
    last_error: Optional[Exception] = None

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            driver.get(page_url)

            wait = WebDriverWait(
                driver,
                PAGE_LOAD_TIMEOUT_SECONDS,
            )

            wait.until(
                lambda current_driver: (
                        current_driver.execute_script(
                            "return document.readyState"
                        )
                        == "complete"
                )
            )

            wait.until(
                EC.presence_of_element_located(
                    (By.CSS_SELECTOR, POST_LINK_SELECTOR)
                )
            )

            return

        except (
                TimeoutException,
                WebDriverException,
        ) as error:
            last_error = error

            print(
                f"[목록 재시도 {attempt}/{MAX_RETRIES}] "
                f"{page_number}페이지"
            )
            print(
                f"원인: {type(error).__name__}: {error}"
            )

            if attempt < MAX_RETRIES:
                time.sleep(attempt * 2)

    raise RuntimeError(
        f"{page_number}페이지 목록을 불러오지 못했습니다."
    ) from last_error


def collect_posts_from_page(
        driver: webdriver.Chrome,
        page_number: int,
) -> list[dict[str, Any]]:
    page_url = make_page_url(page_number)

    print()
    print("=" * 80)
    print(f"{page_number}페이지 목록 접속")
    print(page_url)

    load_list_page(
        driver=driver,
        page_number=page_number,
    )

    link_elements = driver.find_elements(
        By.CSS_SELECTOR,
        POST_LINK_SELECTOR,
    )

    posts: list[dict[str, Any]] = []
    seen_urls: set[str] = set()

    for element in link_elements:
        title = clean_text(element.text)
        href = clean_text(element.get_attribute("href"))

        if not href:
            continue

        normalized_href = normalize_url(href)

        if normalized_href in seen_urls:
            continue

        seen_urls.add(normalized_href)

        posts.append({
            "title": title,
            "url": href,
            "position": len(posts) + 1,
        })

    print(f"게시글 {len(posts)}개 확인")

    return posts


# =========================================================
# 상세 페이지 로딩
# =========================================================

def load_detail_html(
        driver: webdriver.Chrome,
        url: str,
) -> str:
    last_error: Optional[Exception] = None

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            driver.get(url)

            wait = WebDriverWait(
                driver,
                PAGE_LOAD_TIMEOUT_SECONDS,
            )

            wait.until(
                EC.presence_of_element_located(
                    (By.TAG_NAME, "body")
                )
            )

            wait.until(
                lambda current_driver: (
                        current_driver.execute_script(
                            "return document.readyState"
                        )
                        == "complete"
                )
            )

            body_text = clean_text(
                driver.find_element(
                    By.TAG_NAME,
                    "body",
                ).text
            )

            if not body_text:
                raise ValueError(
                    "상세 페이지의 본문이 비어 있습니다."
                )

            return driver.page_source

        except (
                TimeoutException,
                WebDriverException,
                ValueError,
        ) as error:
            last_error = error

            print(
                f"[상세 재시도 {attempt}/{MAX_RETRIES}] {url}"
            )
            print(
                f"원인: {type(error).__name__}: {error}"
            )

            if attempt < MAX_RETRIES:
                time.sleep(attempt * 2)

    raise RuntimeError(
        "상세 페이지를 최대 재시도 횟수만큼 "
        "불러오지 못했습니다."
    ) from last_error


# =========================================================
# 게시글 본문 컨테이너 탐색
# =========================================================

def find_post_content_container(
        soup: BeautifulSoup,
) -> Optional[Tag]:
    """
    우선적으로 Elementor의 게시글 본문 위젯을 찾습니다.

    마지막의 단순 .elementor-widget-container는
    다른 위젯도 선택할 수 있으므로 보조 수단으로만 사용합니다.
    """

    for selector in POST_CONTENT_SELECTORS:
        containers = soup.select(selector)

        for container in containers:
            text = clean_text(
                container.get_text(" ", strip=True)
            )

            # 너무 짧은 컨테이너는 메뉴, 버튼 등의
            # 다른 Elementor 위젯일 가능성이 큼
            if len(text) < 200:
                continue

            return container

    return None


# =========================================================
# 게시글 블록 추출
# =========================================================

BLOCK_TAGS = {
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "p",
    "li",
}


def has_block_parent_inside_container(
        tag: Tag,
        container: Tag,
) -> bool:
    """
    p 안의 p 또는 li 안의 p처럼 중첩된 블록이 있을 때
    같은 내용이 중복 수집되지 않도록 확인합니다.
    """

    parent = tag.parent

    while isinstance(parent, Tag):
        if parent is container:
            return False

        if parent.name in BLOCK_TAGS:
            return True

        parent = parent.parent

    return False


def extract_standard_blocks(
        container: Tag,
) -> list[str]:
    """
    h1~h6, p, li를 문서 순서대로 추출합니다.
    """

    blocks: list[str] = []

    for tag in container.find_all(
            list(BLOCK_TAGS),
            recursive=True,
    ):
        if has_block_parent_inside_container(
                tag=tag,
                container=container,
        ):
            continue

        text = clean_text(
            tag.get_text(" ", strip=True)
        )

        if not text:
            continue

        # 바로 이전 블록과 완전히 같은 경우에만 제거
        if blocks and blocks[-1] == text:
            continue

        blocks.append(text)

    return blocks


def extract_leaf_div_blocks(
        container: Tag,
) -> list[str]:
    """
    오래된 게시글처럼 p나 li 없이 div에만 텍스트가 있는 경우
    하위 블록 요소가 없는 div의 텍스트를 보완 수집합니다.
    """

    blocks: list[str] = []

    for tag in container.find_all(
            "div",
            recursive=True,
    ):
        # 내부에 다른 주요 블록이 있으면
        # 상위 div 전체 텍스트는 중복되므로 제외
        if tag.find(
                list(BLOCK_TAGS | {"div"}),
                recursive=False,
        ):
            continue

        text = clean_text(
            tag.get_text(" ", strip=True)
        )

        if not text:
            continue

        if blocks and blocks[-1] == text:
            continue

        blocks.append(text)

    return blocks


def extract_content_blocks(
        container: Tag,
) -> list[str]:
    """
    게시글의 텍스트 블록을 문서 순서대로 반환합니다.

    1차로 제목, 문단, 목록을 추출합니다.
    2차로 div에만 있는 텍스트를 보완합니다.
    """

    standard_blocks = extract_standard_blocks(
        container
    )

    if standard_blocks:
        return standard_blocks

    return extract_leaf_div_blocks(container)


# =========================================================
# 사용자 영역과 전문가 영역 분리
# =========================================================

def split_user_and_expert_content(
        blocks: list[str],
) -> tuple[str, str, str, Optional[int]]:
    """
    '어피티의 솔루션'이 포함된 블록부터
    전문가 답변 영역으로 분류합니다.

    반환:
    - user_content
    - expert_content
    - split_status
    - boundary_block_index
    """

    user_blocks: list[str] = []
    expert_blocks: list[str] = []

    expert_started = False
    boundary_block_index: Optional[int] = None

    for index, block in enumerate(blocks):
        normalized_block = clean_text(block)

        if (
                not expert_started
                and EXPERT_BOUNDARY_TEXT in normalized_block
        ):
            expert_started = True
            boundary_block_index = index

        if expert_started:
            expert_blocks.append(block)
        else:
            user_blocks.append(block)

    user_content = "\n".join(user_blocks).strip()
    expert_content = "\n".join(expert_blocks).strip()

    if expert_started:
        split_status = "success"
    else:
        split_status = "boundary_not_found"

    return (
        user_content,
        expert_content,
        split_status,
        boundary_block_index,
    )


# =========================================================
# 상세 페이지 원문 파싱
# =========================================================

def parse_raw_post_content(
        html: str,
) -> dict[str, Any]:
    soup = BeautifulSoup(
        html,
        "html.parser",
    )

    container = find_post_content_container(soup)

    if container is None:
        return {
            "user_content": "",
            "expert_content": "",
            "full_content": "",
            "split_status": "container_not_found",
            "boundary_block_index": None,
            "block_count": 0,
        }

    blocks = extract_content_blocks(container)

    if not blocks:
        return {
            "user_content": "",
            "expert_content": "",
            "full_content": "",
            "split_status": "empty_content",
            "boundary_block_index": None,
            "block_count": 0,
        }

    (
        user_content,
        expert_content,
        split_status,
        boundary_block_index,
    ) = split_user_and_expert_content(blocks)

    full_content = "\n".join(blocks).strip()

    return {
        "user_content": user_content,
        "expert_content": expert_content,
        "full_content": full_content,
        "split_status": split_status,
        "boundary_block_index": boundary_block_index,
        "block_count": len(blocks),
    }


# =========================================================
# CSV 데이터 관리
# =========================================================

def load_existing_results() -> list[dict[str, Any]]:
    if not os.path.exists(OUTPUT_FILE):
        return []

    dataframe = pd.read_csv(
        OUTPUT_FILE,
        encoding="utf-8-sig",
    )

    dataframe = dataframe.where(
        pd.notna(dataframe),
        None,
    )

    records = dataframe.to_dict(
        orient="records"
    )

    print(
        f"기존 수집 데이터 {len(records)}개를 "
        "불러왔습니다."
    )

    return records


def atomic_save_dataframe(
        dataframe: pd.DataFrame,
        output_file: str,
) -> None:
    """
    임시 파일에 먼저 저장한 후 기존 파일을 교체합니다.
    저장 도중 종료되어 CSV가 깨질 가능성을 줄입니다.
    """

    temp_file = f"{output_file}.tmp"

    dataframe.to_csv(
        temp_file,
        index=False,
        encoding="utf-8-sig",
    )

    os.replace(
        temp_file,
        output_file,
    )


def save_results(
        results: list[dict[str, Any]],
) -> None:
    dataframe = pd.DataFrame(results)

    if not dataframe.empty:
        dataframe = dataframe.sort_values(
            by=[
                "source_page",
                "post_position",
                "profile_id",
            ],
            kind="stable",
        )

    atomic_save_dataframe(
        dataframe=dataframe,
        output_file=OUTPUT_FILE,
    )


def get_next_profile_id(
        results: list[dict[str, Any]],
) -> int:
    if not results:
        return 1

    return (
            max(
                safe_int(row.get("profile_id"))
                for row in results
            )
            + 1
    )


def find_existing_result(
        results: list[dict[str, Any]],
        source_url: str,
        source_page: int,
        post_position: int,
) -> Optional[dict[str, Any]]:
    normalized_source_url = normalize_url(
        source_url
    )

    # URL 우선 비교
    for row in results:
        existing_url = normalize_url(
            row.get("source_url")
        )

        if (
                existing_url
                and existing_url == normalized_source_url
        ):
            return row

    # URL을 못 찾으면 페이지 번호와 게시글 순서로 비교
    for row in results:
        if (
                safe_int(row.get("source_page"))
                == source_page
                and safe_int(row.get("post_position"))
                == post_position
        ):
            return row

    return None


def replace_result(
        results: list[dict[str, Any]],
        new_result: dict[str, Any],
) -> None:
    profile_id = safe_int(
        new_result.get("profile_id")
    )

    results[:] = [
        row
        for row in results
        if safe_int(row.get("profile_id"))
           != profile_id
    ]

    results.append(new_result)


# =========================================================
# 결과 행 생성
# =========================================================

def create_base_result(
        profile_id: int,
        source_page: int,
        post_position: int,
        post_title: str,
        source_url: str,
) -> dict[str, Any]:
    return {
        "profile_id": profile_id,
        "source_page": source_page,
        "post_position": post_position,
        "post_title": post_title,
        "source_url": source_url,

        "user_content": "",
        "expert_content": "",
        "full_content": "",

        "split_status": None,
        "boundary_block_index": None,
        "block_count": 0,

        "crawl_status": None,
        "error_type": None,
        "error_message": None,

        "last_crawled_at": datetime.now().isoformat(
            timespec="seconds"
        ),
    }


def create_failed_result(
        profile_id: int,
        source_page: int,
        post_position: int,
        post_title: str,
        source_url: str,
        error: Exception,
) -> dict[str, Any]:
    result = create_base_result(
        profile_id=profile_id,
        source_page=source_page,
        post_position=post_position,
        post_title=post_title,
        source_url=source_url,
    )

    result["crawl_status"] = "failed"
    result["error_type"] = type(error).__name__
    result["error_message"] = clean_text(str(error))

    return result


# =========================================================
# 게시글 하나 수집
# =========================================================

def crawl_single_post(
        driver: webdriver.Chrome,
        results: list[dict[str, Any]],
        post: dict[str, Any],
        page_number: int,
        next_profile_id: int,
        force_recrawl: bool,
) -> tuple[int, str]:
    post_position = safe_int(
        post.get("position")
    )

    post_title = clean_text(
        post.get("title")
    )

    post_url = clean_text(
        post.get("url")
    )

    existing_result = find_existing_result(
        results=results,
        source_url=post_url,
        source_page=page_number,
        post_position=post_position,
    )

    if existing_result is not None:
        profile_id = safe_int(
            existing_result.get("profile_id")
        )

        existing_status = clean_text(
            existing_result.get("crawl_status")
        )

        if (
                not force_recrawl
                and existing_status in COMPLETED_STATUSES
        ):
            print(
                f"[건너뜀] {page_number}페이지 "
                f"{post_position}번 / "
                f"상태={existing_status}"
            )

            return next_profile_id, "skipped"

    else:
        profile_id = next_profile_id
        next_profile_id += 1

    print()
    print("-" * 80)
    print(
        f"[수집 시작] {page_number}페이지 "
        f"{post_position}번"
    )
    print(f"profile_id: {profile_id}")
    print(f"제목: {post_title}")
    print(f"URL: {post_url}")

    try:
        html = load_detail_html(
            driver=driver,
            url=post_url,
        )

        parsed = parse_raw_post_content(html)

        result = create_base_result(
            profile_id=profile_id,
            source_page=page_number,
            post_position=post_position,
            post_title=post_title,
            source_url=post_url,
        )

        result.update(parsed)

        split_status = clean_text(
            parsed.get("split_status")
        )

        if split_status == "container_not_found":
            result["crawl_status"] = "container_not_found"
            result["error_type"] = "ContainerNotFound"
            result["error_message"] = (
                "게시글 본문 elementor-widget-container를 "
                "찾지 못했습니다."
            )

        elif split_status == "empty_content":
            result["crawl_status"] = "empty_content"
            result["error_type"] = "EmptyContent"
            result["error_message"] = (
                "게시글 본문 컨테이너는 찾았지만 "
                "텍스트를 추출하지 못했습니다."
            )

        elif split_status == "boundary_not_found":
            # 전문가 경계를 못 찾았지만 원문 수집은 성공
            result["crawl_status"] = "boundary_not_found"
            result["error_type"] = None
            result["error_message"] = None

        else:
            result["crawl_status"] = "success"
            result["error_type"] = None
            result["error_message"] = None

        replace_result(
            results=results,
            new_result=result,
        )

        # 게시글 한 건마다 즉시 저장
        save_results(results)

        print(
            f"[수집 완료] "
            f"crawl_status={result['crawl_status']}, "
            f"split_status={result['split_status']}"
        )
        print(
            f"전체 블록={result['block_count']}개, "
            f"사용자 영역={len(result['user_content'])}자, "
            f"전문가 영역={len(result['expert_content'])}자"
        )

        return (
            next_profile_id,
            clean_text(result["crawl_status"]),
        )

    except Exception as error:
        failed_result = create_failed_result(
            profile_id=profile_id,
            source_page=page_number,
            post_position=post_position,
            post_title=post_title,
            source_url=post_url,
            error=error,
        )

        replace_result(
            results=results,
            new_result=failed_result,
        )

        save_results(results)

        print(
            f"[수집 실패] {page_number}페이지 "
            f"{post_position}번"
        )
        print(
            f"{type(error).__name__}: {error}"
        )

        return next_profile_id, "failed"


# =========================================================
# 실패 위치 조회
# =========================================================

def get_failed_targets(
        results: list[dict[str, Any]],
) -> dict[int, set[int]]:
    """
    반환 예시:
    {
        2: {3, 8},
        5: {1}
    }
    """

    targets: dict[int, set[int]] = {}

    for row in results:
        crawl_status = clean_text(
            row.get("crawl_status")
        )

        if crawl_status not in RETRYABLE_STATUSES:
            continue

        source_page = safe_int(
            row.get("source_page")
        )

        post_position = safe_int(
            row.get("post_position")
        )

        if source_page < 1 or post_position < 1:
            continue

        targets.setdefault(
            source_page,
            set(),
        ).add(post_position)

    return targets


# =========================================================
# 페이지 단위 수집
# =========================================================

def crawl_pages(
        driver: webdriver.Chrome,
        results: list[dict[str, Any]],
        page_numbers: list[int],
        target_positions: Optional[
            dict[int, set[int]]
        ] = None,
        force_page_recrawl: bool = False,
) -> None:
    next_profile_id = get_next_profile_id(
        results
    )

    for page_number in page_numbers:
        try:
            posts = collect_posts_from_page(
                driver=driver,
                page_number=page_number,
            )

        except Exception as error:
            print(
                f"[목록 페이지 실패] {page_number}페이지"
            )
            print(
                f"{type(error).__name__}: {error}"
            )
            continue

        positions_for_page: Optional[set[int]] = None

        if target_positions is not None:
            positions_for_page = target_positions.get(
                page_number,
                set(),
            )

        processed_count = 0
        success_count = 0
        failed_count = 0
        skipped_count = 0

        for post in posts:
            post_position = safe_int(
                post.get("position")
            )

            # 실패 게시글 재수집 모드에서는
            # 지정된 게시글만 처리
            if (
                    positions_for_page is not None
                    and post_position not in positions_for_page
            ):
                continue

            force_recrawl = (
                    force_page_recrawl
                    or positions_for_page is not None
            )

            next_profile_id, status = crawl_single_post(
                driver=driver,
                results=results,
                post=post,
                page_number=page_number,
                next_profile_id=next_profile_id,
                force_recrawl=force_recrawl,
            )

            processed_count += 1

            if status in COMPLETED_STATUSES:
                success_count += 1

            elif status in RETRYABLE_STATUSES:
                failed_count += 1

            elif status == "skipped":
                skipped_count += 1

            time.sleep(REQUEST_DELAY_SECONDS)

        save_results(results)

        print()
        print(f"{page_number}페이지 처리 완료")
        print(
            f"처리={processed_count}, "
            f"완료={success_count}, "
            f"실패={failed_count}, "
            f"건너뜀={skipped_count}"
        )

        time.sleep(REQUEST_DELAY_SECONDS)


# =========================================================
# 실패 게시글만 재수집
# =========================================================

def retry_failed_posts(
        driver: webdriver.Chrome,
        results: list[dict[str, Any]],
) -> None:
    failed_targets = get_failed_targets(
        results
    )

    if not failed_targets:
        print("재수집할 실패 게시글이 없습니다.")
        return

    print()
    print("=" * 80)
    print("실패 게시글 재수집 대상")

    for page_number in sorted(failed_targets):
        positions = sorted(
            failed_targets[page_number]
        )

        print(
            f"- {page_number}페이지: {positions}"
        )

    crawl_pages(
        driver=driver,
        results=results,
        page_numbers=sorted(failed_targets),
        target_positions=failed_targets,
        force_page_recrawl=False,
    )


# =========================================================
# 메인 실행
# =========================================================

def main() -> None:
    args = parse_arguments()

    results = load_existing_results()

    driver: Optional[webdriver.Chrome] = None

    try:
        driver = create_driver()

        # 실패한 게시글만 재수집
        if args.retry_failed:
            retry_failed_posts(
                driver=driver,
                results=results,
            )

        # 특정 페이지 전체 강제 재수집
        elif args.page is not None:
            if not 1 <= args.page <= TOTAL_PAGES:
                raise ValueError(
                    f"--page는 1부터 "
                    f"{TOTAL_PAGES} 사이여야 합니다."
                )

            print(
                f"{args.page}페이지 전체를 "
                "강제로 다시 수집합니다."
            )

            crawl_pages(
                driver=driver,
                results=results,
                page_numbers=[args.page],
                target_positions=None,
                force_page_recrawl=True,
            )

        # 일반 전체 또는 범위 수집
        else:
            start_page = max(
                1,
                args.start_page,
            )

            end_page = min(
                TOTAL_PAGES,
                args.end_page,
            )

            if start_page > end_page:
                raise ValueError(
                    "시작 페이지가 종료 페이지보다 "
                    "클 수 없습니다."
                )

            crawl_pages(
                driver=driver,
                results=results,
                page_numbers=list(
                    range(
                        start_page,
                        end_page + 1,
                        )
                ),
                target_positions=None,
                force_page_recrawl=False,
            )

    except KeyboardInterrupt:
        print()
        print(
            "[사용자 중단] 현재까지 수집한 데이터를 "
            "저장합니다."
        )

    except Exception as error:
        print()
        print(
            f"[프로그램 오류] "
            f"{type(error).__name__}: {error}"
        )

    finally:
        try:
            save_results(results)

            print()
            print(f"최종 CSV 저장 완료: {OUTPUT_FILE}")

        except Exception as save_error:
            print(
                f"[CSV 저장 실패] "
                f"{type(save_error).__name__}: "
                f"{save_error}"
            )

        if driver is not None:
            try:
                driver.quit()
            except Exception:
                pass

        print()
        print("=" * 80)
        print("크롤링 프로그램 종료")
        print(f"저장된 게시글 수: {len(results)}")


if __name__ == "__main__":
    main()
