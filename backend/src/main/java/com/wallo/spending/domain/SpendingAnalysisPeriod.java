package com.wallo.spending.domain;

import java.time.LocalDate;

/**
 * 소비분석 요청 검증을 통과한 뒤 계산된 실제 분석 기간과 비교 기간.
 *
 * <p>{@code targetMonth}는 {@link SpendingAnalysisType#MONTHLY}일 때만 값이 있고
 * {@link SpendingAnalysisType#CUSTOM_RANGE}일 때는 null이다.</p>
 */
public record SpendingAnalysisPeriod(
        SpendingAnalysisType analysisType,
        String targetMonth,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate comparisonPeriodStart,
        LocalDate comparisonPeriodEnd,
        boolean forceReanalyze
) {
}
