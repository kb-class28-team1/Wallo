package com.wallo.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wallo.spending.domain.SpendingComparisonStatus;
import com.wallo.spending.domain.SpendingOverallMetrics;
import com.wallo.spending.dto.SpendingExpenseAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class SpendingMetricsCalculatorTest {

    private final SpendingMetricsCalculator calculator = new SpendingMetricsCalculator();

    private SpendingExpenseAggregate aggregate(long totalExpense, long transactionCount) {
        return new SpendingExpenseAggregate(totalExpense, transactionCount);
    }

    // ---------- 전체 지출 비교 ----------

    @Test
    void comparableWhenCurrentIsHigherThanPrevious() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(120_000L, 5L), aggregate(100_000L, 4L), null);

        assertEquals(new BigDecimal("20.00"), metrics.expenseChangeRate());
        assertEquals(SpendingComparisonStatus.COMPARABLE, metrics.expenseComparisonStatus());
    }

    @Test
    void comparableWhenCurrentIsLowerThanPrevious() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(80_000L, 3L), aggregate(100_000L, 4L), null);

        assertEquals(new BigDecimal("-20.00"), metrics.expenseChangeRate());
        assertEquals(SpendingComparisonStatus.COMPARABLE, metrics.expenseComparisonStatus());
    }

    @Test
    void comparableWhenCurrentEqualsPrevious() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(100_000L, 4L), aggregate(100_000L, 4L), null);

        assertEquals(new BigDecimal("0.00"), metrics.expenseChangeRate());
        assertEquals(SpendingComparisonStatus.COMPARABLE, metrics.expenseComparisonStatus());
    }

    @Test
    void comparableWhenCurrentIsZeroAndPreviousIsPositive() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(0L, 0L), aggregate(100_000L, 4L), null);

        assertEquals(new BigDecimal("-100.00"), metrics.expenseChangeRate());
        assertEquals(SpendingComparisonStatus.COMPARABLE, metrics.expenseComparisonStatus());
    }

    @Test
    void newSpendingWhenPreviousIsZeroAndCurrentIsPositive() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(50_000L, 2L), aggregate(0L, 0L), null);

        assertNull(metrics.expenseChangeRate());
        assertEquals(SpendingComparisonStatus.NEW_SPENDING, metrics.expenseComparisonStatus());
    }

    @Test
    void noSpendingWhenCurrentAndPreviousAreBothZero() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(0L, 0L), aggregate(0L, 0L), null);

        assertNull(metrics.expenseChangeRate());
        assertEquals(SpendingComparisonStatus.NO_SPENDING, metrics.expenseComparisonStatus());
    }

    @Test
    void roundsChangeRateHalfUpWhenThirdDecimalRoundsUp() {
        // (305000 - 300000) / 300000 * 100 = 1.6666...% -> HALF_UP -> 1.67
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(305_000L, 10L), aggregate(300_000L, 10L), null);

        assertEquals(new BigDecimal("1.67"), metrics.expenseChangeRate());
    }

    @Test
    void computesChangeRateForVeryLargeAmountsWithoutLongOverflow() {
        long current = Long.MAX_VALUE;
        long previous = 1L;

        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(current, 1L), aggregate(previous, 1L), null);

        BigDecimal expected = BigDecimal.valueOf(current)
                .subtract(BigDecimal.valueOf(previous))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
        assertEquals(expected, metrics.expenseChangeRate());
        assertEquals(SpendingComparisonStatus.COMPARABLE, metrics.expenseComparisonStatus());
    }

    // ---------- 예산 사용률 ----------

    @Test
    void computesBudgetUsageRateBelowBudget() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(250_000L, 5L), aggregate(0L, 0L), 500_000L);

        assertEquals(500_000L, metrics.budgetAmount());
        assertEquals(new BigDecimal("50.00"), metrics.budgetUsageRate());
    }

    @Test
    void computesBudgetUsageRateAboveBudget() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(600_000L, 8L), aggregate(0L, 0L), 500_000L);

        assertEquals(new BigDecimal("120.00"), metrics.budgetUsageRate());
    }

    @Test
    void computesZeroBudgetUsageRateWhenNoExpense() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(0L, 0L), aggregate(0L, 0L), 500_000L);

        assertEquals(new BigDecimal("0.00"), metrics.budgetUsageRate());
    }

    @Test
    void budgetAmountAndUsageRateAreBothNullWhenBudgetIsNull() {
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(100_000L, 3L), aggregate(0L, 0L), null);

        assertNull(metrics.budgetAmount());
        assertNull(metrics.budgetUsageRate());
    }

    @Test
    void rejectsZeroBudget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(aggregate(100_000L, 3L), aggregate(0L, 0L), 0L)
        );
    }

    @Test
    void rejectsNegativeBudget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(aggregate(100_000L, 3L), aggregate(0L, 0L), -1L)
        );
    }

    @Test
    void roundsBudgetUsageRateHalfUpWhenThirdDecimalRoundsUp() {
        // 200000 / 300000 * 100 = 66.6666...% -> HALF_UP -> 66.67
        SpendingOverallMetrics metrics = calculator.calculate(
                aggregate(200_000L, 5L), aggregate(0L, 0L), 300_000L);

        assertEquals(new BigDecimal("66.67"), metrics.budgetUsageRate());
    }

    // ---------- 입력값 방어 ----------

    @Test
    void rejectsNullCurrentAggregate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(null, aggregate(0L, 0L), null)
        );
    }

    @Test
    void rejectsNullPreviousAggregate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(aggregate(0L, 0L), null, null)
        );
    }

    @Test
    void rejectsNegativeCurrentTotalExpense() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(aggregate(-1L, 0L), aggregate(0L, 0L), null)
        );
    }

    @Test
    void rejectsNegativePreviousTotalExpense() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(aggregate(0L, 0L), aggregate(-1L, 0L), null)
        );
    }

    @Test
    void rejectsNegativeCurrentTransactionCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(aggregate(0L, -1L), aggregate(0L, 0L), null)
        );
    }

    @Test
    void rejectsNegativePreviousTransactionCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(aggregate(0L, 0L), aggregate(0L, -1L), null)
        );
    }
}
