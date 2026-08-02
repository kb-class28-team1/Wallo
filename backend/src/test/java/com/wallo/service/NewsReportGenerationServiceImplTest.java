package com.wallo.service;

import com.wallo.client.NewsReportAiClient;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.domain.News;
import com.wallo.domain.NewsReport;
import com.wallo.dto.ai.NewsReportAiRequest;
import com.wallo.dto.ai.NewsReportAiResponse;
import com.wallo.dto.response.ReportDetailResponse;
import com.wallo.dto.response.ReportListResponse;
import com.wallo.mapper.NewsReportMapper;
import com.wallo.term.service.FinancialTermMatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewsReportGenerationServiceImplTest {

    @Test
    void returnsExistingReportWithoutAiCallOrInsertWhenAlreadyExists() {
        FakeNewsService newsService = new FakeNewsService(news(1L, "본문 내용"));
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        NewsReport existing = existingReport(1L);
        newsReportMapper.initialReport = existing;
        FakeNewsReportAiClient aiClient = new FakeNewsReportAiClient((NewsReportAiResponse) null);
        FakeFinancialTermMatchingService termMatchingService = new FakeFinancialTermMatchingService();
        NewsReportGenerationService service =
                new NewsReportGenerationServiceImpl(newsService, newsReportMapper, aiClient, termMatchingService);

        NewsReport result = service.generateIfAbsent(1L);

        assertEquals(existing, result);
        assertEquals(0, aiClient.callCount);
        assertEquals(0, newsReportMapper.insertCallCount);
        // news_report가 이미 있어도 news_term은 뉴스 내용 기반이라 매번 다시 매칭해도 안전하다.
        assertEquals(1, termMatchingService.callCount);
    }

    @Test
    void generatesCallsAiOnceAndInsertsOnceWhenAbsent() {
        FakeNewsService newsService = new FakeNewsService(news(1L, "본문 내용"));
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        NewsReportAiResponse aiResponse =
                new NewsReportAiResponse("요약", "원인", "사회영향", "사용자영향", "대응방안");
        FakeNewsReportAiClient aiClient = new FakeNewsReportAiClient(aiResponse);
        FakeFinancialTermMatchingService termMatchingService = new FakeFinancialTermMatchingService();
        NewsReportGenerationService service =
                new NewsReportGenerationServiceImpl(newsService, newsReportMapper, aiClient, termMatchingService);

        NewsReport result = service.generateIfAbsent(1L);

        assertEquals(1, aiClient.callCount);
        assertEquals(1, newsReportMapper.insertCallCount);
        assertEquals("요약", result.getSummary());
        assertEquals("대응방안", result.getResponseStrategy());
        assertEquals(1, termMatchingService.callCount);
    }

    @Test
    void throwsReportNotFoundAndDoesNotCallAiWhenNewsDoesNotExist() {
        FakeNewsService newsService = new FakeNewsService(null);
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsReportAiClient aiClient = new FakeNewsReportAiClient((NewsReportAiResponse) null);
        NewsReportGenerationService service =
                new NewsReportGenerationServiceImpl(
                        newsService, newsReportMapper, aiClient, new FakeFinancialTermMatchingService());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.generateIfAbsent(999L));

        assertEquals(ErrorCode.REPORT_NOT_FOUND, exception.getErrorCode());
        assertEquals(0, aiClient.callCount);
    }

    @Test
    void throwsReportContentEmptyAndDoesNotCallAiOrInsertWhenContentIsBlank() {
        FakeNewsService newsService = new FakeNewsService(news(1L, "   "));
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsReportAiClient aiClient = new FakeNewsReportAiClient((NewsReportAiResponse) null);
        NewsReportGenerationService service =
                new NewsReportGenerationServiceImpl(
                        newsService, newsReportMapper, aiClient, new FakeFinancialTermMatchingService());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.generateIfAbsent(1L));

        assertEquals(ErrorCode.REPORT_CONTENT_EMPTY, exception.getErrorCode());
        assertEquals(0, aiClient.callCount);
        assertEquals(0, newsReportMapper.insertCallCount);
    }

    @Test
    void propagatesAiFailureAndDoesNotInsertWhenAiClientFails() {
        FakeNewsService newsService = new FakeNewsService(news(1L, "본문 내용"));
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        FakeNewsReportAiClient aiClient =
                new FakeNewsReportAiClient(new CustomException(ErrorCode.AI_REPORT_GENERATION_FAILED));
        NewsReportGenerationService service =
                new NewsReportGenerationServiceImpl(
                        newsService, newsReportMapper, aiClient, new FakeFinancialTermMatchingService());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.generateIfAbsent(1L));

        assertEquals(ErrorCode.AI_REPORT_GENERATION_FAILED, exception.getErrorCode());
        assertEquals(0, newsReportMapper.insertCallCount);
    }

    // AI 응답의 5개 핵심 필드(summary/cause/socialImpact/userImpact/responseStrategy) 중 하나라도
    // 비어 있으면 "반쪽짜리" 리포트를 저장하지 않고 실패시킨다. summary만 검증하면 나머지 필드가
    // 비어도 news_report에 저장돼 목록/상세에 불완전한 리포트가 그대로 노출되는 문제가 있어 보강했다.
    @Test
    void throwsAiReportInvalidResponseAndDoesNotInsertWhenAnyCoreFieldIsBlank() {
        List<NewsReportAiResponse> incompleteResponses = List.of(
                new NewsReportAiResponse(null, "원인", "사회영향", "사용자영향", "대응방안"),
                new NewsReportAiResponse("   ", "원인", "사회영향", "사용자영향", "대응방안"),
                new NewsReportAiResponse("요약", null, "사회영향", "사용자영향", "대응방안"),
                new NewsReportAiResponse("요약", "원인", null, "사용자영향", "대응방안"),
                new NewsReportAiResponse("요약", "원인", "사회영향", "   ", "대응방안"),
                new NewsReportAiResponse("요약", "원인", "사회영향", "사용자영향", null)
        );

        for (NewsReportAiResponse incomplete : incompleteResponses) {
            FakeNewsService newsService = new FakeNewsService(news(1L, "본문 내용"));
            FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
            FakeNewsReportAiClient aiClient = new FakeNewsReportAiClient(incomplete);
            NewsReportGenerationService service =
                    new NewsReportGenerationServiceImpl(
                            newsService, newsReportMapper, aiClient, new FakeFinancialTermMatchingService());

            CustomException exception = assertThrows(
                    CustomException.class,
                    () -> service.generateIfAbsent(1L));

            assertEquals(ErrorCode.AI_REPORT_INVALID_RESPONSE, exception.getErrorCode());
            assertEquals(0, newsReportMapper.insertCallCount);
        }
    }

    /**
     * 동시에 같은 newsId로 두 요청이 들어와 INSERT가 UNIQUE 제약을 위반하는 상황을 재현한다.
     * 실제 동시성(스레드)을 흉내내지 않고, Fake Mapper의 insert()가 DuplicateKeyException을
     * 던지도록 만들어 "재조회 후 기존 값 반환" 로직만 검증한다 — 현재 프로젝트 규모에서는
     * 이 정도 단위 테스트로 충분하다고 판단했다. 실제 스레드 동시 실행 테스트는 과도한 복잡도 대비
     * 얻는 확신이 적어 생략했다.
     */
    @Test
    void reusesExistingReportWhenDuplicateKeyExceptionOccursOnInsert() {
        FakeNewsService newsService = new FakeNewsService(news(1L, "본문 내용"));
        FakeNewsReportMapper newsReportMapper = new FakeNewsReportMapper();
        newsReportMapper.throwDuplicateKeyOnInsert = true;
        NewsReport winner = existingReport(1L);
        newsReportMapper.reportAfterDuplicateKey = winner;
        NewsReportAiResponse aiResponse =
                new NewsReportAiResponse("요약", "원인", "사회영향", "사용자영향", "대응방안");
        FakeNewsReportAiClient aiClient = new FakeNewsReportAiClient(aiResponse);
        NewsReportGenerationService service =
                new NewsReportGenerationServiceImpl(
                        newsService, newsReportMapper, aiClient, new FakeFinancialTermMatchingService());

        NewsReport result = service.generateIfAbsent(1L);

        assertEquals(winner, result);
        assertEquals(1, newsReportMapper.insertCallCount);
    }

    private News news(Long newsId, String content) {
        return News.builder()
                .newsId(newsId)
                .title("제목")
                .content(content)
                .source("매일경제")
                .url("https://example.com/" + newsId)
                .category("경제")
                .publishedAt(LocalDateTime.of(2026, 7, 30, 12, 0))
                .build();
    }

    private NewsReport existingReport(Long newsId) {
        return NewsReport.builder()
                .reportId(1L)
                .newsId(newsId)
                .summary("기존 요약")
                .build();
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeNewsService implements NewsService {

        private final News news;

        private FakeNewsService(News news) {
            this.news = news;
        }

        @Override
        public boolean saveNews(News news) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByUrl(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public News getNewsById(Long newsId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public News getNewsByIdOrThrow(Long newsId) {
            if (news == null) {
                throw new CustomException(ErrorCode.REPORT_NOT_FOUND);
            }
            return news;
        }

        @Override
        public ReportDetailResponse getReportDetail(Long newsId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<News> getLatestNews(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReportListResponse> getReportList() {
            throw new UnsupportedOperationException();
        }
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeNewsReportMapper implements NewsReportMapper {

        private NewsReport initialReport;
        private NewsReport reportAfterDuplicateKey;
        private boolean throwDuplicateKeyOnInsert = false;
        private int findByNewsIdCallCount = 0;
        private int insertCallCount = 0;

        @Override
        public NewsReport findByNewsId(Long newsId) {
            findByNewsIdCallCount++;
            if (findByNewsIdCallCount == 1) {
                return initialReport;
            }
            return reportAfterDuplicateKey;
        }

        @Override
        public int insert(NewsReport newsReport) {
            insertCallCount++;
            if (throwDuplicateKeyOnInsert) {
                throw new DuplicateKeyException("news_report.news_id UNIQUE 제약 위반");
            }
            newsReport.setReportId(1L);
            return 1;
        }
    }

    /** 실제 AI 서버 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeNewsReportAiClient implements NewsReportAiClient {

        private final NewsReportAiResponse response;
        private final RuntimeException exceptionToThrow;
        private int callCount = 0;

        private FakeNewsReportAiClient(NewsReportAiResponse response) {
            this.response = response;
            this.exceptionToThrow = null;
        }

        private FakeNewsReportAiClient(RuntimeException exceptionToThrow) {
            this.response = null;
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public NewsReportAiResponse generateReport(NewsReportAiRequest request) {
            callCount++;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return response;
        }
    }

    /** 실제 DB 대신 호출 여부만 확인하기 위한 테스트 전용 Fake다. */
    private static class FakeFinancialTermMatchingService implements FinancialTermMatchingService {

        private int callCount = 0;

        @Override
        public int matchAndSaveTerms(Long newsId, String title, String content) {
            callCount++;
            return 0;
        }
    }
}
