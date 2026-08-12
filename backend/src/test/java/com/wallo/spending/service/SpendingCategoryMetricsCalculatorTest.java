package com.wallo.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.spending.domain.SpendingCategoryMetrics;
import com.wallo.spending.domain.SpendingComparisonStatus;
import com.wallo.spending.dto.SpendingCategoryAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpendingCategoryMetricsCalculatorTest {

    private final SpendingCategoryMetricsCalculator calculator = new SpendingCategoryMetricsCalculator();

    private SpendingCategoryAggregate aggregate(String category, long amount, long transactionCount) {
        return new SpendingCategoryAggregate(category, amount, transactionCount);
    }

    private SpendingCategoryMetrics findCategory(List<SpendingCategoryMetrics> result, String category) {
        return result.stream()
                .filter(metrics -> metrics.category().equals(category))
                .findFirst()
                .orElseThrow(() -> new AssertionError(category + " not found in result"));
    }

    // ---------- 병합 ----------

    @Test
    void mergesCategoryPresentInBothCurrentAndPrevious() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                1000L,
                List.of(aggregate("FOOD", 1000L, 3L)),
                List.of(aggregate("FOOD", 800L, 2L))
        );

        assertEquals(1, result.size());
        SpendingCategoryMetrics food = result.get(0);
        assertEquals("FOOD", food.category());
        assertEquals(1000L, food.amount());
        assertEquals(800L, food.previousAmount());
    }

    @Test
    void includesCategoryPresentOnlyInCurrent() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                1000L,
                List.of(aggregate("FOOD", 1000L, 3L)),
                List.of()
        );

        SpendingCategoryMetrics food = findCategory(result, "FOOD");
        assertEquals(1000L, food.amount());
        assertEquals(0L, food.previousAmount());
        assertEquals(SpendingComparisonStatus.NEW_SPENDING, food.comparisonStatus());
        assertNull(food.changeRate());
    }

    @Test
    void includesCategoryPresentOnlyInPrevious() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                0L,
                List.of(),
                List.of(aggregate("DELIVERY", 5000L, 2L))
        );

        SpendingCategoryMetrics delivery = findCategory(result, "DELIVERY");
        assertEquals(0L, delivery.amount());
        assertEquals(5000L, delivery.previousAmount());
        assertEquals(-5000L, delivery.changeAmount());
        assertEquals(SpendingComparisonStatus.COMPARABLE, delivery.comparisonStatus());
        assertEquals(new BigDecimal("-100.00"), delivery.changeRate());
        assertEquals(new BigDecimal("0.00"), delivery.ratio());
    }

    @Test
    void sortsResultsByCategoryAscending() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                4000L,
                List.of(
                        aggregate("FOOD", 1000L, 1L),
                        aggregate("CAFE", 2000L, 1L),
                        aggregate("DELIVERY", 1000L, 1L)
                ),
                List.of()
        );

        assertEquals(3, result.size());
        assertEquals("CAFE", result.get(0).category());
        assertEquals("DELIVERY", result.get(1).category());
        assertEquals("FOOD", result.get(2).category());
    }

    @Test
    void returnsEmptyListWhenBothListsAreEmpty() {
        List<SpendingCategoryMetrics> result = calculator.calculate(0L, List.of(), List.of());

        assertTrue(result.isEmpty());
    }

    // ---------- 비중 ----------

    @Test
    void computesNormalRatio() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                4000L,
                List.of(aggregate("FOOD", 1000L, 1L), aggregate("CAFE", 3000L, 1L)),
                List.of()
        );

        assertEquals(new BigDecimal("25.00"), findCategory(result, "FOOD").ratio());
    }

    @Test
    void roundsRatioHalfUpAtThirdDecimal() {
        // 2000 / 3000 * 100 = 66.6666...% -> HALF_UP -> 66.67
        List<SpendingCategoryMetrics> result = calculator.calculate(
                3000L,
                List.of(aggregate("FOOD", 2000L, 1L), aggregate("CAFE", 1000L, 1L)),
                List.of()
        );

        assertEquals(new BigDecimal("66.67"), findCategory(result, "FOOD").ratio());
    }

    @Test
    void returnsEmptyListWhenCurrentTotalExpenseZeroAndBothListsEmpty() {
        List<SpendingCategoryMetrics> result = calculator.calculate(0L, List.of(), List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void ratioIsZeroWhenCurrentTotalExpenseZeroAndCategoryOnlyInPrevious() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                0L,
                List.of(),
                List.of(aggregate("FOOD", 5000L, 1L))
        );

        assertEquals(new BigDecimal("0.00"), result.get(0).ratio());
    }

    @Test
    void rejectsWhenCurrentTotalExpenseZeroButCurrentAmountPositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0L, List.of(aggregate("FOOD", 1000L, 1L)), List.of())
        );
    }

    @Test
    void rejectsWhenCurrentCategorySumDoesNotMatchCurrentTotalExpense() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        5000L, List.of(aggregate("FOOD", 1000L, 1L)), List.of())
        );
    }

    // ---------- 증감 ----------

    @Test
    void computesPositiveChangeRate() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                1200L,
                List.of(aggregate("FOOD", 1200L, 1L)),
                List.of(aggregate("FOOD", 1000L, 1L))
        );

        SpendingCategoryMetrics food = result.get(0);
        assertEquals(200L, food.changeAmount());
        assertEquals(new BigDecimal("20.00"), food.changeRate());
        assertEquals(SpendingComparisonStatus.COMPARABLE, food.comparisonStatus());
    }

    @Test
    void computesNegativeChangeRate() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                800L,
                List.of(aggregate("FOOD", 800L, 1L)),
                List.of(aggregate("FOOD", 1000L, 1L))
        );

        assertEquals(new BigDecimal("-20.00"), result.get(0).changeRate());
    }

    @Test
    void changeRateZeroWhenAmountsEqual() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                1000L,
                List.of(aggregate("FOOD", 1000L, 1L)),
                List.of(aggregate("FOOD", 1000L, 1L))
        );

        assertEquals(new BigDecimal("0.00"), result.get(0).changeRate());
        assertEquals(0L, result.get(0).changeAmount());
    }

    @Test
    void changeRateIsMinus100WhenCategoryOnlyInPrevious() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                0L,
                List.of(),
                List.of(aggregate("FOOD", 4000L, 1L))
        );

        assertEquals(new BigDecimal("-100.00"), result.get(0).changeRate());
    }

    @Test
    void newSpendingWhenCategoryOnlyInCurrent() {
        List<SpendingCategoryMetrics> result = calculator.calculate(
                3000L,
                List.of(aggregate("FOOD", 3000L, 1L)),
                List.of()
        );

        SpendingCategoryMetrics food = result.get(0);
        assertEquals(SpendingComparisonStatus.NEW_SPENDING, food.comparisonStatus());
        assertNull(food.changeRate());
    }

    @Test
    void noSpendingWhenCurrentAndPreviousAmountsAreBothZero() {
        // 실제 Mapper 결과에서는 발생하지 않지만(GROUP BY는 항상 amount>0인 행만 반환),
        // 계산기 자체는 명시적으로 amount=0인 입력도 방어적으로 처리해야 한다.
        List<SpendingCategoryMetrics> result = calculator.calculate(
                0L,
                List.of(aggregate("FOOD", 0L, 0L)),
                List.of()
        );

        SpendingCategoryMetrics food = result.get(0);
        assertEquals(SpendingComparisonStatus.NO_SPENDING, food.comparisonStatus());
        assertNull(food.changeRate());
    }

    @Test
    void roundsChangeRateHalfUpAtThirdDecimal() {
        // (305000 - 300000) / 300000 * 100 = 1.6666...% -> HALF_UP -> 1.67
        List<SpendingCategoryMetrics> result = calculator.calculate(
                305_000L,
                List.of(aggregate("FOOD", 305_000L, 1L)),
                List.of(aggregate("FOOD", 300_000L, 1L))
        );

        assertEquals(new BigDecimal("1.67"), result.get(0).changeRate());
    }

    @Test
    void computesChangeRateForVeryLargeAmountsWithoutLongOverflow() {
        long current = Long.MAX_VALUE;
        long previous = 1L;

        List<SpendingCategoryMetrics> result = calculator.calculate(
                current,
                List.of(aggregate("FOOD", current, 1L)),
                List.of(aggregate("FOOD", previous, 1L))
        );

        BigDecimal expected = BigDecimal.valueOf(current)
                .subtract(BigDecimal.valueOf(previous))
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
        assertEquals(expected, result.get(0).changeRate());
    }

    // ---------- 입력 검증 ----------

    @Test
    void rejectsNullCurrentList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0L, null, List.of())
        );
    }

    @Test
    void rejectsNullPreviousList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0L, List.of(), null)
        );
    }

    @Test
    void rejectsNegativeCurrentTotalExpense() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(-1L, List.of(), List.of())
        );
    }

    @Test
    void rejectsNullCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0L, List.of(aggregate(null, 0L, 0L)), List.of())
        );
    }

    @Test
    void rejectsBlankCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0L, List.of(aggregate("   ", 0L, 0L)), List.of())
        );
    }

    @Test
    void rejectsDisallowedCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(1000L, List.of(aggregate("INCOME", 1000L, 1L)), List.of())
        );
    }

    @Test
    void rejectsDuplicateCategoryInCurrentList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        3000L,
                        List.of(aggregate("FOOD", 1000L, 1L), aggregate("FOOD", 2000L, 1L)),
                        List.of()
                )
        );
    }

    @Test
    void rejectsDuplicateCategoryInPreviousList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        0L,
                        List.of(),
                        List.of(aggregate("FOOD", 1000L, 1L), aggregate("FOOD", 2000L, 1L))
                )
        );
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0L, List.of(aggregate("FOOD", -1L, 0L)), List.of())
        );
    }

    @Test
    void rejectsNegativeTransactionCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(0L, List.of(aggregate("FOOD", 0L, -1L)), List.of())
        );
    }

    @Test
    void rejectsSumOverflow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(
                        Long.MAX_VALUE,
                        List.of(
                                aggregate("FOOD", Long.MAX_VALUE, 1L),
                                aggregate("CAFE", Long.MAX_VALUE, 1L)
                        ),
                        List.of()
                )
        );
    }
}
