package com.wallo.spending.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wallo.spending.domain.SpendingAnalysisPeriod;
import com.wallo.spending.domain.SpendingAnalysisType;
import com.wallo.spending.dto.SpendingAnalysisDto;
import com.wallo.spending.exception.InvalidSpendingAnalysisRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SpendingAnalysisPeriodCalculatorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private SpendingAnalysisPeriodCalculator calculatorAt(String todayIsoDate) {
        Clock clock = Clock.fixed(
                LocalDate.parse(todayIsoDate).atStartOfDay(KST).toInstant(),
                KST
        );
        return new SpendingAnalysisPeriodCalculator(clock);
    }

    private SpendingAnalysisDto.CreateRequest monthlyRequest(String targetMonth) {
        SpendingAnalysisDto.CreateRequest request = new SpendingAnalysisDto.CreateRequest();
        request.setAnalysisType("MONTHLY");
        request.setTargetMonth(targetMonth);
        return request;
    }

    private SpendingAnalysisDto.CreateRequest customRangeRequest(String startDate, String endDate) {
        SpendingAnalysisDto.CreateRequest request = new SpendingAnalysisDto.CreateRequest();
        request.setAnalysisType("CUSTOM_RANGE");
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        return request;
    }

    // ---------- MONTHLY ----------

    @Test
    void monthlyCurrentMonthCoversFirstDayThroughToday() {
        SpendingAnalysisPeriod period =
                calculatorAt("2026-08-05").calculate(monthlyRequest("2026-08"));

        assertEquals(SpendingAnalysisType.MONTHLY, period.analysisType());
        assertEquals("2026-08", period.targetMonth());
        assertEquals(LocalDate.of(2026, 8, 1), period.periodStart());
        assertEquals(LocalDate.of(2026, 8, 5), period.periodEnd());
        assertEquals(LocalDate.of(2026, 7, 1), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 5), period.comparisonPeriodEnd());
    }

    @Test
    void monthlyPastMonthCoversEntireMonth() {
        SpendingAnalysisPeriod period =
                calculatorAt("2026-08-05").calculate(monthlyRequest("2026-07"));

        assertEquals(LocalDate.of(2026, 7, 1), period.periodStart());
        assertEquals(LocalDate.of(2026, 7, 31), period.periodEnd());
        assertEquals(LocalDate.of(2026, 6, 1), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2026, 6, 30), period.comparisonPeriodEnd());
    }

    @Test
    void monthlyRejectsFutureMonth() {
        InvalidSpendingAnalysisRequestException exception = assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(monthlyRequest("2026-09"))
        );

        assertEquals("미래 월은 분석할 수 없습니다.", exception.getReason());
    }

    @Test
    void monthlyRejectsMissingTargetMonth() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(monthlyRequest(null))
        );
    }

    @Test
    void monthlyRejectsMissingTargetMonthWhenBlank() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(monthlyRequest("   "))
        );
    }

    @Test
    void monthlyRejectsInvalidTargetMonthFormat() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(monthlyRequest("2026/08"))
        );
    }

    @Test
    void monthlyRejectsWhenStartDateOrEndDateProvided() {
        SpendingAnalysisDto.CreateRequest request = monthlyRequest("2026-08");
        request.setStartDate("2026-08-01");

        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(request)
        );
    }

    @Test
    void monthlyJanuaryComparesAgainstPreviousDecember() {
        SpendingAnalysisPeriod period =
                calculatorAt("2026-01-15").calculate(monthlyRequest("2026-01"));

        assertEquals(LocalDate.of(2026, 1, 1), period.periodStart());
        assertEquals(LocalDate.of(2026, 1, 15), period.periodEnd());
        assertEquals(LocalDate.of(2025, 12, 1), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2025, 12, 15), period.comparisonPeriodEnd());
    }

    @Test
    void monthlyMarch31CurrentMonthCapsComparisonToFebruaryLastDay() {
        SpendingAnalysisPeriod period =
                calculatorAt("2024-03-31").calculate(monthlyRequest("2024-03"));

        assertEquals(LocalDate.of(2024, 3, 31), period.periodEnd());
        assertEquals(LocalDate.of(2024, 2, 1), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2024, 2, 29), period.comparisonPeriodEnd());
    }

    @Test
    void monthlyPastLeapFebruaryCoversTwentyNineDays() {
        SpendingAnalysisPeriod period =
                calculatorAt("2024-03-15").calculate(monthlyRequest("2024-02"));

        assertEquals(LocalDate.of(2024, 2, 1), period.periodStart());
        assertEquals(LocalDate.of(2024, 2, 29), period.periodEnd());
        assertEquals(LocalDate.of(2024, 1, 1), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2024, 1, 31), period.comparisonPeriodEnd());
    }

    // ---------- CUSTOM_RANGE ----------

    @Test
    void customRangeComparesAgainstImmediatelyPrecedingSameLengthPeriod() {
        SpendingAnalysisPeriod period = calculatorAt("2026-08-05")
                .calculate(customRangeRequest("2026-08-01", "2026-08-05"));

        assertEquals(LocalDate.of(2026, 8, 1), period.periodStart());
        assertEquals(LocalDate.of(2026, 8, 5), period.periodEnd());
        assertEquals(LocalDate.of(2026, 7, 27), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 31), period.comparisonPeriodEnd());
    }

    @Test
    void customRangeAllowsSingleDayPeriod() {
        SpendingAnalysisPeriod period = calculatorAt("2026-08-05")
                .calculate(customRangeRequest("2026-08-05", "2026-08-05"));

        assertEquals(LocalDate.of(2026, 8, 5), period.periodStart());
        assertEquals(LocalDate.of(2026, 8, 5), period.periodEnd());
        assertEquals(LocalDate.of(2026, 8, 4), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2026, 8, 4), period.comparisonPeriodEnd());
    }

    @Test
    void customRangeAllowsExactly366Days() {
        SpendingAnalysisPeriod period = calculatorAt("2026-08-05")
                .calculate(customRangeRequest("2025-01-01", "2026-01-01"));

        assertEquals(LocalDate.of(2025, 1, 1), period.periodStart());
        assertEquals(LocalDate.of(2026, 1, 1), period.periodEnd());
    }

    @Test
    void customRangeRejects367Days() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05")
                        .calculate(customRangeRequest("2025-01-01", "2026-01-02"))
        );
    }

    @Test
    void customRangeRejectsWhenStartDateAfterEndDate() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05")
                        .calculate(customRangeRequest("2026-08-05", "2026-08-01"))
        );
    }

    @Test
    void customRangeRejectsFutureEndDate() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05")
                        .calculate(customRangeRequest("2026-08-01", "2026-08-06"))
        );
    }

    @Test
    void customRangeRejectsMissingStartDate() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05")
                        .calculate(customRangeRequest(null, "2026-08-05"))
        );
    }

    @Test
    void customRangeRejectsMissingEndDate() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05")
                        .calculate(customRangeRequest("2026-08-01", null))
        );
    }

    @Test
    void customRangeRejectsInvalidDateFormat() {
        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05")
                        .calculate(customRangeRequest("2026/08/01", "2026-08-05"))
        );
    }

    @Test
    void customRangeRejectsWhenTargetMonthProvided() {
        SpendingAnalysisDto.CreateRequest request =
                customRangeRequest("2026-08-01", "2026-08-05");
        request.setTargetMonth("2026-08");

        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(request)
        );
    }

    @Test
    void customRangeComparisonPeriodCrossesYearBoundary() {
        SpendingAnalysisPeriod period = calculatorAt("2026-08-05")
                .calculate(customRangeRequest("2026-01-01", "2026-01-05"));

        assertEquals(LocalDate.of(2025, 12, 27), period.comparisonPeriodStart());
        assertEquals(LocalDate.of(2025, 12, 31), period.comparisonPeriodEnd());
    }

    // ---------- analysisType 검증 ----------

    @Test
    void rejectsMissingAnalysisType() {
        SpendingAnalysisDto.CreateRequest request = new SpendingAnalysisDto.CreateRequest();

        InvalidSpendingAnalysisRequestException exception = assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(request)
        );

        assertFalse(exception.getReason() == null || exception.getReason().isBlank());
    }

    @Test
    void rejectsUnsupportedAnalysisType() {
        SpendingAnalysisDto.CreateRequest request = new SpendingAnalysisDto.CreateRequest();
        request.setAnalysisType("WEEKLY");

        assertThrows(
                InvalidSpendingAnalysisRequestException.class,
                () -> calculatorAt("2026-08-05").calculate(request)
        );
    }

    // ---------- 공통 ----------

    @Test
    void forceReanalyzeDefaultsToFalseWhenNotProvided() {
        SpendingAnalysisPeriod period =
                calculatorAt("2026-08-05").calculate(monthlyRequest("2026-08"));

        assertFalse(period.forceReanalyze());
    }

    @Test
    void forceReanalyzeStaysTrueWhenProvided() {
        SpendingAnalysisDto.CreateRequest request = monthlyRequest("2026-08");
        request.setForceReanalyze(true);

        SpendingAnalysisPeriod period = calculatorAt("2026-08-05").calculate(request);

        assertTrue(period.forceReanalyze());
    }

    @Test
    void usesKstDateRegardlessOfInjectedClockZone() {
        // UTC 2026-08-04T16:00:00Z = KST 2026-08-05T01:00 -> KST 기준 "오늘"은 2026-08-05여야 한다.
        Clock utcClock = Clock.fixed(Instant.parse("2026-08-04T16:00:00Z"), ZoneId.of("UTC"));
        SpendingAnalysisPeriodCalculator calculator = new SpendingAnalysisPeriodCalculator(utcClock);

        SpendingAnalysisPeriod period = calculator.calculate(monthlyRequest("2026-08"));

        assertEquals(LocalDate.of(2026, 8, 5), period.periodEnd());
    }
}
