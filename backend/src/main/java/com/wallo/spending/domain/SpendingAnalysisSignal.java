package com.wallo.spending.domain;

import java.math.BigDecimal;

/**
 * {@code SPENDING_ANALYSES.signals} JSON 배열의 항목 하나.
 *
 * <p>신호 유형에 따라 필요한 필드만 채워지고 나머지는 null이다(예: {@code BUDGET_EXCEEDED}는
 * {@code budgetAmount}/{@code budgetUsageRate}만 값이 있고 나머지는 null). 판정 근거를
 * 재현할 수 있는 코드와 수치만 담으며, 표시용 한국어 문장이나 AI 설명은 포함하지 않는다.</p>
 */
public record SpendingAnalysisSignal(
        SpendingSignalType signalType,
        String targetCategory,
        Long currentAmount,
        Long previousAmount,
        Long changeAmount,
        BigDecimal changeRate,
        Long budgetAmount,
        BigDecimal budgetUsageRate
) {
}
