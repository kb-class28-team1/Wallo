package com.wallo.report.scheduler;

import com.wallo.report.crawler.NewsCrawler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 하루 4회(06/12/15/18시, Asia/Seoul) {@link NewsCrawler#crawlAndSave()}를 자동 실행한다.
 * 증시 일정이 아니라, 사용자가 하루 동안 접하는 경제·생활금융 뉴스를 최신 상태로 유지하는 것이 목적이다.
 *
 * <p>크롤링이 끝난 뒤에는 {@link FinancialReportGenerationScheduler}를 이어서 호출해, 리포트가 없는
 * 뉴스를 곧바로 채워 넣는다. 사용자가 목록에서 기사를 클릭했을 때 "생성 중" 대기 없이 바로 AI 리포트가
 * 보이게 하기 위함이다. 다만 AI 호출은 크롤링 저장이 전부 끝난 뒤 완전히 분리된 단계에서 수행하므로,
 * AI 서버 장애가 뉴스 저장 자체를 실패시키지는 않는다(크롤링 트랜잭션과 결합하지 않음).
 */
@Component
public class NewsCrawlingScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewsCrawlingScheduler.class);

    private final NewsCrawler newsCrawler;
    private final FinancialReportGenerationScheduler financialReportGenerationScheduler;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public NewsCrawlingScheduler(
            NewsCrawler newsCrawler,
            FinancialReportGenerationScheduler financialReportGenerationScheduler
    ) {
        this.newsCrawler = newsCrawler;
        this.financialReportGenerationScheduler = financialReportGenerationScheduler;
    }

    @Scheduled(cron = "0 0 6,12,15,18 * * *", zone = "Asia/Seoul")
    public void scheduledNewsCrawling() {
        run(false);
    }

    /**
     * 시연 또는 운영 점검 시 스케줄 시간을 기다리지 않고 크롤링과 리포트 생성을 즉시 실행한다.
     * 자동 스케줄과 수동 요청이 겹치면 새 실행을 시작하지 않아 중복 Chrome/AI 호출을 막는다.
     */
    public RunResult runNow() {
        return run(true);
    }

    private RunResult run(boolean manual) {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("뉴스 크롤링과 금융 리포트 생성 작업이 이미 진행 중입니다.");
            return RunResult.alreadyRunning();
        }

        try {
            return runCrawlAndReportGeneration(manual);
        } finally {
            isRunning.set(false);
        }
    }

    private RunResult runCrawlAndReportGeneration(boolean manual) {
        log.info("===== 뉴스 크롤링 시작 =====");

        List<Long> savedNewsIds = List.of();
        try {
            savedNewsIds = newsCrawler.crawlAndSave();
        } catch (Exception e) {
            log.error("===== 뉴스 크롤링 실패 =====", e);
        } finally {
            log.info("===== 뉴스 크롤링 종료 =====");
        }

        FinancialReportGenerationScheduler.BatchResult reportResult =
                triggerReportGeneration(savedNewsIds, manual);
        return new RunResult(
                true,
                savedNewsIds.size(),
                reportResult.total(),
                reportResult.success(),
                reportResult.failure(),
                reportResult.skipped()
        );
    }

    /**
     * 크롤링 저장이 전부 끝난 뒤(성공/실패와 무관하게) 리포트가 없는 뉴스를 바로 채운다. 크롤링과는
     * 별개 단계라 여기서 예외가 나도 이미 끝난 크롤링 결과에는 영향이 없다. 이번에 새로 저장된
     * savedNewsIds는 batch-size 제한과 무관하게 항상 우선 생성 대상에 포함된다. 자동 실행은
     * financial-report.scheduler.enabled 설정을 따르고, 수동 실행은 시연을 위해 활성화 여부와 무관하게
     * 실행한다. 두 경로 모두 백로그 조회 건수는 batch-size 설정을 사용한다.
     */
    private FinancialReportGenerationScheduler.BatchResult triggerReportGeneration(
            List<Long> savedNewsIds,
            boolean manual
    ) {
        try {
            return manual
                    ? financialReportGenerationScheduler.generateManuallyForCrawledNews(savedNewsIds)
                    : financialReportGenerationScheduler.generateForCrawledNews(savedNewsIds);
        } catch (Exception e) {
            log.error("===== 크롤링 후속 금융 리포트 생성 실패 =====", e);
            return new FinancialReportGenerationScheduler.BatchResult(0, 0, 0, 0);
        }
    }

    public record RunResult(
            boolean started,
            int crawledNewsCount,
            int targetReportCount,
            int generatedReportCount,
            int failedReportCount,
            int skippedReportCount
    ) {
        private static RunResult alreadyRunning() {
            return new RunResult(false, 0, 0, 0, 0, 0);
        }
    }
}
