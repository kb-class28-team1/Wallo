package com.wallo.spending.domain;

import java.math.BigDecimal;

/**
 * 소비분석 전체(카테고리 구분 없는) 지출 비교와 예산 사용률 계산 결과.
 *
 * <p>{@code expenseChangeRate}는 {@link SpendingComparisonStatus#COMPARABLE}일 때만 값이
 * 있고, 그 외에는 null이다. {@code budgetAmount}/{@code budgetUsageRate}는 예산이 없으면
 * (CUSTOM_RANGE 분석이거나 MONTHLY에서 예산 미설정) 둘 다 null이다.</p>
 */
public record SpendingOverallMetrics(
        long totalExpense,
        long transactionCount,
        long previousTotalExpense,
        BigDecimal expenseChangeRate,
        SpendingComparisonStatus expenseComparisonStatus,
        Long budgetAmount,
        BigDecimal budgetUsageRate
) {
}
