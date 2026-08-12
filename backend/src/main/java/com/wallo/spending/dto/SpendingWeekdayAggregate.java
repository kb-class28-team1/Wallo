package com.wallo.spending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지정 기간의 요일별 유효 지출 금액과 거래 건수.
 *
 * <p>{@code weekday}는 {@code MONDAY}~{@code SUNDAY} 영문 코드다. 거래가 없는 요일은
 * 결과에 포함되지 않는다 — 누락 요일을 0으로 채우는 처리는 Service 계층 책임이다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpendingWeekdayAggregate {
    private String weekday;
    private long amount;
    private long transactionCount;
}
