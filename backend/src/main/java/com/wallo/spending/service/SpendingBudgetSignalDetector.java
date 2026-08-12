package com.wallo.spending.service;

import com.wallo.spending.domain.SpendingAnalysisSignal;
import com.wallo.spending.domain.SpendingOverallMetrics;
import com.wallo.spending.domain.SpendingSignalType;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 예산 초과({@code BUDGET_EXCEEDED}) 신호 하나를 판정하는 순수 계산 객체.
 *
 * <p>{@code CATEGORY_SURGE} 판정은 이번 단계에서 다루지 않는다. 단일 예산 신호만 판정하므로
 * 빈 {@code List} 대신 {@link Optional}을 반환한다({@code ExpenseCategoryRule.classify()}가
 * 단일 조건 판정 결과를 {@code Optional}로 반환하는 기존 관례를 따름). 여러 신호를 하나의
 * 목록으로 합치는 정렬·개수 제한은 이후 orchestration 단계의 책임이며, 이 클래스는 다루지
 * 않는다.</p>
 */
@Component
public class SpendingBudgetSignalDetector {

    private static final BigDecimal BUDGET_EXCEEDED_THRESHOLD = BigDecimal.valueOf(100);

    public Optional<SpendingAnalysisSignal> detect(SpendingOverallMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("소비분석 전체 지표 값이 필요합니다.");
        }
        validateBudgetInvariant(metrics.budgetAmount(), metrics.budgetUsageRate());

        if (metrics.budgetAmount() == null) {
            return Optional.empty();
        }
        if (metrics.budgetUsageRate().compareTo(BUDGET_EXCEEDED_THRESHOLD) <= 0) {
            return Optional.empty();
        }

        return Optional.of(new SpendingAnalysisSignal(
                SpendingSignalType.BUDGET_EXCEEDED,
                null,
                null,
                null,
                null,
                null,
                metrics.budgetAmount(),
                metrics.budgetUsageRate()
        ));
    }

    private void validateBudgetInvariant(Long budgetAmount, BigDecimal budgetUsageRate) {
        if (budgetAmount == null && budgetUsageRate == null) {
            return;
        }
        if (budgetAmount == null) {
            throw new IllegalArgumentException(
                    "budgetAmount가 null이면 budgetUsageRate도 null이어야 합니다.");
        }
        if (budgetUsageRate == null) {
            throw new IllegalArgumentException(
                    "budgetUsageRate가 null이면 budgetAmount도 null이어야 합니다.");
        }
        if (budgetAmount <= 0) {
            throw new IllegalArgumentException("budgetAmount는 0보다 커야 합니다: " + budgetAmount);
        }
        if (budgetUsageRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("budgetUsageRate는 음수가 될 수 없습니다: " + budgetUsageRate);
        }
    }
}
