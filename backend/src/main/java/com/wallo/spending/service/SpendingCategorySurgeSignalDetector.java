package com.wallo.spending.service;

import com.wallo.spending.domain.SpendingAnalysisSignal;
import com.wallo.spending.domain.SpendingCategoryMetrics;
import com.wallo.spending.domain.SpendingComparisonStatus;
import com.wallo.spending.domain.SpendingExpenseCategory;
import com.wallo.spending.domain.SpendingSignalType;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 카테고리별 지출 급증({@code CATEGORY_SURGE}) 신호를 판정하는 순수 계산 객체.
 *
 * <p>DB를 직접 조회하지 않는다 — {@link SpendingCategoryMetrics}(기존
 * {@code SpendingCategoryMetricsCalculator}의 계산 결과) 목록을 orchestration Service가
 * 전달하면, 이 클래스는 그 값만으로 후보를 걸러내고 정렬해 최대 3개까지 반환한다.
 * {@code changeRate} 공식을 다시 계산하지 않고, 입력에 이미 계산된 값을 그대로 신뢰해
 * 신호에 전달한다(공식 계산 책임은 {@code SpendingCategoryMetricsCalculator}에 있음).</p>
 *
 * <p>{@code BUDGET_EXCEEDED}와의 병합, 전체 신호 정렬은 이 클래스의 책임이 아니며 이후
 * orchestration 단계에서 처리한다.</p>
 */
@Component
public class SpendingCategorySurgeSignalDetector {

    private static final BigDecimal SURGE_THRESHOLD = BigDecimal.valueOf(30);
    private static final int MAX_SIGNALS = 3;

    private static final Comparator<SpendingCategoryMetrics> SURGE_ORDER = Comparator
            .comparingLong(SpendingCategoryMetrics::changeAmount).reversed()
            .thenComparing(Comparator.comparing(SpendingCategoryMetrics::changeRate).reversed())
            .thenComparing(SpendingCategoryMetrics::category);

    public List<SpendingAnalysisSignal> detect(List<SpendingCategoryMetrics> categoryMetrics) {
        if (categoryMetrics == null) {
            throw new IllegalArgumentException("카테고리 지표 목록이 필요합니다.");
        }
        validate(categoryMetrics);

        return categoryMetrics.stream()
                .filter(this::isSurgeCandidate)
                .sorted(SURGE_ORDER)
                .limit(MAX_SIGNALS)
                .map(this::toSignal)
                .toList();
    }

    private boolean isSurgeCandidate(SpendingCategoryMetrics metrics) {
        if (metrics.comparisonStatus() != SpendingComparisonStatus.COMPARABLE) {
            return false;
        }
        if (metrics.changeRate() == null) {
            return false;
        }
        if (metrics.changeRate().compareTo(SURGE_THRESHOLD) < 0) {
            return false;
        }
        if (metrics.changeAmount() <= 0) {
            return false;
        }
        return metrics.amount() > metrics.previousAmount();
    }

    private SpendingAnalysisSignal toSignal(SpendingCategoryMetrics metrics) {
        return new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE,
                metrics.category(),
                metrics.amount(),
                metrics.previousAmount(),
                metrics.changeAmount(),
                metrics.changeRate(),
                null,
                null
        );
    }

    private void validate(List<SpendingCategoryMetrics> categoryMetrics) {
        Set<String> seen = new HashSet<>();
        for (SpendingCategoryMetrics metrics : categoryMetrics) {
            if (metrics == null) {
                throw new IllegalArgumentException("카테고리 지표 목록에 null 항목이 있습니다.");
            }
            String category = validateCategory(metrics.category());
            if (!seen.add(category)) {
                throw new IllegalArgumentException(
                        "카테고리 지표 목록에 중복된 카테고리가 있습니다: " + category);
            }
            if (metrics.amount() < 0) {
                throw new IllegalArgumentException(
                        category + " 카테고리 금액은 음수가 될 수 없습니다: " + metrics.amount());
            }
            if (metrics.previousAmount() < 0) {
                throw new IllegalArgumentException(
                        category + " 카테고리 이전 금액은 음수가 될 수 없습니다: " + metrics.previousAmount());
            }
            long expectedChangeAmount = Math.subtractExact(metrics.amount(), metrics.previousAmount());
            if (metrics.changeAmount() != expectedChangeAmount) {
                throw new IllegalArgumentException(
                        category + " 카테고리 changeAmount가 amount-previousAmount와 일치하지 않습니다: "
                                + metrics.changeAmount() + " != " + expectedChangeAmount);
            }
            if (metrics.comparisonStatus() == null) {
                throw new IllegalArgumentException(category + " 카테고리 comparisonStatus가 필요합니다.");
            }
            validateComparisonStatusInvariant(category, metrics);
            if (metrics.ratio() == null) {
                throw new IllegalArgumentException(category + " 카테고리 ratio 값이 필요합니다.");
            }
            if (metrics.ratio().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        category + " 카테고리 ratio는 음수가 될 수 없습니다: " + metrics.ratio());
            }
        }
    }

    private void validateComparisonStatusInvariant(String category, SpendingCategoryMetrics metrics) {
        switch (metrics.comparisonStatus()) {
            case COMPARABLE -> {
                if (metrics.previousAmount() <= 0) {
                    throw new IllegalArgumentException(
                            category + " 카테고리는 COMPARABLE인데 previousAmount가 0 이하입니다: "
                                    + metrics.previousAmount());
                }
                if (metrics.changeRate() == null) {
                    throw new IllegalArgumentException(
                            category + " 카테고리는 COMPARABLE인데 changeRate가 null입니다.");
                }
            }
            case NEW_SPENDING -> {
                if (metrics.previousAmount() != 0) {
                    throw new IllegalArgumentException(
                            category + " 카테고리는 NEW_SPENDING인데 previousAmount가 0이 아닙니다: "
                                    + metrics.previousAmount());
                }
                if (metrics.amount() <= 0) {
                    throw new IllegalArgumentException(
                            category + " 카테고리는 NEW_SPENDING인데 amount가 0 이하입니다: " + metrics.amount());
                }
                if (metrics.changeRate() != null) {
                    throw new IllegalArgumentException(
                            category + " 카테고리는 NEW_SPENDING인데 changeRate가 존재합니다: "
                                    + metrics.changeRate());
                }
            }
            case NO_SPENDING -> {
                if (metrics.previousAmount() != 0 || metrics.amount() != 0) {
                    throw new IllegalArgumentException(
                            category + " 카테고리는 NO_SPENDING인데 금액이 0이 아닙니다(amount="
                                    + metrics.amount() + ", previousAmount=" + metrics.previousAmount() + ").");
                }
                if (metrics.changeRate() != null) {
                    throw new IllegalArgumentException(
                            category + " 카테고리는 NO_SPENDING인데 changeRate가 존재합니다: "
                                    + metrics.changeRate());
                }
            }
        }
    }

    private String validateCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("카테고리 값이 필요합니다.");
        }
        try {
            return SpendingExpenseCategory.valueOf(category).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("허용되지 않은 카테고리입니다: " + category, exception);
        }
    }
}
