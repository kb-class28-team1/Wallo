package com.wallo.spending.domain;

/**
 * 시간대 하나의 정규화된 지출 금액과 거래 건수.
 *
 * <p>이 구조는 이후 {@code SPENDING_ANALYSES.time_slot_breakdown} JSON 저장의 기반이 된다.</p>
 */
public record SpendingTimeSlotMetrics(
        String timeSlot,
        long amount,
        long transactionCount
) {
}
