package com.wallo.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.spending.domain.SpendingAnalysisSignal;
import com.wallo.spending.domain.SpendingSignalType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpendingSignalAssemblerTest {

    private final SpendingSignalAssembler assembler = new SpendingSignalAssembler();

    private SpendingAnalysisSignal budgetSignal(long budgetAmount, BigDecimal usageRate) {
        return new SpendingAnalysisSignal(
                SpendingSignalType.BUDGET_EXCEEDED, null, null, null, null, null, budgetAmount, usageRate);
    }

    private SpendingAnalysisSignal categorySignal(String category, long current, long previous, BigDecimal changeRate) {
        return new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, category, current, previous,
                current - previous, changeRate, null, null);
    }

    // ---------- 정상 조립 ----------

    @Test
    void returnsEmptyListWhenBothAbsent() {
        List<SpendingAnalysisSignal> result = assembler.assemble(Optional.empty(), List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsBudgetSignalOnly() {
        SpendingAnalysisSignal budget = budgetSignal(500_000L, BigDecimal.valueOf(101));
        List<SpendingAnalysisSignal> result = assembler.assemble(Optional.of(budget), List.of());
        assertEquals(List.of(budget), result);
    }

    @Test
    void returnsCategorySignalsOnly() {
        SpendingAnalysisSignal food = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        List<SpendingAnalysisSignal> result = assembler.assemble(Optional.empty(), List.of(food));
        assertEquals(List.of(food), result);
    }

    @Test
    void placesBudgetSignalFirstWhenBothPresent() {
        SpendingAnalysisSignal budget = budgetSignal(500_000L, BigDecimal.valueOf(120));
        SpendingAnalysisSignal food = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        List<SpendingAnalysisSignal> result = assembler.assemble(Optional.of(budget), List.of(food));
        assertEquals(List.of(budget, food), result);
    }

    @Test
    void preservesCategorySurgeInputOrder() {
        SpendingAnalysisSignal cafe = categorySignal("CAFE", 90_000L, 30_000L, BigDecimal.valueOf(200));
        SpendingAnalysisSignal food = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        SpendingAnalysisSignal delivery = categorySignal("DELIVERY", 80_000L, 60_000L, BigDecimal.valueOf(33));
        List<SpendingAnalysisSignal> input = List.of(cafe, food, delivery);
        List<SpendingAnalysisSignal> result = assembler.assemble(Optional.empty(), input);
        assertEquals(input, result);
    }

    @Test
    void assemblesMaxFourSignals() {
        SpendingAnalysisSignal budget = budgetSignal(500_000L, BigDecimal.valueOf(120));
        SpendingAnalysisSignal cafe = categorySignal("CAFE", 90_000L, 30_000L, BigDecimal.valueOf(200));
        SpendingAnalysisSignal food = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        SpendingAnalysisSignal delivery = categorySignal("DELIVERY", 80_000L, 60_000L, BigDecimal.valueOf(33));
        List<SpendingAnalysisSignal> result =
                assembler.assemble(Optional.of(budget), List.of(cafe, food, delivery));
        assertEquals(List.of(budget, cafe, food, delivery), result);
    }

    @Test
    void resultListIsImmutable() {
        List<SpendingAnalysisSignal> result = assembler.assemble(Optional.empty(), List.of());
        assertThrows(UnsupportedOperationException.class, () ->
                result.add(budgetSignal(1L, BigDecimal.valueOf(101))));
    }

    @Test
    void doesNotMutateInputCategoryList() {
        SpendingAnalysisSignal food = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        List<SpendingAnalysisSignal> input = new ArrayList<>(List.of(food));
        assembler.assemble(Optional.empty(), input);
        assertEquals(1, input.size());
        assertEquals(food, input.get(0));
    }

    // ---------- 입력 null 검증 ----------

    @Test
    void rejectsNullBudgetOptional() {
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(null, List.of()));
    }

    @Test
    void rejectsNullCategoryList() {
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(Optional.empty(), null));
    }

    @Test
    void rejectsNullItemInCategoryList() {
        List<SpendingAnalysisSignal> input = new ArrayList<>();
        input.add(null);
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(Optional.empty(), input));
    }

    // ---------- 예산 신호 검증 ----------

    @Test
    void rejectsBudgetSignalWithWrongSignalType() {
        SpendingAnalysisSignal wrongType = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(wrongType), List.of()));
    }

    @Test
    void rejectsBudgetSignalWithNonNullTargetCategory() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.BUDGET_EXCEEDED, "FOOD", null, null, null, null,
                500_000L, BigDecimal.valueOf(120));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(invalid), List.of()));
    }

    @Test
    void rejectsBudgetSignalWithNonNullChangeFields() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.BUDGET_EXCEEDED, null, null, null, 1000L, null,
                500_000L, BigDecimal.valueOf(120));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(invalid), List.of()));
    }

    @Test
    void rejectsBudgetAmountNull() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.BUDGET_EXCEEDED, null, null, null, null, null,
                null, BigDecimal.valueOf(120));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(invalid), List.of()));
    }

    @Test
    void rejectsBudgetAmountZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(budgetSignal(0L, BigDecimal.valueOf(120))), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(budgetSignal(-1L, BigDecimal.valueOf(120))), List.of()));
    }

    @Test
    void rejectsBudgetUsageRateNull() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.BUDGET_EXCEEDED, null, null, null, null, null,
                500_000L, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(invalid), List.of()));
    }

    @Test
    void rejectsBudgetUsageRateAtOrBelowThreshold() {
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(budgetSignal(500_000L, BigDecimal.valueOf(100))), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.of(budgetSignal(500_000L, BigDecimal.valueOf(99))), List.of()));
    }

    // ---------- 카테고리 급증 신호 검증 ----------

    @Test
    void rejectsCategorySignalWithWrongSignalType() {
        SpendingAnalysisSignal wrongType = budgetSignal(500_000L, BigDecimal.valueOf(120));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(wrongType)));
    }

    @Test
    void rejectsCategorySignalWithNullTargetCategory() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, null, 100_000L, 50_000L, 50_000L,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithBlankTargetCategory() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "  ", 100_000L, 50_000L, 50_000L,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithDisallowedTargetCategory() {
        SpendingAnalysisSignal invalid = categorySignal("UNKNOWN", 100_000L, 50_000L, BigDecimal.valueOf(100));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNullCurrentAmount() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", null, 50_000L, 50_000L,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNegativeCurrentAmount() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", -1L, 50_000L, -50_001L,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNullPreviousAmount() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", 100_000L, null, 50_000L,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNonPositivePreviousAmount() {
        SpendingAnalysisSignal invalid = categorySignal("FOOD", 100_000L, 0L, BigDecimal.valueOf(100));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWhereCurrentNotGreaterThanPrevious() {
        SpendingAnalysisSignal invalid = categorySignal("FOOD", 10_000L, 15_000L, BigDecimal.valueOf(100));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNullChangeAmount() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", 100_000L, 50_000L, null,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNonPositiveChangeAmount() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", 100_000L, 50_000L, 0L,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithChangeAmountMismatch() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", 100_000L, 50_000L, 1_000L,
                BigDecimal.valueOf(100), null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNullChangeRate() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", 100_000L, 50_000L, 50_000L,
                null, null, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithChangeRateBelowThreshold() {
        SpendingAnalysisSignal invalid = categorySignal("FOOD", 60_000L, 50_000L, BigDecimal.valueOf(20));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNonNullBudgetAmount() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", 100_000L, 50_000L, 50_000L,
                BigDecimal.valueOf(100), 500_000L, null);
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsCategorySignalWithNonNullBudgetUsageRate() {
        SpendingAnalysisSignal invalid = new SpendingAnalysisSignal(
                SpendingSignalType.CATEGORY_SURGE, "FOOD", 100_000L, 50_000L, 50_000L,
                BigDecimal.valueOf(100), null, BigDecimal.valueOf(120));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(invalid)));
    }

    @Test
    void rejectsDuplicateTargetCategory() {
        SpendingAnalysisSignal first = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        SpendingAnalysisSignal second = categorySignal("FOOD", 90_000L, 60_000L, BigDecimal.valueOf(50));
        assertThrows(IllegalArgumentException.class, () ->
                assembler.assemble(Optional.empty(), List.of(first, second)));
    }

    @Test
    void rejectsMoreThanThreeCategorySignals() {
        SpendingAnalysisSignal food = categorySignal("FOOD", 100_000L, 50_000L, BigDecimal.valueOf(100));
        SpendingAnalysisSignal cafe = categorySignal("CAFE", 90_000L, 30_000L, BigDecimal.valueOf(200));
        SpendingAnalysisSignal delivery = categorySignal("DELIVERY", 80_000L, 60_000L, BigDecimal.valueOf(33));
        SpendingAnalysisSignal shopping = categorySignal("SHOPPING", 70_000L, 40_000L, BigDecimal.valueOf(75));
        List<SpendingAnalysisSignal> input = List.of(food, cafe, delivery, shopping);
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(Optional.empty(), input));
    }
}
