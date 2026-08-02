package com.wallo.scheduler;

import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.mapper.NewsMapper;
import com.wallo.service.NewsReportGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * news_report가 없는 뉴스를 찾아 금융 리포트를 순차 생성한다.
 *
 * <p>크롤러({@link com.wallo.crawler.NewsCrawler})는 news 저장까지만 담당하고 AI를 직접 호출하지
 * 않는다. AI 호출은 이 스케줄러가 별도 시점에 수행해, AI 장애가 크롤링(뉴스 저장) 트랜잭션에 영향을
 * 주지 않도록 완전히 분리한다.
 *
 * <p>뉴스 크롤링({@link NewsCrawlingScheduler}, 06/12/15/18시 정각)이 끝나면 {@link #generateForCrawledNews}가
 * 곧바로 이어서 호출되고, 그와 별개로 이 클래스 자체의 주기 실행이 30분 뒤(06:30/12:30/15:30/18:30)에
 * 한 번 더 돌며 안전망 역할을 한다.
 */
@Component
public class FinancialReportGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(FinancialReportGenerationScheduler.class);

    private static final BatchResult EMPTY_RESULT = new BatchResult(0, 0, 0, 0);

    // 뉴스 자체가 사라졌거나(REPORT_NOT_FOUND) 본문이 비어 있어(REPORT_CONTENT_EMPTY) 다시 시도해도
    // 성공할 수 없는 경우는 실패가 아니라 스킵으로 집계한다.
    private static final Set<ErrorCode> SKIPPABLE_ERROR_CODES =
            EnumSet.of(ErrorCode.REPORT_NOT_FOUND, ErrorCode.REPORT_CONTENT_EMPTY);

    private final NewsMapper newsMapper;
    private final NewsReportGenerationService newsReportGenerationService;
    private final boolean enabled;
    private final int batchSize;

    // 이전 실행이 아직 끝나지 않았으면 겹쳐 실행되지 않도록 막는 안전장치. Spring의 기본 스케줄러는
    // 단일 스레드로 동작해 원래도 겹치지 않지만, 이 플래그로 그 가정과 무관하게 명시적으로 보장한다.
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public FinancialReportGenerationScheduler(
            NewsMapper newsMapper,
            NewsReportGenerationService newsReportGenerationService,
            @Value("${financial-report.scheduler.enabled:false}") boolean enabled,
            @Value("${financial-report.scheduler.batch-size:10}") int batchSize
    ) {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "financial-report.scheduler.batch-size는 1 이상이어야 합니다. 입력값: " + batchSize);
        }

        this.newsMapper = newsMapper;
        this.newsReportGenerationService = newsReportGenerationService;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "0 30 6,12,15,18 * * *", zone = "Asia/Seoul")
    public void generateMissingReports() {
        runGuarded(List.of());
    }

    /**
     * 크롤링 직후({@link NewsCrawlingScheduler}) 호출 전용. 이번에 새로 저장된 뉴스(priorityNewsIds)는
     * batch-size 제한과 무관하게 이번 실행에 항상 포함되어, 배치 크기 때문에 밀려나지 않는다.
     * 남는 배치 예산은 기존과 동일하게 findNewsIdsWithoutReport(batchSize) 백로그로 채운다.
     */
    BatchResult generateForCrawledNews(List<Long> priorityNewsIds) {
        return runGuarded(priorityNewsIds);
    }

    private BatchResult runGuarded(List<Long> priorityNewsIds) {
        if (!enabled) {
            log.info("금융 리포트 자동 생성 스케줄러가 비활성화되어 있습니다(financial-report.scheduler.enabled=false).");
            return EMPTY_RESULT;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("이전 금융 리포트 자동 생성 작업이 아직 진행 중이라 이번 실행은 건너뜁니다.");
            return EMPTY_RESULT;
        }

        try {
            return runBatch(priorityNewsIds);
        } finally {
            isRunning.set(false);
        }
    }

    /** 접근 제한자를 패키지 수준으로 두어, 테스트에서 성공/실패/스킵 건수를 로그가 아니라 반환값으로 바로 검증할 수 있게 한다. */
    BatchResult runBatch() {
        return runBatch(List.of());
    }

    /**
     * priorityNewsIds는 batch-size와 무관하게 항상 대상에 포함하고, 남는 배치 예산은 기존처럼
     * findNewsIdsWithoutReport(batchSize) 백로그로 채운다. 두 목록에 겹치는 뉴스(priorityNewsIds도
     * 아직 리포트가 없으니 백로그 조회에 함께 잡힐 수 있다)는 LinkedHashSet으로 한 번만 처리한다.
     */
    BatchResult runBatch(List<Long> priorityNewsIds) {
        List<Long> backlogNewsIds = newsMapper.findNewsIdsWithoutReport(batchSize);

        Set<Long> targetNewsIds = new LinkedHashSet<>(priorityNewsIds);
        targetNewsIds.addAll(backlogNewsIds);

        if (targetNewsIds.isEmpty()) {
            log.info("===== 금융 리포트 자동 생성: 리포트가 없는 뉴스가 없어 처리 대상 없음 =====");
            return EMPTY_RESULT;
        }

        log.info(
                "===== 금융 리포트 자동 생성 시작 - 우선 대상 {}건 + 백로그 {}건 = 총 {}건 =====",
                priorityNewsIds.size(), backlogNewsIds.size(), targetNewsIds.size()
        );

        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        for (Long newsId : targetNewsIds) {
            try {
                newsReportGenerationService.generateIfAbsent(newsId);
                successCount++;
            } catch (CustomException exception) {
                if (SKIPPABLE_ERROR_CODES.contains(exception.getErrorCode())) {
                    skippedCount++;
                    log.warn("금융 리포트 생성 스킵 - newsId: {}, 사유: {}", newsId, exception.getErrorCode());
                } else {
                    failureCount++;
                    log.error("금융 리포트 생성 실패 - newsId: {}", newsId, exception);
                }
            } catch (RuntimeException exception) {
                failureCount++;
                log.error("금융 리포트 생성 중 예상치 못한 오류 - newsId: {}", newsId, exception);
            }
        }

        log.info(
                "===== 금융 리포트 자동 생성 종료 - 성공 {}건, 실패 {}건, 스킵 {}건 (전체 대상 {}건) =====",
                successCount, failureCount, skippedCount, targetNewsIds.size()
        );

        return new BatchResult(targetNewsIds.size(), successCount, failureCount, skippedCount);
    }

    /** 이번 실행의 처리 결과 요약. total = success + failure + skipped. */
    record BatchResult(int total, int success, int failure, int skipped) {
    }
}
