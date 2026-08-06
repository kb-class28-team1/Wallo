package com.wallo.spending.service;

import com.wallo.spending.domain.SpendingCategoryMetrics;
import com.wallo.spending.domain.SpendingComparisonStatus;
import com.wallo.spending.domain.SpendingExpenseCategory;
import com.wallo.spending.dto.SpendingCategoryAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 카테고리별 현재·이전 기간 집계를 병합해 비중과 증감 지표를 계산하는 순수 계산 객체.
 *
 * <p>DB를 직접 조회하지 않는다 — {@link SpendingCategoryAggregate}(기존
 * {@code SpendingAnalysisMapper.selectCategoryAggregates}의 결과 DTO) 목록을 orchestration
 * Service가 조회해 전달하면, 이 클래스는 그 값만으로 계산한다.</p>
 *
 * <p>증감률 계산식은 {@link SpendingMetricsCalculator}와 동일하지만, 6줄 남짓의 계산식 하나를
 * 공유하기 위해 이미 커밋된 그 클래스를 리팩터링하는 대신 의도적으로 그대로 중복 구현했다
 * (지시된 "이번 커밋 범위를 크게 키우는 리팩터링 금지" 원칙에 따름).</p>
 */
@Component
public class SpendingCategoryMetricsCalculator {

    private static final int RATE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO_RATE = BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);

    public List<SpendingCategoryMetrics> calculate(
            long currentTotalExpense,
            List<SpendingCategoryAggregate> current,
            List<SpendingCategoryAggregate> previous
    ) {
        if (current == null) {
            throw new IllegalArgumentException("현재 기간 카테고리 집계 목록이 필요합니다.");
        }
        if (previous == null) {
            throw new IllegalArgumentException("이전 기간 카테고리 집계 목록이 필요합니다.");
        }
        if (currentTotalExpense < 0) {
            throw new IllegalArgumentException(
                    "현재 기간 총지출은 음수가 될 수 없습니다: " + currentTotalExpense);
        }

        Map<String, SpendingCategoryAggregate> currentByCategory = indexAndValidate(current, "current");
        Map<String, SpendingCategoryAggregate> previousByCategory = indexAndValidate(previous, "previous");

        long currentSum = sumAmounts(currentByCategory);
        if (currentSum != currentTotalExpense) {
            throw new IllegalArgumentException(
                    "current 카테고리 금액 합계(" + currentSum
                            + ")가 currentTotalExpense(" + currentTotalExpense + ")와 일치하지 않습니다.");
        }

        TreeSet<String> categories = new TreeSet<>();
        categories.addAll(currentByCategory.keySet());
        categories.addAll(previousByCategory.keySet());

        List<SpendingCategoryMetrics> result = new ArrayList<>(categories.size());
        for (String category : categories) {
            long amount = amountOf(currentByCategory, category);
            long previousAmount = amountOf(previousByCategory, category);

            BigDecimal ratio = currentTotalExpense > 0
                    ? BigDecimal.valueOf(amount)
                            .multiply(ONE_HUNDRED)
                            .divide(BigDecimal.valueOf(currentTotalExpense), RATE_SCALE, RoundingMode.HALF_UP)
                    : ZERO_RATE;

            long changeAmount = Math.subtractExact(amount, previousAmount);

            SpendingComparisonStatus comparisonStatus = previousAmount > 0
                    ? SpendingComparisonStatus.COMPARABLE
                    : (amount > 0 ? SpendingComparisonStatus.NEW_SPENDING : SpendingComparisonStatus.NO_SPENDING);
            BigDecimal changeRate = comparisonStatus == SpendingComparisonStatus.COMPARABLE
                    ? changeRate(amount, previousAmount)
                    : null;

            result.add(new SpendingCategoryMetrics(
                    category, amount, ratio, previousAmount, changeAmount, changeRate, comparisonStatus));
        }

        return List.copyOf(result);
    }

    private Map<String, SpendingCategoryAggregate> indexAndValidate(
            List<SpendingCategoryAggregate> aggregates, String listName
    ) {
        Map<String, SpendingCategoryAggregate> byCategory = new LinkedHashMap<>();
        for (SpendingCategoryAggregate aggregate : aggregates) {
            if (aggregate == null) {
                throw new IllegalArgumentException(listName + " 목록에 null 항목이 있습니다.");
            }
            String category = validateCategory(aggregate.getCategory());
            if (aggregate.getAmount() < 0) {
                throw new IllegalArgumentException(
                        category + " 카테고리 금액은 음수가 될 수 없습니다: " + aggregate.getAmount());
            }
            if (aggregate.getTransactionCount() < 0) {
                throw new IllegalArgumentException(
                        category + " 카테고리 거래 건수는 음수가 될 수 없습니다: "
                                + aggregate.getTransactionCount());
            }
            if (byCategory.put(category, aggregate) != null) {
                throw new IllegalArgumentException(
                        listName + " 목록에 중복된 카테고리가 있습니다: " + category);
            }
        }
        return byCategory;
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

    private long sumAmounts(Map<String, SpendingCategoryAggregate> byCategory) {
        long sum = 0L;
        for (SpendingCategoryAggregate aggregate : byCategory.values()) {
            try {
                sum = Math.addExact(sum, aggregate.getAmount());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "카테고리 금액 합계 계산 중 오버플로가 발생했습니다.", exception);
            }
        }
        return sum;
    }

    private long amountOf(Map<String, SpendingCategoryAggregate> byCategory, String category) {
        SpendingCategoryAggregate aggregate = byCategory.get(category);
        return aggregate == null ? 0L : aggregate.getAmount();
    }

    /** overflow 방지를 위해 long끼리 먼저 연산하지 않고 BigDecimal로 변환한 뒤 계산한다. */
    private BigDecimal changeRate(long amount, long previousAmount) {
        BigDecimal current = BigDecimal.valueOf(amount);
        BigDecimal previous = BigDecimal.valueOf(previousAmount);
        return current.subtract(previous)
                .multiply(ONE_HUNDRED)
                .divide(previous, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
