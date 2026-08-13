package com.wallo.spending.domain;

/**
 * 요일 하나의 정규화된 지출 금액과 거래 건수.
 *
 * <p>이 구조는 이후 {@code SPENDING_ANALYSES.weekday_breakdown} JSON 저장의 기반이 된다.</p>
 */
public record SpendingWeekdayMetrics(
        String weekday,
        long amount,
        long transactionCount
) {
}
