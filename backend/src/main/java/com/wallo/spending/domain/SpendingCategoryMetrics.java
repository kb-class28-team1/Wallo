package com.wallo.spending.domain;

import java.math.BigDecimal;

/**
 * 카테고리 하나의 현재·이전 기간 병합 결과.
 *
 * <p>현재 또는 이전 기간 중 하나 이상에 존재했던 카테고리만 결과로 만들어진다(양쪽 모두
 * 없는 카테고리는 0원 행으로 채우지 않음). {@code changeRate}는
 * {@link SpendingComparisonStatus#COMPARABLE}일 때만 값이 있다. 이 구조는 이후
 * {@code SPENDING_ANALYSES.category_breakdown} JSON 저장의 기반이 된다(표시용 한글명,
 * AI 설명, signalType, 예상 절감액 등은 포함하지 않음).</p>
 */
public record SpendingCategoryMetrics(
        String category,
        long amount,
        BigDecimal ratio,
        long previousAmount,
        long changeAmount,
        BigDecimal changeRate,
        SpendingComparisonStatus comparisonStatus
) {
}
