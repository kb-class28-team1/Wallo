package com.wallo.report.scheduler;

import com.wallo.report.crawler.NewsCrawler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NewsCrawler와 FinancialReportGenerationScheduler는 인터페이스가 없는 구체 클래스라
 * (다른 곳의 Fake 객체 관례 대신) Mockito로 모킹한다. ChallengeControllerTest와 동일한 방식이다.
 */
class NewsCrawlingSchedulerTest {

    // 1. 수동 실행은 뉴스만 크롤링하고 AI 생성을 시작하지 않는다.
    @Test
    void manualRunOnlyCrawlsNews() {
        NewsCrawler newsCrawler = mock(NewsCrawler.class);
        List<Long> savedNewsIds = List.of(1L, 2L);
        when(newsCrawler.crawlAndSave()).thenReturn(savedNewsIds);
        FinancialReportGenerationScheduler reportScheduler = mock(FinancialReportGenerationScheduler.class);
        NewsCrawlingScheduler scheduler = new NewsCrawlingScheduler(newsCrawler, reportScheduler);

        NewsCrawlingScheduler.RunResult result = scheduler.runNow();

        verify(newsCrawler).crawlAndSave();
        org.junit.jupiter.api.Assertions.assertTrue(result.started());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.crawledNewsCount());
    }

    // 2. 자동 스케줄은 크롤링 후 백그라운드 AI 생성을 예약한다.
    @Test
    void scheduledRunQueuesReportGenerationEvenWhenCrawlFails() {
        NewsCrawler newsCrawler = mock(NewsCrawler.class);
        doThrow(new RuntimeException("크롤링 실패")).when(newsCrawler).crawlAndSave();
        FinancialReportGenerationScheduler reportScheduler = mock(FinancialReportGenerationScheduler.class);
        NewsCrawlingScheduler scheduler = new NewsCrawlingScheduler(newsCrawler, reportScheduler);

        assertDoesNotThrow(scheduler::scheduledNewsCrawling);

        verify(reportScheduler).requestGeneration();
    }

    // 3. 리포트 생성 단계에서 예외가 나도 크롤링 스케줄러 밖으로 전파되지 않는다(완전히 분리된 단계).
    @Test
    void reportGenerationFailureDoesNotPropagate() {
        NewsCrawler newsCrawler = mock(NewsCrawler.class);
        when(newsCrawler.crawlAndSave()).thenReturn(List.of());
        FinancialReportGenerationScheduler reportScheduler = mock(FinancialReportGenerationScheduler.class);
        doThrow(new RuntimeException("예약 실패")).when(reportScheduler).requestGeneration();
        NewsCrawlingScheduler scheduler = new NewsCrawlingScheduler(newsCrawler, reportScheduler);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, scheduler::scheduledNewsCrawling);

        verify(newsCrawler).crawlAndSave();
    }
}
