package com.wallo.scheduler;

import com.wallo.term.service.FinancialTermMatchingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialTermCacheRefreshSchedulerTest {

    // 1. 정상적으로 financial_term 매칭 캐시 갱신을 호출한다.
    @Test
    void callsRefreshCacheOnFinancialTermMatchingService() {
        FakeFinancialTermMatchingService service = new FakeFinancialTermMatchingService();
        FinancialTermCacheRefreshScheduler scheduler = new FinancialTermCacheRefreshScheduler(service);

        scheduler.refreshCache();

        assertEquals(1, service.refreshCallCount);
    }

    // 2. 갱신 중 예외가 나도 스케줄러 밖으로 전파되지 않는다 — 이번 주기만 실패하고 기존 캐시는
    //    그대로 유지되며, 다음 주기에 다시 시도된다(서버 전체에 영향 없음).
    @Test
    void doesNotPropagateExceptionWhenRefreshFails() {
        FakeFinancialTermMatchingService service = new FakeFinancialTermMatchingService();
        service.failNext = true;
        FinancialTermCacheRefreshScheduler scheduler = new FinancialTermCacheRefreshScheduler(service);

        assertDoesNotThrow(scheduler::refreshCache);

        assertEquals(1, service.refreshCallCount);
    }

    /** 실제 DB 대신 서비스 규칙만 검증하기 위한 테스트 전용 Fake다. */
    private static class FakeFinancialTermMatchingService implements FinancialTermMatchingService {

        private int refreshCallCount = 0;
        private boolean failNext = false;

        @Override
        public int matchAndSaveTerms(Long newsId, String title, String content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refreshCache() {
            refreshCallCount++;
            if (failNext) {
                throw new RuntimeException("DB 연결 실패");
            }
        }
    }
}
