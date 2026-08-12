package com.wallo.spending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지정 기간의 유효 지출 총액과 거래 건수.
 *
 * <p>공개 API 요청/응답 DTO({@link SpendingAnalysisDto})와는 별도로 둔다. 이 값은 Mapper가
 * 반환하는 원시 집계 결과이고, 비율·증감률 계산 등은 이후 Service 계층이 이 값을 입력으로 삼아
 * 수행한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpendingExpenseAggregate {
    private long totalExpense;
    private long transactionCount;
}
