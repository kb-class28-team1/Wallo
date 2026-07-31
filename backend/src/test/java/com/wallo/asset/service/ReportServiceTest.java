package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.ReportDto;
import com.wallo.asset.mapper.ReportMapper;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private final ReportMapper reportMapper = mock(ReportMapper.class);
    private final ReportService reportService = new ReportService(reportMapper);

    @Test
    void returnsCategoryWithLargestExpenseIncrease() {
        when(reportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-31",
                "2026-06-01",
                "2026-06-30"
        )).thenReturn(Arrays.asList(
                new ReportDto.CategoryExpense("DELIVERY", 150_000L, 100_000L),
                new ReportDto.CategoryExpense("CAFE", 300_000L, 200_000L),
                new ReportDto.CategoryExpense("SHOPPING", 125_000L, 100_000L)
        ));

        ReportDto.Insight insight = reportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 31)
        );

        assertEquals("카페 지출 50% 급증!", insight.getReportTitle());
        assertEquals(
                "지난달 대비 카페 지출이 크게 늘었어요. 소비 내역을 확인해 보세요!",
                insight.getReportContent()
        );
        verify(reportMapper).selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-31",
                "2026-06-01",
                "2026-06-30"
        );
    }

    @Test
    void returnsNullWhenNoCategoryMeetsInsightCondition() {
        when(reportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-15",
                "2026-06-01",
                "2026-06-15"
        )).thenReturn(Arrays.asList(
                new ReportDto.CategoryExpense("FOOD", 129_000L, 100_000L),
                new ReportDto.CategoryExpense("CAFE", 50_000L, 0L),
                new ReportDto.CategoryExpense("SHOPPING", 80_000L, 100_000L)
        ));

        ReportDto.Insight insight = reportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 15)
        );

        assertNull(insight);
    }

    @Test
    void returnsNullWhenCurrentMonthHasNoDeliveryExpense() {
        when(reportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-15",
                "2026-06-01",
                "2026-06-15"
        )).thenReturn(Arrays.asList(
                new ReportDto.CategoryExpense("DELIVERY", 0L, 100_000L)
        ));

        ReportDto.Insight insight = reportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 15)
        );

        assertNull(insight);
    }

    @Test
    void keepsDeliverySpecificMessageWhenDeliveryHasLargestIncrease() {
        when(reportMapper.selectCategoryExpenses(
                7L,
                "2026-07-01",
                "2026-07-20",
                "2026-06-01",
                "2026-06-20"
        )).thenReturn(Arrays.asList(
                new ReportDto.CategoryExpense("DELIVERY", 130_000L, 100_000L),
                new ReportDto.CategoryExpense("FOOD", 65_000L, 50_000L)
        ));

        ReportDto.Insight insight = reportService.getConsumptionInsight(
                7L,
                LocalDate.of(2026, 7, 20)
        );

        assertEquals("배달비 30% 급증!", insight.getReportTitle());
        assertEquals(
                "지난달 대비 식비 중 배달 앱 결제가 크게 늘었어요. 야식의 유혹을 조심하세요!",
                insight.getReportContent()
        );
    }
}
