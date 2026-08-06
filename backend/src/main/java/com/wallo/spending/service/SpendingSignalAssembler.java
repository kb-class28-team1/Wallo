package com.wallo.spending.service;

import com.wallo.spending.domain.SpendingAnalysisSignal;
import com.wallo.spending.domain.SpendingExpenseCategory;
import com.wallo.spending.domain.SpendingSignalType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@link SpendingBudgetSignalDetector}와 {@link SpendingCategorySurgeSignalDetector}가
 * 각각 판정한 신호를 하나의 최종 목록으로 합치는 순수 계산 객체.
 *
 * <p>DB를 직접 조회하지 않으며, 두 Detector가 이미 계산·정렬한 값을 다시 계산하거나
 * 재정렬하지 않는다 — 예산 신호가 있으면 항상 맨 앞에 배치하고, 카테고리 급증 신호는
 * {@code SpendingCategorySurgeSignalDetector}가 반환한 순서를 그대로 유지한다. 이 클래스는
 * 두 입력의 형태(shape)가 각 Detector의 계약을 만족하는지 방어적으로 검증할 뿐이다.</p>
 */
@Component
public class SpendingSignalAssembler {

    private static final int MAX_CATEGORY_SURGE_SIGNALS = 3;
    private static final BigDecimal BUDGET_USAGE_THRESHOLD = BigDecimal.valueOf(100);
    private static final BigDecimal CATEGORY_SURGE_THRESHOLD = BigDecimal.valueOf(30);

    public List<SpendingAnalysisSignal> assemble(
            Optional<SpendingAnalysisSignal> budgetSignal,
            List<SpendingAnalysisSignal> categorySurgeSignals
    ) {
        if (budgetSignal == null) {
            throw new IllegalArgumentException("budgetSignal Optional 값이 필요합니다.");
        }
        if (categorySurgeSignals == null) {
            throw new IllegalArgumentException("categorySurgeSignals 목록이 필요합니다.");
        }
        if (categorySurgeSignals.size() > MAX_CATEGORY_SURGE_SIGNALS) {
            throw new IllegalArgumentException(
                    "categorySurgeSignals는 최대 " + MAX_CATEGORY_SURGE_SIGNALS + "개까지 허용됩니다: "
                            + categorySurgeSignals.size());
        }

        budgetSignal.ifPresent(this::validateBudgetSignal);

        Set<String> seenCategories = new HashSet<>();
        for (SpendingAnalysisSignal signal : categorySurgeSignals) {
            if (signal == null) {
                throw new IllegalArgumentException("categorySurgeSignals 목록에 null 항목이 있습니다.");
            }
            String category = validateCategorySurgeSignal(signal);
            if (!seenCategories.add(category)) {
                throw new IllegalArgumentException(
                        "categorySurgeSignals 목록에 중복된 카테고리가 있습니다: " + category);
            }
        }

        List<SpendingAnalysisSignal> result = new ArrayList<>(1 + categorySurgeSignals.size());
        budgetSignal.ifPresent(result::add);
        result.addAll(categorySurgeSignals);
        return List.copyOf(result);
    }

    private void validateBudgetSignal(SpendingAnalysisSignal signal) {
        if (signal.signalType() != SpendingSignalType.BUDGET_EXCEEDED) {
            throw new IllegalArgumentException(
                    "예산 신호의 signalType이 BUDGET_EXCEEDED가 아닙니다: " + signal.signalType());
        }
        if (signal.targetCategory() != null) {
            throw new IllegalArgumentException("예산 신호의 targetCategory는 null이어야 합니다.");
        }
        if (signal.currentAmount() != null) {
            throw new IllegalArgumentException("예산 신호의 currentAmount는 null이어야 합니다.");
        }
        if (signal.previousAmount() != null) {
            throw new IllegalArgumentException("예산 신호의 previousAmount는 null이어야 합니다.");
        }
        if (signal.changeAmount() != null) {
            throw new IllegalArgumentException("예산 신호의 changeAmount는 null이어야 합니다.");
        }
        if (signal.changeRate() != null) {
            throw new IllegalArgumentException("예산 신호의 changeRate는 null이어야 합니다.");
        }
        if (signal.budgetAmount() == null) {
            throw new IllegalArgumentException("예산 신호의 budgetAmount가 필요합니다.");
        }
        if (signal.budgetAmount() <= 0) {
            throw new IllegalArgumentException("예산 신호의 budgetAmount는 0보다 커야 합니다: " + signal.budgetAmount());
        }
        if (signal.budgetUsageRate() == null) {
            throw new IllegalArgumentException("예산 신호의 budgetUsageRate가 필요합니다.");
        }
        if (signal.budgetUsageRate().compareTo(BUDGET_USAGE_THRESHOLD) <= 0) {
            throw new IllegalArgumentException(
                    "예산 신호의 budgetUsageRate는 100보다 커야 합니다: " + signal.budgetUsageRate());
        }
    }

    private String validateCategorySurgeSignal(SpendingAnalysisSignal signal) {
        if (signal.signalType() != SpendingSignalType.CATEGORY_SURGE) {
            throw new IllegalArgumentException(
                    "카테고리 신호의 signalType이 CATEGORY_SURGE가 아닙니다: " + signal.signalType());
        }
        String category = validateCategory(signal.targetCategory());

        if (signal.currentAmount() == null) {
            throw new IllegalArgumentException(category + " 신호의 currentAmount가 필요합니다.");
        }
        if (signal.currentAmount() < 0) {
            throw new IllegalArgumentException(
                    category + " 신호의 currentAmount는 음수가 될 수 없습니다: " + signal.currentAmount());
        }
        if (signal.previousAmount() == null) {
            throw new IllegalArgumentException(category + " 신호의 previousAmount가 필요합니다.");
        }
        if (signal.previousAmount() <= 0) {
            throw new IllegalArgumentException(
                    category + " 신호의 previousAmount는 0보다 커야 합니다: " + signal.previousAmount());
        }
        if (signal.currentAmount() <= signal.previousAmount()) {
            throw new IllegalArgumentException(
                    category + " 신호의 currentAmount는 previousAmount보다 커야 합니다.");
        }

        if (signal.changeAmount() == null) {
            throw new IllegalArgumentException(category + " 신호의 changeAmount가 필요합니다.");
        }
        if (signal.changeAmount() <= 0) {
            throw new IllegalArgumentException(
                    category + " 신호의 changeAmount는 0보다 커야 합니다: " + signal.changeAmount());
        }
        long expectedChangeAmount = Math.subtractExact(signal.currentAmount(), signal.previousAmount());
        if (signal.changeAmount() != expectedChangeAmount) {
            throw new IllegalArgumentException(
                    category + " 신호의 changeAmount가 currentAmount-previousAmount와 일치하지 않습니다: "
                            + signal.changeAmount() + " != " + expectedChangeAmount);
        }

        if (signal.changeRate() == null) {
            throw new IllegalArgumentException(category + " 신호의 changeRate가 필요합니다.");
        }
        if (signal.changeRate().compareTo(CATEGORY_SURGE_THRESHOLD) < 0) {
            throw new IllegalArgumentException(
                    category + " 신호의 changeRate는 30 이상이어야 합니다: " + signal.changeRate());
        }

        if (signal.budgetAmount() != null) {
            throw new IllegalArgumentException(category + " 신호의 budgetAmount는 null이어야 합니다.");
        }
        if (signal.budgetUsageRate() != null) {
            throw new IllegalArgumentException(category + " 신호의 budgetUsageRate는 null이어야 합니다.");
        }

        return category;
    }

    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("targetCategory 값이 필요합니다.");
        }
        try {
            return SpendingExpenseCategory.valueOf(category).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("허용되지 않은 카테고리입니다: " + category, exception);
        }
    }
}
