package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.wallo.chat.dto.ConsumptionAnalysisView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsumptionAnalysisViewAssemblerTest {

    private final ConsumptionAnalysisViewAssembler assembler =
            new ConsumptionAnalysisViewAssembler();

    @Test
    void convertsCalculationIntoFrontendContract() {
        Map<String, Object> calculation = Map.ofEntries(
                Map.entry("periodType", "MONTHLY"),
                Map.entry("focus", "CATEGORY"),
                Map.entry("analysisPeriod", Map.of("start", "2026-08-01", "end", "2026-08-11")),
                Map.entry("comparisonPeriod", Map.of("start", "2026-07-01", "end", "2026-07-31")),
                Map.entry("dataSufficiency", Map.of(
                        "sufficient", true, "transactionCount", 5, "totalExpense", 142_500)),
                Map.entry("totalChange", Map.of(
                        "currentAmount", 142_500, "previousAmount", 408_500,
                        "changeAmount", -266_000, "changeRatePercent", -65.1,
                        "warning", false)),
                Map.entry("categoryOverview", List.of(Map.of(
                        "category", "TRANSPORT", "transactionCount", 1,
                        "maxTransactionAmount", 30_000,
                        "previousAmount", 0, "currentAmount", 30_000,
                        "changeAmount", 30_000))),
                Map.entry("criteriaEvaluations", List.of(Map.of(
                        "code", "DATA_SUFFICIENCY", "evaluated", true,
                        "detected", true, "reasonCode", "DETECTED",
                        "metrics", Map.of("transactionCount", 5)))),
                Map.entry("categorySurges", List.of()),
                Map.entry("newSpending", List.of()),
                Map.entry("oneOffHighSpending", List.of(Map.of(
                        "category", "TRANSPORT",
                        "maxTransactionAmount", 30_000,
                        "categoryAmount", 30_000,
                        "sharePercent", 100.0,
                        "limitAutomaticReductionMission", true
                ))),
                Map.entry("repeatingCategories", List.of(Map.of(
                        "category", "CAFE", "amount", 45_000,
                        "count", 3, "max", 15_000))),
                Map.entry("patterns", Map.of()),
                Map.entry("recurringPaymentCandidates", List.of()),
                Map.entry("positiveImprovements", List.of()),
                Map.entry("continuousImprovement", Map.of(
                        "twoConsecutiveMonthsDecreased", false))
        );

        ConsumptionAnalysisView result = assembler.assemble(calculation);

        assertEquals("CATEGORY", result.focus());
        assertEquals("이번 달", result.period().label());
        assertEquals(142_500L, result.summary().currentTotal());
        assertEquals(-65.1, result.summary().deltaRate());
        assertNull(result.insufficient());
        assertNotNull(result.signals());
        assertEquals(1, result.signals().categoryOverview().size());
        assertEquals(1, result.signals().criteriaEvaluations().size());
        assertEquals(1, result.signals().repeatingCategories().size());
        assertEquals(21.1,
                result.signals().oneTimeLarge().get(0).get("shareOfTotalPercent"));
    }

    @Test
    void exposesOnlyInsufficientNoticeWhenDataIsNotEnough() {
        Map<String, Object> calculation = Map.of(
                "periodType", "WEEKLY",
                "analysisPeriod", Map.of("start", "2026-08-10", "end", "2026-08-11"),
                "comparisonPeriod", Map.of("start", "2026-08-03", "end", "2026-08-04"),
                "dataSufficiency", Map.of(
                        "sufficient", false, "transactionCount", 2, "totalExpense", 10_000),
                "message", "거래가 충분하지 않아요"
        );

        ConsumptionAnalysisView result = assembler.assemble(calculation);

        assertFalse(result.hasEnoughData());
        assertEquals(2, result.insufficient().txCount());
        assertNull(result.summary());
    }
}
