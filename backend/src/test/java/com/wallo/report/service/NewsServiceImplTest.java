package com.wallo.report.service;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.report.domain.News;
import com.wallo.report.domain.NewsReport;
import com.wallo.report.domain.NewsReportListItem;
import com.wallo.report.dto.response.MatchedTermResponse;
import com.wallo.report.dto.response.ReportDetailResponse;
import com.wallo.report.dto.response.ReportListResponse;
import com.wallo.report.mapper.NewsMapper;
import com.wallo.report.mapper.NewsReportMapper;
import com.wallo.report.term.domain.FinancialTerm;
import com.wallo.report.term.mapper.NewsTermMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsServiceImplTest {

    @Test
    void getNewsByIdOrThrowReturnsNewsWhenFound() {
        FakeNewsMapper mapper = new FakeNewsMapper();
        News news = News.builder()
                .newsId(1L)
                .title("제목")
                .content("본문")
                .source("매일경제")
                .url("https://example.com/1")
                .category("경제")
                .publishedAt(LocalDateTime.of(2026, 7, 30, 12, 0))
                .build();
        mapper.newsById.put(1L, news);
        NewsService service = new NewsServiceImpl(mapper, new FakeNewsReportMapper(), new FakeNewsTermMapper());

        News result = service.getNewsByIdOrThrow(1L);

        assertEquals(1L, result.getNewsId());
        assertEquals("제목", result.getTitle());
    }

    @Test
    void getNewsByIdOrThrowThrowsCustomExceptionWhenNotFound() {
        FakeNewsMapper mapper = new FakeNewsMapper();
        NewsService service = new NewsServiceImpl(mapper, new FakeNewsReportMapper(), new FakeNewsTermMapper());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getNewsByIdOrThrow(999L));

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    // news_report가 있는 뉴스는 목록에서도 summary가 채워지고 analyzed가 true여야 한다.
    @Test
    void getReportListReturnsSummaryAndAnalyzedTrueWhenReportExists() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.listItems = List.of(
                NewsReportListItem.builder()
                        .newsId(1L)
                        .title("제목")
                        .category("경제")
                        .source("매일경제")
                        .url("https://example.com/1")
                        .publishedAt(LocalDateTime.of(2026, 7, 30, 12, 0))
                        .summary("요약입니다")
                        .analyzed(true)
                        .build()
        );
        NewsService service = new NewsServiceImpl(newsMapper, new FakeNewsReportMapper(), new FakeNewsTermMapper());

        List<ReportListResponse> result = service.getReportList();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("요약입니다", result.get(0).getSummary());
        assertTrue(result.get(0).isAnalyzed());
    }

    // news_report가 없는 뉴스는 목록에서 summary가 null이고 analyzed가 false여야 한다(하드코딩 null 제거 검증).
    @Test
    void getReportListReturnsNullSummaryAndAnalyzedFalseWhenReportDoesNotExist() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.listItems = List.of(
                NewsReportListItem.builder()
                        .newsId(2L)
                        .title("제목2")
                        .category("경제")
                        .source("매일경제")
                        .url("https://example.com/2")
                        .publishedAt(LocalDateTime.of(2026, 7, 29, 9, 0))
                        .summary(null)
                        .analyzed(false)
                        .build()
        );
        NewsService service = new NewsServiceImpl(newsMapper, new FakeNewsReportMapper(), new FakeNewsTermMapper());

        List<ReportListResponse> result = service.getReportList();

        assertNull(result.get(0).getSummary());
        assertTrue(!result.get(0).isAnalyzed());
    }

    // Mapper가 이미 published_at 최신순으로 정렬해 반환하므로, Service는 순서를 바꾸지 않고 그대로 매핑해야 한다.
    @Test
    void getReportListPreservesMapperOrderingAndReturnsEmptyListWhenNoNews() {
        FakeNewsMapper emptyMapper = new FakeNewsMapper();
        NewsService emptyService =
                new NewsServiceImpl(emptyMapper, new FakeNewsReportMapper(), new FakeNewsTermMapper());
        assertTrue(emptyService.getReportList().isEmpty());

        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.listItems = List.of(
                NewsReportListItem.builder().newsId(3L).publishedAt(LocalDateTime.of(2026, 7, 30, 0, 0)).build(),
                NewsReportListItem.builder().newsId(1L).publishedAt(LocalDateTime.of(2026, 7, 29, 0, 0)).build(),
                NewsReportListItem.builder().newsId(2L).publishedAt(LocalDateTime.of(2026, 7, 28, 0, 0)).build()
        );
        NewsService service = new NewsServiceImpl(newsMapper, new FakeNewsReportMapper(), new FakeNewsTermMapper());

        List<ReportListResponse> result = service.getReportList();

        assertEquals(List.of(3L, 1L, 2L), result.stream().map(ReportListResponse::getId).collect(java.util.stream.Collectors.toList()));
    }

    // findAllWithReportSummary는 news_report가 있는 뉴스만 반환해야 한다(목록에 나온 기사는 클릭 즉시
    // AI 리포트가 보여야 함). 이 프로젝트에는 테스트 DB가 없어 SQL을 직접 실행하는 대신, 쿼리 텍스트가
    // INNER JOIN(리포트 없는 뉴스 제외)인지 검증해 LEFT JOIN으로 되돌아가는 회귀를 막는다.
    @Test
    void findAllWithReportSummaryQueryOnlyIncludesNewsWithReport() throws java.io.IOException {
        String mapperXml = readNewsMapperXml();

        int selectStart = mapperXml.indexOf("id=\"findAllWithReportSummary\"");
        assertTrue(selectStart >= 0, "findAllWithReportSummary 쿼리를 찾지 못했습니다.");

        int selectEnd = mapperXml.indexOf("</select>", selectStart);
        String querySql = mapperXml.substring(selectStart, selectEnd);

        assertTrue(querySql.contains("JOIN news_report"), "news_report와의 JOIN이 없습니다.");
        assertTrue(!querySql.contains("LEFT JOIN"), "LEFT JOIN을 사용하면 리포트가 없는 뉴스까지 노출됩니다.");
    }

    private String readNewsMapperXml() throws java.io.IOException {
        try (java.io.InputStream inputStream = getClass().getResourceAsStream("/mapper/report/NewsMapper.xml")) {
            org.junit.jupiter.api.Assertions.assertNotNull(inputStream, "mapper/report/NewsMapper.xml을 클래스패스에서 찾지 못했습니다.");
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void getReportDetailFillsAiFieldsWhenNewsReportExists() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        newsMapper.newsById.put(1L, news(1L));
        newsReportMapper.reportByNewsId.put(1L, NewsReport.builder()
                .reportId(1L)
                .newsId(1L)
                .summary("요약1\n요약2")
                .eventDescription("사건 설명입니다.")
                .cause("원인")
                .socialImpact("사회적 영향")
                .userImpact("사용자 영향")
                .responseStrategy("대응 방안")
                .build());
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper, newsTermMapper);

        ReportDetailResponse response = service.getReportDetail(1L);

        assertEquals(1L, response.getNewsId());
        // news_report.summary는 bullet을 줄바꿈으로 이어붙인 TEXT라, 다시 줄바꿈 기준으로 나눠 돌려준다.
        assertEquals(List.of("요약1", "요약2"), response.getSummaryPoints());
        assertEquals("사건 설명입니다.", response.getEventDescription());
        assertEquals("원인", response.getCause());
        assertEquals("사회적 영향", response.getSocialImpact());
        assertEquals(null, response.getUserImpact());
        assertEquals(null, response.getActionPlan());
        assertTrue(response.getTerms().isEmpty());
    }

    // 마이그레이션 이전의 기존 리포트는 summary가 bullet이 아니라 문단 하나였다. 줄바꿈이 없어도
    // 깨지지 않고 항목 1개짜리 목록으로 안전하게 내려가야 한다.
    @Test
    void getReportDetailReturnsSingleItemSummaryPointsForPreMigrationParagraphSummary() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        newsMapper.newsById.put(1L, news(1L));
        newsReportMapper.reportByNewsId.put(1L, NewsReport.builder()
                .reportId(1L)
                .newsId(1L)
                .summary("줄바꿈 없이 저장된 기존 문단 요약입니다.")
                .build());
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper, newsTermMapper);

        ReportDetailResponse response = service.getReportDetail(1L);

        assertEquals(List.of("줄바꿈 없이 저장된 기존 문단 요약입니다."), response.getSummaryPoints());
        // 마이그레이션 이전 리포트는 event_description이 없어(NULL) null로 내려간다 — 프론트가 이미
        // 빈 값을 안전하게 처리하므로(ReportSection의 "아직 내용이 준비되지 않았습니다") 별도 처리 불필요.
        assertNull(response.getEventDescription());
    }

    // 매칭된 금융용어가 있으면 news_term + financial_term 조인 결과가 termId/term/definition/source로 채워져야 한다.
    @Test
    void getReportDetailReturnsMatchedTermsWithNameAndDefinition() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        newsMapper.newsById.put(1L, news(1L));
        newsTermMapper.termsByNewsId.put(1L, List.of(
                FinancialTerm.builder()
                        .termId(10L)
                        .termName("기준금리")
                        .description("한국은행 금융통화위원회가 결정하는 정책금리.")
                        .source("한국은행")
                        .build(),
                FinancialTerm.builder()
                        .termId(20L)
                        .termName("가계수지")
                        .description("가정의 일정 기간 수입과 지출을 비교한 것.")
                        .source("금융감독원")
                        .build()
        ));
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper, newsTermMapper);

        ReportDetailResponse response = service.getReportDetail(1L);

        List<MatchedTermResponse> terms = response.getTerms();
        assertEquals(2, terms.size());
        assertEquals(10L, terms.get(0).getTermId());
        assertEquals("기준금리", terms.get(0).getTerm());
        assertEquals("한국은행 금융통화위원회가 결정하는 정책금리.", terms.get(0).getDefinition());
        assertEquals("한국은행", terms.get(0).getSource());
        assertEquals(20L, terms.get(1).getTermId());
        assertEquals("가계수지", terms.get(1).getTerm());
    }

    // 매칭된 용어가 없으면 null이 아니라 빈 리스트여야 한다.
    @Test
    void getReportDetailReturnsEmptyListNotNullWhenNoTermsMatched() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        newsMapper.newsById.put(1L, news(1L));
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper, newsTermMapper);

        ReportDetailResponse response = service.getReportDetail(1L);

        assertTrue(response.getTerms() != null && response.getTerms().isEmpty());
    }

    @Test
    void getReportDetailReturnsNullAiFieldsWhenNewsReportDoesNotExist() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        newsMapper.newsById.put(1L, news(1L));
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper, newsTermMapper);

        ReportDetailResponse response = service.getReportDetail(1L);

        assertEquals(1L, response.getNewsId());
        assertTrue(response.getSummaryPoints().isEmpty());
        assertNull(response.getEventDescription());
        assertNull(response.getCause());
        assertNull(response.getSocialImpact());
        assertNull(response.getUserImpact());
        assertNull(response.getActionPlan());
        assertTrue(response.getTerms().isEmpty());
    }

    @Test
    void getReportDetailThrowsCustomExceptionWhenNewsDoesNotExist() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsTermMapper newsTermMapper = new FakeNewsTermMapper();
        NewsService service = new NewsServiceImpl(newsMapper, newsReportMapper, newsTermMapper);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getReportDetail(999L));

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
    }

    private News news(Long newsId) {
        return News.builder()
                .newsId(newsId)
                .title("제목")
                .content("본문")
                .source("매일경제")
                .url("https://example.com/" + newsId)
                .category("경제")
                .publishedAt(LocalDateTime.of(2026, 7, 30, 12, 0))
                .build();
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeNewsMapper implements NewsMapper {

        private final Map<Long, News> newsById = new HashMap<>();

        @Override
        public int insertNews(News news) {
            return 0;
        }

        @Override
        public int existsByUrl(String url) {
            return 0;
        }

        @Override
        public News findById(Long newsId) {
            return newsById.get(newsId);
        }

        @Override
        public List<News> findLatest(int limit) {
            return new ArrayList<>();
        }

        @Override
        public List<NewsReportListItem> findAllWithReportSummary() {
            return listItems;
        }

        @Override
        public List<Long> findNewsIdsWithoutReport(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteNewsTermsBeforePublishedAt(LocalDateTime cutoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteNewsReportsBeforePublishedAt(LocalDateTime cutoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteNewsBeforePublishedAt(LocalDateTime cutoff) {
            throw new UnsupportedOperationException();
        }

        private List<NewsReportListItem> listItems = new ArrayList<>();
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeNewsReportMapper implements NewsReportMapper {

        private final Map<Long, NewsReport> reportByNewsId = new HashMap<>();

        @Override
        public NewsReport findByNewsId(Long newsId) {
            return reportByNewsId.get(newsId);
        }

        @Override
        public int insert(NewsReport newsReport) {
            throw new UnsupportedOperationException();
        }
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Mapper다. */
    private static class FakeNewsTermMapper implements NewsTermMapper {

        private final Map<Long, List<FinancialTerm>> termsByNewsId = new HashMap<>();

        @Override
        public int deleteByNewsId(Long newsId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int batchInsert(Long newsId, List<Long> termIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<FinancialTerm> findTermsByNewsId(Long newsId) {
            return termsByNewsId.getOrDefault(newsId, new ArrayList<>());
        }
    }
}
