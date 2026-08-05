package com.wallo.spending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지정 기간의 카테고리별 유효 지출 금액과 거래 건수.
 *
 * <p>{@link SpendingExpenseAggregate}와 마찬가지로 Mapper가 반환하는 원시 집계 결과이며,
 * 비중(ratio)·이전 기간 대비 증감·존재하지 않는 카테고리를 0으로 채우는 처리는 이 DTO의
 * 책임이 아니라 이후 Service 계층이 이 값을 입력으로 삼아 수행한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpendingCategoryAggregate {
    private String category;
    private long amount;
    private long transactionCount;
}
