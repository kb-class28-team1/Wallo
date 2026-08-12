package com.wallo.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.spending.domain.SpendingAnalysisSignal;
import com.wallo.spending.domain.SpendingComparisonStatus;
import com.wallo.spending.domain.SpendingOverallMetrics;
import com.wallo.spending.domain.SpendingSignalType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpendingBudgetSignalDetectorTest {

    private final SpendingBudgetSignalDetector detector = new SpendingBudgetSignalDetector();

    private SpendingOverallMetrics metrics(Long budgetAmount, BigDecimal budgetUsageRate) {
        return new SpendingOverallMetrics(
                0L, 0L, 0L, null, SpendingComparisonStatus.NO_SPENDING,
                budgetAmount, budgetUsageRate
        );
    }

    // ---------- 신호 미생성 ----------

    @Test
    void noSignalWhenBudgetIsNull() {
        Optional<SpendingAnalysisSignal> result = detector.detect(metrics(null, null));

        assertTrue(result.isEmpty());
    }

    @Test
    void noSignalWhenUsageRateIsZero() {
        Optional<SpendingAnalysisSignal> result = detector.detect(metrics(500_000L, new BigDecimal("0.00")));

        assertTrue(result.isEmpty());
    }

    @Test
    void noSignalWhenUsageRateIsBelowThreshold() {
        Optional<SpendingAnalysisSignal> result = detector.detect(metrics(500_000L, new BigDecimal("99.99")));

        assertTrue(result.isEmpty());
    }

    @Test
    void noSignalWhenUsageRateIsExactlyOneHundred() {
        Optional<SpendingAnalysisSignal> result = detector.detect(metrics(500_000L, new BigDecimal("100.00")));

        assertTrue(result.isEmpty());
    }

    // ---------- 신호 생성 ----------

    @Test
    void generatesSignalWhenUsageRateIsJustAboveThreshold() {
        Optional<SpendingAnalysisSignal> result = detector.detect(metrics(500_000L, new BigDecimal("100.01")));

        assertTrue(result.isPresent());
    }

    @Test
    void generatesSignalWhenUsageRateIsWellAboveThreshold() {
        Optional<SpendingAnalysisSignal> result = detector.detect(metrics(500_000L, new BigDecimal("120.00")));

        assertTrue(result.isPresent());
    }

    @Test
    void generatesSignalForVeryLargeUsageRate() {
        BigDecimal hugeRate = new BigDecimal("999999999999.99");

        Optional<SpendingAnalysisSignal> result = detector.detect(metrics(1L, hugeRate));

        assertTrue(result.isPresent());
        assertEquals(hugeRate, result.get().budgetUsageRate());
    }

    @Test
    void generatedSignalHasExpectedShape() {
        SpendingAnalysisSignal signal = detector.detect(metrics(500_000L, new BigDecimal("120.00"))).orElseThrow();

        assertEquals(SpendingSignalType.BUDGET_EXCEEDED, signal.signalType());
        assertNull(signal.targetCategory());
        assertNull(signal.currentAmount());
        assertNull(signal.previousAmount());
        assertNull(signal.changeAmount());
        assertNull(signal.changeRate());
        assertEquals(500_000L, signal.budgetAmount());
        assertEquals(new BigDecimal("120.00"), signal.budgetUsageRate());
    }

    @Test
    void preservesBudgetAmountAndUsageRateScaleFromInput() {
        BigDecimal usageRate = new BigDecimal("120.00");

        SpendingAnalysisSignal signal = detector.detect(metrics(500_000L, usageRate)).orElseThrow();

        assertEquals(500_000L, signal.budgetAmount());
        assertEquals(usageRate, signal.budgetUsageRate());
        assertEquals(usageRate.scale(), signal.budgetUsageRate().scale());
    }

    // ---------- 입력 검증 ----------

    @Test
    void rejectsNullMetrics() {
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null));
    }

    @Test
    void rejectsBudgetAmountNullWhenUsageRateIsNotNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(metrics(null, new BigDecimal("50.00")))
        );
    }

    @Test
    void rejectsUsageRateNullWhenBudgetAmountIsNotNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(metrics(500_000L, null))
        );
    }

    @Test
    void rejectsZeroBudgetAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(metrics(0L, new BigDecimal("50.00")))
        );
    }

    @Test
    void rejectsNegativeBudgetAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(metrics(-1L, new BigDecimal("50.00")))
        );
    }

    @Test
    void rejectsNegativeUsageRate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(metrics(500_000L, new BigDecimal("-0.01")))
        );
    }
}
