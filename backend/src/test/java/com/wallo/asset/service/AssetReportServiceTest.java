package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AssetReportDto;
import com.wallo.asset.mapper.AssetReportMapper;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AssetReportServiceTest {

    private final AssetReportMapper assetReportMapper = mock(AssetReportMapper.class);
    private final AssetReportService assetReportService = new AssetReportService(assetReportMapper);

    @Test
    void returnsTaxSettlementIncludingCreditAndCheckCardSpending() {
        when(assetReportMapper.selectAnnualSalary(7L)).thenReturn(50_000_000L);
        when(assetReportMapper.selectCardSpending(
                7L,
                "2026-01-01",
                "2026-07-31"
        )).thenReturn(new AssetReportDto.CardSpending(
                11_500_000L,
                3_000_000L,
                8_500_000L
        ));

        AssetReportDto.TaxSettlement result = assetReportService.getTaxSettlement(
                7L,
                2026,
                LocalDate.of(2026, 7, 31)
        );

        assertEquals(50_000_000L, result.getAnnualSalary());
        assertEquals(12_500_000L, result.getCreditCardThreshold());
        assertEquals(11_500_000L, result.getCardSpentYtd());
        assertEquals(3_000_000L, result.getCreditCardSpentYtd());
        assertEquals(8_500_000L, result.getCheckCardSpentYtd());
    }

    @Test
    void rejectsTaxSettlementWhenAnnualSalaryIsMissing() {
        when(assetReportMapper.selectAnnualSalary(7L)).thenReturn(null);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> assetReportService.getTaxSettlement(
                        7L,
                        2026,
                        LocalDate.of(2026, 7, 31)
                )
        );

        assertEquals(ErrorCode.ANNUAL_SALARY_REQUIRED, exception.getErrorCode());
    }

    @Test
    void rejectsFutureTaxSettlementYear() {
        CustomException exception = assertThrows(
                CustomException.class,
                () -> assetReportService.getTaxSettlement(
                        7L,
                        2027,
                        LocalDate.of(2026, 7, 31)
                )
        );

        assertEquals(ErrorCode.INVALID_REPORT_YEAR, exception.getErrorCode());
    }

    @Test
    void returnsCategoryWithLargestCurrentExpense() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-31",
                "2026-06-01",
                "2026-06-30"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("DELIVERY", 500_000L, 100_000L),
                new AssetReportDto.CategoryExpense("CAFE", 600_000L, 590_000L),
                new AssetReportDto.CategoryExpense("SHOPPING", 125_000L, 100_000L)
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 31)
        );

        assertEquals("카페 지출이 가장 많아요", insight.getReportTitle());
        assertEquals(
                "이번 달은 카페 지출이 가장 많아요. 소비 내역을 한 번 확인해 보세요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
        verify(assetReportMapper).selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-31",
                "2026-06-01",
                "2026-06-30"
        );
    }

    @Test
    void returnsLargestCategoryEvenWhenItDidNotIncreaseRapidly() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-15",
                "2026-06-01",
                "2026-06-15"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("FOOD", 129_000L, 100_000L),
                new AssetReportDto.CategoryExpense("CAFE", 50_000L, 0L),
                new AssetReportDto.CategoryExpense("SHOPPING", 80_000L, 100_000L)
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 15)
        );

        assertEquals("식비 지출이 가장 많아요", insight.getReportTitle());
        assertEquals(
                "이번 달은 식비 지출이 가장 많아요. 소비 내역을 한 번 확인해 보세요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
    }

    @Test
    void returnsEarlyMonthMessageWithoutQueryingExpenses() {
        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 3)
        );

        assertEquals("소비 데이터를 모으고 있어요", insight.getReportTitle());
        assertEquals(
                "이번 달 소비 패턴을 분석하려면 조금 더 지출 내역이 필요해요."
                        + " 데이터가 쌓이면 지출이 많은 카테고리와 지난달 대비 변화를 알려드릴게요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
    }

    @Test
    void returnsInsufficientDataMessageWhenCurrentTotalExpenseIsBelowThreshold() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-15",
                "2026-06-01",
                "2026-06-15"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("FOOD", 20_000L, 10_000L),
                new AssetReportDto.CategoryExpense("CAFE", 9_000L, 5_000L)
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 15)
        );

        assertEquals("소비 데이터가 아직 충분하지 않아요", insight.getReportTitle());
        assertEquals(
                "현재까지의 소비 데이터가 아직 충분하지 않아요."
                        + " 조금 더 지출 내역이 쌓이면 소비 패턴을 분석해드릴게요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
    }

    @Test
    void includesIncreaseRateWhenLargestCategoryIncreasedByAtLeastThirtyPercent() {
        when(assetReportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-20",
                "2026-06-01",
                "2026-06-20"
        )).thenReturn(Arrays.asList(
                new AssetReportDto.CategoryExpense("DELIVERY", 130_000L, 100_000L),
                new AssetReportDto.CategoryExpense("FOOD", 65_000L, 50_000L)
        ));

        AssetReportDto.Insight insight = assetReportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 20)
        );

        assertEquals("배달 지출이 가장 많아요", insight.getReportTitle());
        assertEquals(
                "이번 달은 배달 지출이 가장 많아요. 지난달 같은 기간보다 30% 늘었어요."
                        + " 소비 내역을 한 번 확인해 보세요.",
                insight.getReportContent()
        );
        assertEquals(AssetReportDto.GenerationMode.RULE, insight.getGenerationMode());
    }
}
