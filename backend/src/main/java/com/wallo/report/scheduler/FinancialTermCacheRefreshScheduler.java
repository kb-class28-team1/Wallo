package com.wallo.report.scheduler;

import com.wallo.report.term.service.FinancialTermMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * financial_term 매칭 캐시({@link FinancialTermMatchingService})를 주기적으로 다시 만든다.
 *
 * <p>{@code FinancialTermMatchingServiceImpl}은 성능을 위해 financial_term 전체를 메모리에 캐싱하는데,
 * 캐시를 다시 만들어주는 트리거가 없으면 서버 기동 뒤 financial_term이 새로 적재되거나 바뀌어도
 * 애플리케이션을 재시작하기 전까지는 반영되지 않는다(운영 중인 서버를 그때마다 재시작할 수는 없다).
 * 이 스케줄러가 일정 주기로 {@link FinancialTermMatchingService#refreshCache()}를 호출해, 재시작 없이도
 * 캐시가 오래돼봐야 이 주기만큼만 뒤처지도록 보장한다.
 *
 * <p>financial_term은 사람이 드물게(수동으로) 적재·수정하는 참조 데이터라 실시간 반영이 필요하지 않아,
 * 뉴스 크롤링/리포트 생성 스케줄러처럼 특정 시각에 맞출 필요 없이 단순 고정 주기로 충분하다고 판단했다.
 */
@Component
public class FinancialTermCacheRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(FinancialTermCacheRefreshScheduler.class);

    private final FinancialTermMatchingService financialTermMatchingService;

    public FinancialTermCacheRefreshScheduler(FinancialTermMatchingService financialTermMatchingService) {
        this.financialTermMatchingService = financialTermMatchingService;
    }

    @Scheduled(
            initialDelayString = "${financial-term.cache.refresh-interval-ms:3600000}",
            fixedRateString = "${financial-term.cache.refresh-interval-ms:3600000}"
    )
    public void refreshCache() {
        try {
            financialTermMatchingService.refreshCache();
            log.info("financial_term 매칭 캐시를 갱신했습니다.");
        } catch (RuntimeException exception) {
            // 이번 주기 갱신만 실패할 뿐, 기존 캐시는 그대로 유지되어 매칭 자체가 멈추지는 않는다.
            // 원인(대개 DB 연결 문제)을 다음 주기에 다시 시도하기 전에 남겨 둔다.
            log.error("financial_term 매칭 캐시 갱신에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
        }
    }
}
