package com.wallo.report.scheduler;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.report.domain.News;
import com.wallo.report.domain.NewsReport;
import com.wallo.report.domain.NewsReportListItem;
import com.wallo.report.mapper.NewsMapper;
import com.wallo.report.scheduler.FinancialReportGenerationScheduler.BatchResult;
import com.wallo.report.service.NewsReportGenerationService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialReportGenerationSchedulerTest {

    // 1. 비활성화 상태면 대상 조회조차 하지 않고 아무것도 생성하지 않는다.
    @Test
    void doesNotQueryOrGenerateWhenDisabled() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L, 2L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, false, 10);

        scheduler.generateMissingReports();

        assertEquals(0, newsMapper.findNewsIdsWithoutReportCallCount);
        assertEquals(0, generationService.callCount());
    }

    @Test
    void manualGenerationRunsEvenWhenAutomaticSchedulerIsDisabled() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(2L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, false, 10);

        BatchResult result = scheduler.generateManuallyForCrawledNews(List.of(1L));

        assertEquals(List.of(1L, 2L), generationService.requestedNewsIds);
        assertEquals(new BatchResult(2, 2, 0, 0), result);
    }

    // 2. 대상이 없으면(빈 목록) 아무것도 생성하지 않는다.
    @Test
    void doesNothingWhenNoTargets() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of();
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);

        BatchResult result = scheduler.runBatch();

        assertEquals(new BatchResult(0, 0, 0, 0), result);
        assertEquals(0, generationService.callCount());
    }

    // 3. 대상 각각에 대해 generateIfAbsent()가 순서대로 호출된다.
    @Test
    void generatesForEachTargetNewsInOrder() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(3L, 1L, 2L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);

        BatchResult result = scheduler.runBatch();

        assertEquals(List.of(3L, 1L, 2L), generationService.requestedNewsIds);
        assertEquals(new BatchResult(3, 3, 0, 0), result);
    }

    // 4. 특정 뉴스 처리가 실패해도(일반 예외) 나머지 뉴스는 계속 처리된다.
    @Test
    void continuesProcessingRemainingNewsWhenOneFails() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L, 2L, 3L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        generationService.failuresByNewsId.put(2L, new CustomException(ErrorCode.AI_REPORT_GENERATION_FAILED));
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);

        BatchResult result = scheduler.runBatch();

        assertEquals(List.of(1L, 2L, 3L), generationService.requestedNewsIds);
        assertEquals(new BatchResult(3, 2, 1, 0), result);
    }

    // 5. 본문이 비어 있거나(REPORT_CONTENT_EMPTY) 뉴스가 사라진 경우(REPORT_NOT_FOUND)는
    //    실패가 아니라 스킵으로 집계되고, 처리는 계속된다.
    @Test
    void classifiesContentEmptyAndNotFoundAsSkippedNotFailed() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L, 2L, 3L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        generationService.failuresByNewsId.put(1L, new CustomException(ErrorCode.REPORT_CONTENT_EMPTY));
        generationService.failuresByNewsId.put(2L, new CustomException(ErrorCode.REPORT_NOT_FOUND));
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);

        BatchResult result = scheduler.runBatch();

        assertEquals(new BatchResult(3, 1, 0, 2), result);
    }

    // 6. 예상 못 한 일반 RuntimeException도 실패로 집계되며 처리가 중단되지 않는다.
    @Test
    void classifiesUnexpectedRuntimeExceptionAsFailure() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L, 2L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        generationService.failuresByNewsId.put(1L, new IllegalStateException("예상치 못한 오류"));
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);

        BatchResult result = scheduler.runBatch();

        assertEquals(new BatchResult(2, 1, 1, 0), result);
    }

    // 7. 설정된 배치 크기가 Mapper 조회에 그대로 전달된다.
    @Test
    void passesConfiguredBatchSizeToMapper() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of();
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 25);

        scheduler.runBatch();

        assertEquals(25, newsMapper.lastRequestedLimit);
    }

    // 8. 정상적으로 끝난 실행 뒤에는 실행 중 플래그가 풀려, 다음 스케줄에서도 정상 처리된다(연속 실행 검증).
    @Test
    void allowsSequentialRunsAfterPreviousRunCompletes() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);

        scheduler.requestGeneration();
        scheduler.generateNextMissingReport();
        scheduler.generateNextMissingReport();

        assertEquals(2, newsMapper.findNewsIdsWithoutReportCallCount);
        assertEquals(2, generationService.callCount());
    }

    // 9. batchSize가 1 미만이면 생성자가 명확한 IllegalArgumentException을 던진다.
    @Test
    void rejectsInvalidBatchSize() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();

        assertThrows(
                IllegalArgumentException.class,
                () -> new FinancialReportGenerationScheduler(newsMapper, generationService, true, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FinancialReportGenerationScheduler(newsMapper, generationService, true, -1));
    }

    // 10. NewsMapper.xml의 findNewsIdsWithoutReport 쿼리에 본문(content) 빈 값 필터가 실제로
    //     들어있는지 확인한다. 이 프로젝트에는 테스트 DB가 없어 SQL을 직접 실행하는 대신,
    //     쿼리 텍스트에 필요한 조건이 존재하는지를 검증해 회귀(필터가 실수로 지워지는 상황)를 막는다.
    @Test
    void findNewsIdsWithoutReportQueryFiltersOutBlankContent() throws IOException {
        String mapperXml = readNewsMapperXml();

        int selectStart = mapperXml.indexOf("id=\"findNewsIdsWithoutReport\"");
        assertTrue(selectStart >= 0, "findNewsIdsWithoutReport 쿼리를 찾지 못했습니다.");

        int selectEnd = mapperXml.indexOf("</select>", selectStart);
        String querySql = mapperXml.substring(selectStart, selectEnd);

        assertTrue(querySql.contains("n.content IS NOT NULL"), "본문 NULL 제외 조건이 없습니다.");
        assertTrue(querySql.contains("TRIM(n.content)"), "본문 공백 제외 조건이 없습니다.");
    }

    // 11. 크롤링 직후 우선 처리 대상(priorityNewsIds)은 batch-size로 채워진 백로그와 무관하게
    //     이번 실행에 항상 포함된다(배치 크기 때문에 밀려나지 않음).
    @Test
    void priorityNewsIdsAreAlwaysIncludedRegardlessOfBatchSize() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(10L, 11L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 2);

        BatchResult result = scheduler.runBatch(List.of(99L));

        assertEquals(List.of(99L, 10L, 11L), generationService.requestedNewsIds);
        assertEquals(new BatchResult(3, 3, 0, 0), result);
        assertEquals(2, newsMapper.lastRequestedLimit);
    }

    // 12. 우선 대상이 백로그 조회 결과에도 포함돼 있으면(둘 다 리포트가 없는 상태) 한 번만 처리된다.
    @Test
    void duplicatesBetweenPriorityAndBacklogAreProcessedOnce() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L, 2L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);

        BatchResult result = scheduler.runBatch(List.of(2L));

        assertEquals(List.of(2L, 1L), generationService.requestedNewsIds);
        assertEquals(new BatchResult(2, 2, 0, 0), result);
    }

    // 13. 스케줄러가 비활성화 상태면 크롤링 직후 호출(generateForCrawledNews)도 대상 조회 없이 스킵된다.
    @Test
    void generateForCrawledNewsSkipsWhenDisabled() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L);
        FakeNewsReportGenerationService generationService = new FakeNewsReportGenerationService();
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, false, 10);

        BatchResult result = scheduler.generateForCrawledNews(List.of(99L));

        assertEquals(new BatchResult(0, 0, 0, 0), result);
        assertEquals(0, newsMapper.findNewsIdsWithoutReportCallCount);
        assertEquals(0, generationService.callCount());
    }

    // 14. 크롤링 직후 호출(generateForCrawledNews)과 수동 누락 리포트 생성(generateMissingReports)이 겹쳐도
    //     isRunning 가드 때문에 재진입 호출은 대상 조회조차 없이 즉시 건너뛴다. 실제 스레드 동시 실행 대신,
    //     처리 도중(generateIfAbsent 안에서) 같은 스케줄러를 재호출하는 방식으로 결정적으로(non-flaky) 검증한다.
    @Test
    void skipsOverlappingCallWhileAlreadyRunning() {
        FakeNewsMapper newsMapper = new FakeNewsMapper();
        newsMapper.targetIds = List.of(1L);
        FinancialReportGenerationScheduler[] schedulerHolder = new FinancialReportGenerationScheduler[1];
        ReentrantFakeNewsReportGenerationService generationService =
                new ReentrantFakeNewsReportGenerationService(() -> schedulerHolder[0].generateMissingReports());
        FinancialReportGenerationScheduler scheduler =
                new FinancialReportGenerationScheduler(newsMapper, generationService, true, 10);
        schedulerHolder[0] = scheduler;

        scheduler.requestGeneration();
        scheduler.generateNextMissingReport();

        // 재진입 호출이 대상을 실제로 처리했다면 백로그 조회와 generateIfAbsent 호출이 2번씩 찍혔을 것이다.
        assertEquals(1, newsMapper.findNewsIdsWithoutReportCallCount);
        assertEquals(List.of(1L), generationService.requestedNewsIds);
    }

    private String readNewsMapperXml() throws IOException {
        try (InputStream inputStream =
                     getClass().getResourceAsStream("/mapper/report/NewsMapper.xml")) {
            assertNotNull(inputStream, "mapper/report/NewsMapper.xml을 클래스패스에서 찾지 못했습니다.");
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeNewsMapper implements NewsMapper {

        private List<Long> targetIds = List.of();
        private int findNewsIdsWithoutReportCallCount = 0;
        private int lastRequestedLimit = -1;

        @Override
        public int insertNews(News news) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int existsByUrl(String url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public News findById(Long newsId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<News> findLatest(int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NewsReportListItem> findAllWithReportSummary() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Long> findNewsIdsWithoutReport(int limit) {
            findNewsIdsWithoutReportCallCount++;
            lastRequestedLimit = limit;
            return new ArrayList<>(targetIds);
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
    }

    /** 실제 DB/AI 서버 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeNewsReportGenerationService implements NewsReportGenerationService {

        private final Map<Long, RuntimeException> failuresByNewsId = new HashMap<>();
        private final List<Long> requestedNewsIds = new ArrayList<>();

        @Override
        public NewsReport generateIfAbsent(Long newsId) {
            requestedNewsIds.add(newsId);

            RuntimeException failure = failuresByNewsId.get(newsId);
            if (failure != null) {
                throw failure;
            }

            return NewsReport.builder().newsId(newsId).summary("요약").build();
        }

        private int callCount() {
            return requestedNewsIds.size();
        }
    }

    /**
     * isRunning 재진입 방지 가드를 검증하기 위한 테스트 전용 Fake다. generateIfAbsent 처리 도중
     * (즉 바깥쪽 실행이 아직 isRunning=true인 상태에서) 주어진 콜백으로 스케줄러를 다시 호출해,
     * 크롤링 직후 트리거와 백업 스케줄이 겹치는 상황을 스레드 없이 재현한다.
     */
    private static class ReentrantFakeNewsReportGenerationService implements NewsReportGenerationService {

        private final Runnable overlappingCall;
        private final List<Long> requestedNewsIds = new ArrayList<>();

        private ReentrantFakeNewsReportGenerationService(Runnable overlappingCall) {
            this.overlappingCall = overlappingCall;
        }

        @Override
        public NewsReport generateIfAbsent(Long newsId) {
            requestedNewsIds.add(newsId);
            overlappingCall.run();
            return NewsReport.builder().newsId(newsId).summary("요약").build();
        }

        private int callCount() {
            return requestedNewsIds.size();
        }
    }
}
