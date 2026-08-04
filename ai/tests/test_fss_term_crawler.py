"""crawl_fss_terms.py 단위 테스트. 실제 사이트를 호출하지 않고 fixture HTML로 검증한다."""

from bs4 import BeautifulSoup

from scripts.crawl_fss_terms import (
    normalize_whitespace,
    parse_last_page,
    parse_terms_from_page,
)


def _soup(html_fragment: str) -> BeautifulSoup:
    return BeautifulSoup(html_fragment, "html.parser")


def _result_list_html(dl_fragments: str) -> str:
    return f'<div class="bd-list result-list">{dl_fragments}</div>'


# 1. 일반 항목 (숫자, 용어, 영문 용어, 정의가 모두 있는 경우)
def test_parses_normal_term_with_english_term():
    html_fragment = _result_list_html(
        """
        <dl>
            <dt>
                538.
                휴면예금
                    [Dormant Deposit]
                </dt>
            <dd>은행 및 우체국의 요구불 예금 중 소멸시효가 완성된 이후 찾아가지 않은 예금이다.
</dd>
        </dl>
        """
    )
    terms = parse_terms_from_page(_soup(html_fragment))

    assert len(terms) == 1
    term = terms[0]
    assert term.source_id == 538
    assert term.term == "휴면예금"
    assert term.english_term == "Dormant Deposit"
    assert term.definition == "은행 및 우체국의 요구불 예금 중 소멸시효가 완성된 이후 찾아가지 않은 예금이다."
    assert term.source == "금융감독원"


# 2. 영문 용어가 없는 항목
def test_parses_term_without_english_term():
    html_fragment = _result_list_html(
        """
        <dl>
            <dt>
                533.
                회계관련 부정행위 신고 및 포상제도</dt>
            <dd>회사의 회계정보와 관련한 부정행위를 신고하고 포상하는 제도를 말한다.</dd>
        </dl>
        """
    )
    terms = parse_terms_from_page(_soup(html_fragment))

    assert terms[0].source_id == 533
    assert terms[0].term == "회계관련 부정행위 신고 및 포상제도"
    assert terms[0].english_term == ""


# 3. term에 줄바꿈이 포함된 경우
def test_normalizes_newline_inside_term():
    html_fragment = _result_list_html(
        """
        <dl>
            <dt>
                532.
                환매청구권
(풋백옵션)
                    [Put-Back Option]
                </dt>
            <dd>기업공개시 일반청약자가 공모주식을 인수회사에 매도할 수 있는 권리를 말한다.</dd>
        </dl>
        """
    )
    terms = parse_terms_from_page(_soup(html_fragment))

    assert terms[0].term == "환매청구권 (풋백옵션)"
    assert terms[0].english_term == "Put-Back Option"


# 4. english_term에 줄바꿈이 포함된 경우
def test_normalizes_newline_inside_english_term():
    html_fragment = _result_list_html(
        """
        <dl>
            <dt>
                535.
                회생&정리계획
                    [RRP :
Recovery & Resolution Plan]
                </dt>
            <dd>회생계획과 정리계획을 의미한다.</dd>
        </dl>
        """
    )
    terms = parse_terms_from_page(_soup(html_fragment))

    assert terms[0].term == "회생&정리계획"
    assert terms[0].english_term == "RRP : Recovery & Resolution Plan"


# 5. HTML entity 복원
def test_restores_html_entities_in_definition():
    html_fragment = _result_list_html(
        """
        <dl>
            <dt>
                999.
                테스트용어
                    [Test Term]
                </dt>
            <dd>A&amp;B 상품은 &quot;안전자산&quot;에 해당한다.</dd>
        </dl>
        """
    )
    terms = parse_terms_from_page(_soup(html_fragment))

    assert terms[0].definition == 'A&B 상품은 "안전자산"에 해당한다.'


# 6. definition 공백/줄바꿈 정규화
def test_normalizes_whitespace_in_definition():
    html_fragment = _result_list_html(
        """
        <dl>
            <dt>
                998.
                테스트용어2
                    [Test Term Two]
                </dt>
            <dd>   여러 줄에
    걸쳐   작성된    정의입니다.

</dd>
        </dl>
        """
    )
    terms = parse_terms_from_page(_soup(html_fragment))

    assert terms[0].definition == "여러 줄에 걸쳐 작성된 정의입니다."


def test_normalize_whitespace_collapses_all_whitespace_runs():
    assert normalize_whitespace("  환매청구권\n(풋백옵션)  \n\t") == "환매청구권 (풋백옵션)"


# 7. 마지막 페이지 번호 자동 추출 (하드코딩 금지 검증)
def test_parse_last_page_reads_data_pageindex_from_end_link():
    pagination_html = """
        <div class="pagination-set">
            <ul class="pagination pagination-centered">
                <li class="i first disabled"><a title="처음 목록" href="javascript:fnSearch(1)" data-pageindex="1">처음</a></li>
                <li class="active"><span><em title="현재목록"><span>1</span></em></span></li>
                <li><a href="javascript:fnSearch(2)" data-pageindex="2">2</a></li>
                <li class="i next"><a title="다음 목록" href="javascript:fnSearch(11)" data-pageindex="11">다음</a></li>
                <li class="i end"><a title="마지막 목록" href="javascript:fnSearch(54)" data-pageindex="54">끝</a></li>
            </ul>
        </div>
    """
    assert parse_last_page(_soup(pagination_html)) == 54


def test_parse_last_page_falls_back_to_max_pageindex_when_end_link_missing():
    pagination_html = """
        <div class="pagination-set">
            <ul class="pagination pagination-centered">
                <li class="active"><span><em title="현재목록"><span>1</span></em></span></li>
                <li><a href="javascript:fnSearch(2)" data-pageindex="2">2</a></li>
                <li><a href="javascript:fnSearch(3)" data-pageindex="3">3</a></li>
            </ul>
        </div>
    """
    assert parse_last_page(_soup(pagination_html)) == 3


def test_parse_last_page_defaults_to_one_when_no_pagination_present():
    assert parse_last_page(_soup("<div></div>")) == 1


# 8. 빈 목록
def test_parses_empty_result_list_container():
    html_fragment = _result_list_html("")
    terms = parse_terms_from_page(_soup(html_fragment))

    assert terms == []


def test_parses_page_without_result_list_container():
    terms = parse_terms_from_page(_soup("<div>내용 없음</div>"))

    assert terms == []


# 9. 중복 source_id
def test_parses_duplicate_source_id_entries_without_deduplication():
    html_fragment = _result_list_html(
        """
        <dl>
            <dt>
                100.
                용어A
                    [Term A]
                </dt>
            <dd>정의 A</dd>
        </dl>
        <dl>
            <dt>
                100.
                용어B
                    [Term B]
                </dt>
            <dd>정의 B</dd>
        </dl>
        """
    )
    terms = parse_terms_from_page(_soup(html_fragment))

    assert len(terms) == 2
    assert terms[0].source_id == 100
    assert terms[1].source_id == 100
    assert terms[0].term != terms[1].term
