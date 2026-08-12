package com.wallo.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.spending.domain.SpendingAnalysisSignal;
import com.wallo.spending.domain.SpendingCategoryMetrics;
import com.wallo.spending.domain.SpendingComparisonStatus;
import com.wallo.spending.domain.SpendingSignalType;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpendingCategorySurgeSignalDetectorTest {

    private static final BigDecimal DUMMY_RATIO = new BigDecimal("10.00");

    private final SpendingCategorySurgeSignalDetector detector = new SpendingCategorySurgeSignalDetector();

    private SpendingCategoryMetrics comparable(
            String category, long amount, long previousAmount, BigDecimal changeRate
    ) {
        return new SpendingCategoryMetrics(
                category, amount, DUMMY_RATIO, previousAmount, amount - previousAmount, changeRate,
                SpendingComparisonStatus.COMPARABLE
        );
    }

    private SpendingCategoryMetrics newSpending(String category, long amount) {
        return new SpendingCategoryMetrics(
                category, amount, DUMMY_RATIO, 0L, amount, null, SpendingComparisonStatus.NEW_SPENDING
        );
    }

    private SpendingCategoryMetrics noSpending(String category) {
        return new SpendingCategoryMetrics(
                category, 0L, DUMMY_RATIO, 0L, 0L, null, SpendingComparisonStatus.NO_SPENDING
        );
    }

    // ---------- 임계값 ----------

    @Test
    void excludesWhenChangeRateBelowThreshold() {
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 12_999L, 10_000L, new BigDecimal("29.99"))));

        assertTrue(result.isEmpty());
    }

    @Test
    void includesWhenChangeRateExactlyThreshold() {
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 13_000L, 10_000L, new BigDecimal("30.00"))));

        assertEquals(1, result.size());
    }

    @Test
    void includesWhenChangeRateJustAboveThreshold() {
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 13_001L, 10_000L, new BigDecimal("30.01"))));

        assertEquals(1, result.size());
    }

    @Test
    void includesWhenChangeRateIsHigh() {
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 20_000L, 10_000L, new BigDecimal("100.00"))));

        assertEquals(1, result.size());
    }

    @Test
    void thresholdComparisonUsesCompareToRegardlessOfScale() {
        // "30.0"(scale=1)도 compareTo 기준으로 30.00과 동일하게 취급되어 포함되어야 한다.
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 13_000L, 10_000L, new BigDecimal("30.0"))));

        assertEquals(1, result.size());
    }

    // ---------- 제외 조건 ----------

    @Test
    void excludesNewSpendingCategory() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of(newSpending("FOOD", 500_000L)));

        assertTrue(result.isEmpty());
    }

    @Test
    void excludesNoSpendingCategory() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of(noSpending("FOOD")));

        assertTrue(result.isEmpty());
    }

    @Test
    void excludesDecreasedCategoryEvenIfChangeRateFieldIsHigh() {
        // amount(8000) < previousAmount(10000)인데도 changeRate 필드만 40.00으로 잘못 들어온 경우,
        // changeAmount<=0/amount<=previousAmount 정합성 방어로 제외되어야 한다.
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 8_000L, 10_000L, new BigDecimal("40.00"))));

        assertTrue(result.isEmpty());
    }

    @Test
    void excludesSameAmountCategory() {
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 10_000L, 10_000L, new BigDecimal("0.00"))));

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyListWhenNoCandidates() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of());

        assertTrue(result.isEmpty());
    }

    // ---------- 신호 필드 ----------

    @Test
    void mapsSignalFieldsFromCategoryMetrics() {
        BigDecimal changeRate = new BigDecimal("30.00");
        SpendingAnalysisSignal signal = detector.detect(
                List.of(comparable("FOOD", 13_000L, 10_000L, changeRate))).get(0);

        assertEquals(SpendingSignalType.CATEGORY_SURGE, signal.signalType());
        assertEquals("FOOD", signal.targetCategory());
        assertEquals(13_000L, signal.currentAmount());
        assertEquals(10_000L, signal.previousAmount());
        assertEquals(3_000L, signal.changeAmount());
        assertEquals(changeRate, signal.changeRate());
        assertEquals(changeRate.scale(), signal.changeRate().scale());
        assertNull(signal.budgetAmount());
        assertNull(signal.budgetUsageRate());
    }

    // ---------- 정렬 및 제한 ----------

    @Test
    void sortsByChangeAmountDescending() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of(
                comparable("FOOD", 11_000L, 10_000L, new BigDecimal("30.00")),   // changeAmount=1000
                comparable("CAFE", 15_000L, 10_000L, new BigDecimal("50.00"))    // changeAmount=5000
        ));

        assertEquals("CAFE", result.get(0).targetCategory());
        assertEquals("FOOD", result.get(1).targetCategory());
    }

    @Test
    void breaksChangeAmountTieByChangeRateDescending() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of(
                comparable("FOOD", 14_000L, 10_000L, new BigDecimal("40.00")),   // changeAmount=4000
                comparable("CAFE", 14_000L, 10_000L, new BigDecimal("35.00"))    // changeAmount=4000
        ));

        assertEquals("FOOD", result.get(0).targetCategory());
        assertEquals("CAFE", result.get(1).targetCategory());
    }

    @Test
    void breaksFullTieByCategoryAscending() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of(
                comparable("DELIVERY", 14_000L, 10_000L, new BigDecimal("35.00")),
                comparable("CAFE", 14_000L, 10_000L, new BigDecimal("35.00"))
        ));

        assertEquals("CAFE", result.get(0).targetCategory());
        assertEquals("DELIVERY", result.get(1).targetCategory());
    }

    @Test
    void matchesFullOrderingExampleFromSpec() {
        // FOOD(50000,40.00) CAFE(50000,35.00) DELIVERY(50000,35.00) SHOPPING(30000,100.00)
        // -> 정렬: FOOD, CAFE, DELIVERY, SHOPPING -> 상위 3개: FOOD, CAFE, DELIVERY
        List<SpendingAnalysisSignal> result = detector.detect(List.of(
                comparable("FOOD", 60_000L, 10_000L, new BigDecimal("40.00")),      // changeAmount=50000
                comparable("CAFE", 60_000L, 10_000L, new BigDecimal("35.00")),      // changeAmount=50000
                comparable("DELIVERY", 60_000L, 10_000L, new BigDecimal("35.00")),  // changeAmount=50000
                comparable("SHOPPING", 40_000L, 10_000L, new BigDecimal("100.00"))  // changeAmount=30000
        ));

        assertEquals(3, result.size());
        assertEquals(List.of("FOOD", "CAFE", "DELIVERY"),
                result.stream().map(SpendingAnalysisSignal::targetCategory).toList());
    }

    @Test
    void returnsAllWhenExactlyThreeCandidates() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of(
                comparable("FOOD", 13_000L, 10_000L, new BigDecimal("30.00")),
                comparable("CAFE", 13_000L, 10_000L, new BigDecimal("30.00")),
                comparable("DELIVERY", 13_000L, 10_000L, new BigDecimal("30.00"))
        ));

        assertEquals(3, result.size());
    }

    @Test
    void limitsToTopThreeWhenMoreThanThreeCandidates() {
        List<SpendingAnalysisSignal> result = detector.detect(List.of(
                comparable("FOOD", 13_000L, 10_000L, new BigDecimal("30.00")),
                comparable("CAFE", 13_000L, 10_000L, new BigDecimal("30.00")),
                comparable("DELIVERY", 13_000L, 10_000L, new BigDecimal("30.00")),
                comparable("SHOPPING", 13_000L, 10_000L, new BigDecimal("30.00"))
        ));

        assertEquals(3, result.size());
    }

    @Test
    void resultListIsImmutable() {
        List<SpendingAnalysisSignal> result = detector.detect(
                List.of(comparable("FOOD", 13_000L, 10_000L, new BigDecimal("30.00"))));

        assertThrows(UnsupportedOperationException.class,
                () -> result.add(result.get(0)));
    }

    // ---------- 입력 검증 ----------

    @Test
    void rejectsNullList() {
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null));
    }

    @Test
    void rejectsNullItemInList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(Arrays.asList(comparable("FOOD", 13_000L, 10_000L, new BigDecimal("30.00")), null))
        );
    }

    @Test
    void rejectsNullCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(comparable(null, 13_000L, 10_000L, new BigDecimal("30.00"))))
        );
    }

    @Test
    void rejectsBlankCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(comparable("   ", 13_000L, 10_000L, new BigDecimal("30.00"))))
        );
    }

    @Test
    void rejectsDisallowedCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(comparable("INCOME", 13_000L, 10_000L, new BigDecimal("30.00"))))
        );
    }

    @Test
    void rejectsDuplicateCategory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(
                        comparable("FOOD", 13_000L, 10_000L, new BigDecimal("30.00")),
                        comparable("FOOD", 14_000L, 10_000L, new BigDecimal("40.00"))
                ))
        );
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", -1L, DUMMY_RATIO, 0L, -1L, null, SpendingComparisonStatus.NO_SPENDING)))
        );
    }

    @Test
    void rejectsNegativePreviousAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 0L, DUMMY_RATIO, -1L, 1L, new BigDecimal("30.00"),
                        SpendingComparisonStatus.COMPARABLE)))
        );
    }

    @Test
    void rejectsChangeAmountMismatch() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, DUMMY_RATIO, 10_000L, 9_999L, new BigDecimal("30.00"),
                        SpendingComparisonStatus.COMPARABLE)))
        );
    }

    @Test
    void rejectsNullComparisonStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, DUMMY_RATIO, 10_000L, 3_000L, new BigDecimal("30.00"), null)))
        );
    }

    @Test
    void rejectsComparableWithZeroPreviousAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, DUMMY_RATIO, 0L, 13_000L, new BigDecimal("30.00"),
                        SpendingComparisonStatus.COMPARABLE)))
        );
    }

    @Test
    void rejectsComparableWithNullChangeRate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, DUMMY_RATIO, 10_000L, 3_000L, null,
                        SpendingComparisonStatus.COMPARABLE)))
        );
    }

    @Test
    void rejectsNewSpendingWithNonZeroPreviousAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, DUMMY_RATIO, 1L, 12_999L, null,
                        SpendingComparisonStatus.NEW_SPENDING)))
        );
    }

    @Test
    void rejectsNewSpendingWithZeroAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 0L, DUMMY_RATIO, 0L, 0L, null, SpendingComparisonStatus.NEW_SPENDING)))
        );
    }

    @Test
    void rejectsNewSpendingWithChangeRatePresent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, DUMMY_RATIO, 0L, 13_000L, new BigDecimal("30.00"),
                        SpendingComparisonStatus.NEW_SPENDING)))
        );
    }

    @Test
    void rejectsNoSpendingWithNonZeroAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 1L, DUMMY_RATIO, 0L, 1L, null, SpendingComparisonStatus.NO_SPENDING)))
        );
    }

    @Test
    void rejectsNoSpendingWithChangeRatePresent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 0L, DUMMY_RATIO, 0L, 0L, new BigDecimal("0.00"),
                        SpendingComparisonStatus.NO_SPENDING)))
        );
    }

    @Test
    void rejectsNullRatio() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, null, 10_000L, 3_000L, new BigDecimal("30.00"),
                        SpendingComparisonStatus.COMPARABLE)))
        );
    }

    @Test
    void rejectsNegativeRatio() {
        assertThrows(
                IllegalArgumentException.class,
                () -> detector.detect(List.of(new SpendingCategoryMetrics(
                        "FOOD", 13_000L, new BigDecimal("-0.01"), 10_000L, 3_000L, new BigDecimal("30.00"),
                        SpendingComparisonStatus.COMPARABLE)))
        );
    }
}
