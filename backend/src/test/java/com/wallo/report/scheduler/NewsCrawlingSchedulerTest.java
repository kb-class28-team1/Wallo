package com.wallo.report.scheduler;

import com.wallo.report.crawler.NewsCrawler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NewsCrawler와 FinancialReportGenerationScheduler는 인터페이스가 없는 구체 클래스라
 * (다른 곳의 Fake 객체 관례 대신) Mockito로 모킹한다. ChallengeControllerTest와 동일한 방식이다.
 */
class NewsCrawlingSchedulerTest {

    // 1. 크롤링이 성공하면 이어서 이번에 저장된 news_id를 우선 대상으로 금융 리포트 생성이 트리거된다.
    @Test
    void triggersReportGenerationAfterSuccessfulCrawl() {
        NewsCrawler newsCrawler = mock(NewsCrawler.class);
        List<Long> savedNewsIds = List.of(1L, 2L);
        when(newsCrawler.crawlAndSave()).thenReturn(savedNewsIds);
        FinancialReportGenerationScheduler reportScheduler = mock(FinancialReportGenerationScheduler.class);
        when(reportScheduler.generateManuallyForCrawledNews(eq(savedNewsIds)))
                .thenReturn(new FinancialReportGenerationScheduler.BatchResult(2, 2, 0, 0));
        NewsCrawlingScheduler scheduler = new NewsCrawlingScheduler(newsCrawler, reportScheduler);

        NewsCrawlingScheduler.RunResult result = scheduler.runNow();

        verify(newsCrawler).crawlAndSave();
        verify(reportScheduler).generateManuallyForCrawledNews(eq(savedNewsIds));
        org.junit.jupiter.api.Assertions.assertTrue(result.started());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.crawledNewsCount());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.generatedReportCount());
    }

    // 2. 크롤링이 실패해도(기존 news 백로그가 있을 수 있으므로) 리포트 생성은 빈 우선 목록으로 그대로 시도된다.
    @Test
    void stillTriggersReportGenerationWhenCrawlFails() {
        NewsCrawler newsCrawler = mock(NewsCrawler.class);
        doThrow(new RuntimeException("크롤링 실패")).when(newsCrawler).crawlAndSave();
        FinancialReportGenerationScheduler reportScheduler = mock(FinancialReportGenerationScheduler.class);
        when(reportScheduler.generateForCrawledNews(eq(List.of())))
                .thenReturn(new FinancialReportGenerationScheduler.BatchResult(0, 0, 0, 0));
        NewsCrawlingScheduler scheduler = new NewsCrawlingScheduler(newsCrawler, reportScheduler);

        assertDoesNotThrow(scheduler::scheduledNewsCrawling);

        verify(reportScheduler).generateForCrawledNews(eq(List.of()));
    }

    // 3. 리포트 생성 단계에서 예외가 나도 크롤링 스케줄러 밖으로 전파되지 않는다(완전히 분리된 단계).
    @Test
    void reportGenerationFailureDoesNotPropagate() {
        NewsCrawler newsCrawler = mock(NewsCrawler.class);
        when(newsCrawler.crawlAndSave()).thenReturn(List.of());
        FinancialReportGenerationScheduler reportScheduler = mock(FinancialReportGenerationScheduler.class);
        doThrow(new RuntimeException("AI 서버 장애")).when(reportScheduler).generateForCrawledNews(eq(List.of()));
        NewsCrawlingScheduler scheduler = new NewsCrawlingScheduler(newsCrawler, reportScheduler);

        assertDoesNotThrow(scheduler::scheduledNewsCrawling);

        verify(newsCrawler).crawlAndSave();
    }
}
