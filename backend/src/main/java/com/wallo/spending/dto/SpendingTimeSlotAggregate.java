package com.wallo.spending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지정 기간의 시간대별 유효 지출 금액과 거래 건수.
 *
 * <p>{@code timeSlot}은 {@code DAWN}/{@code MORNING}/{@code AFTERNOON}/{@code EVENING}
 * 중 하나다. 거래가 없는 시간대는 결과에 포함되지 않는다 — 누락 시간대를 0으로 채우는 처리는
 * Service 계층 책임이다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpendingTimeSlotAggregate {
    private String timeSlot;
    private long amount;
    private long transactionCount;
}
