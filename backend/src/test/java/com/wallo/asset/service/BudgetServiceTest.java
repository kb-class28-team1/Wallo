package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AssetReportDto;
import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.mapper.BudgetMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BudgetServiceTest {

    private static final Clock SEOUL_CLOCK = Clock.fixed(
            Instant.parse("2026-08-06T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final BudgetMapper budgetMapper = mock(BudgetMapper.class);
    private final ConsumptionInsightCache consumptionInsightCache =
            new ConsumptionInsightCache();
    private final BudgetService budgetService = new BudgetService(
            budgetMapper,
            consumptionInsightCache,
            SEOUL_CLOCK
    );

    @Test
    void invalidatesCurrentMonthInsightCacheAfterBudgetUpdate() {
        ConsumptionInsightCache.Key key = new ConsumptionInsightCache.Key(
                7L,
                YearMonth.of(2026, 8)
        );
        consumptionInsightCache.getOrGenerate(key, () -> new AssetReportDto.Insight(
                "제목",
                "본문",
                AssetReportDto.GenerationMode.AI,
                "CAFE"
        ));

        budgetService.upsertBudget(7L, new BudgetDto.UpsertRequest(null, 500_000L));

        assertNull(consumptionInsightCache.get(key));
        ArgumentCaptor<BudgetDto.UpsertRequest> requestCaptor =
                ArgumentCaptor.forClass(BudgetDto.UpsertRequest.class);
        verify(budgetMapper).upsertBudget(eq(7L), requestCaptor.capture());
        assertEquals("2026-08", requestCaptor.getValue().getTargetMonth());
        assertEquals(500_000L, requestCaptor.getValue().getTotalAmount());
    }

    @Test
    void doesNotInvalidateInsightCacheWhenUpdatingAnotherMonth() {
        ConsumptionInsightCache.Key currentMonthKey = new ConsumptionInsightCache.Key(
                7L,
                YearMonth.of(2026, 8)
        );
        consumptionInsightCache.getOrGenerate(currentMonthKey, () -> new AssetReportDto.Insight(
                "제목",
                "본문",
                AssetReportDto.GenerationMode.AI,
                "CAFE"
        ));

        budgetService.upsertBudget(7L, new BudgetDto.UpsertRequest("2026-07", 500_000L));

        assertNotNull(consumptionInsightCache.get(currentMonthKey));
    }

    @Test
    void legacyTotalUpdateKeepsExistingCategoryAllocationsInPlan() {
        when(budgetMapper.selectApplicablePlan(7L, "2026-08"))
                .thenReturn(
                        new BudgetDto.Plan(12L, "2026-08", 1_000_000L),
                        new BudgetDto.Plan(12L, "2026-08", 800_000L)
                );
        when(budgetMapper.selectPlanAllocations(12L)).thenReturn(List.of(
                new BudgetDto.Allocation("FOOD", 300_000L)
        ));
        when(budgetMapper.selectSpentAmount(7L, "2026-08-01", "2026-08-31"))
                .thenReturn(0L);

        BudgetDto.Summary result = budgetService.upsertBudget(
                7L,
                new BudgetDto.UpsertRequest("2026-08", 800_000L)
        );

        assertEquals(800_000L, result.getTotalAmount());
        ArgumentCaptor<BudgetDto.CategoryUpsertRequest> planCaptor =
                ArgumentCaptor.forClass(BudgetDto.CategoryUpsertRequest.class);
        verify(budgetMapper).upsertPlan(eq(7L), planCaptor.capture());
        assertEquals(800_000L, planCaptor.getValue().getTotalAmount());
        assertEquals(11, planCaptor.getValue().getCategoryBudgets().size());
        assertEquals(
                300_000L,
                planCaptor.getValue().getCategoryBudgets().get(0).getBudgetAmount()
        );
    }

    @Test
    void calculatesCategoryBudgetsAndUnallocatedEtcBudget() {
        when(budgetMapper.selectApplicablePlan(7L, "2026-08"))
                .thenReturn(new BudgetDto.Plan(11L, "2026-08", 1_000_000L));
        when(budgetMapper.selectPlanAllocations(11L)).thenReturn(List.of(
                new BudgetDto.Allocation("FOOD", 300_000L),
                new BudgetDto.Allocation("CAFE", 100_000L)
        ));
        when(budgetMapper.selectCategoryExpenses(7L, "2026-08-01", "2026-08-31"))
                .thenReturn(List.of(
                        new BudgetDto.CategoryExpense("FOOD", 360_000L),
                        new BudgetDto.CategoryExpense("OTHER", 50_000L)
                ));
        when(budgetMapper.selectSpentAmount(7L, "2026-08-01", "2026-08-31"))
                .thenReturn(410_000L);

        BudgetDto.CategorySummary result = budgetService.getCategoryBudgetSummary(7L, "2026-08");

        assertEquals(1_000_000L, result.getTotalAmount());
        assertEquals(400_000L, result.getAllocatedAmount());
        assertEquals(600_000L, result.getUnallocatedAmount());
        assertEquals(410_000L, result.getSpentAmount());
        assertEquals(590_000L, result.getRemainingAmount());
        assertEquals(new BigDecimal("41.00"), result.getUsageRate());
        assertEquals(12, result.getCategories().size());

        BudgetDto.Category food = result.getCategories().stream()
                .filter(category -> "FOOD".equals(category.getCategory()))
                .findFirst()
                .orElseThrow();
        assertEquals(300_000L, food.getBudgetAmount());
        assertEquals(360_000L, food.getSpentAmount());
        assertEquals(-60_000L, food.getRemainingAmount());
        assertEquals(new BigDecimal("120.00"), food.getUsageRate());
        assertEquals(true, food.isOverBudget());

        BudgetDto.Category etc = result.getCategories().stream()
                .filter(category -> "ETC".equals(category.getCategory()))
                .findFirst()
                .orElseThrow();
        assertEquals(600_000L, etc.getBudgetAmount());
        assertEquals(50_000L, etc.getSpentAmount());
        assertEquals(new BigDecimal("8.33"), etc.getUsageRate());
        assertEquals(false, etc.isOverBudget());
    }

    @Test
    void rejectsCategoryAllocationWhenItExceedsTotalBudget() {
        BudgetDto.CategoryUpsertRequest request = new BudgetDto.CategoryUpsertRequest(
                "2026-08",
                1_000_000L,
                List.of(
                        new BudgetDto.CategoryBudgetRequest("FOOD", 600_000L),
                        new BudgetDto.CategoryBudgetRequest("CAFE", 500_000L)
                )
        );

        assertThrows(
                com.wallo.asset.exception.InvalidDashboardRequestException.class,
                () -> budgetService.upsertCategoryBudgets(7L, request)
        );
    }

    @Test
    void rejectsCategoryBudgetUpdatesForPastMonths() {
        BudgetDto.CategoryUpsertRequest request = new BudgetDto.CategoryUpsertRequest(
                "2026-07",
                1_000_000L,
                List.of(new BudgetDto.CategoryBudgetRequest("FOOD", 300_000L))
        );

        assertThrows(
                com.wallo.asset.exception.InvalidDashboardRequestException.class,
                () -> budgetService.upsertCategoryBudgets(7L, request)
        );
    }

    @Test
    void savesCurrentPlanAndSynchronizesLegacyAggregateBudget() {
        BudgetDto.CategoryUpsertRequest request = new BudgetDto.CategoryUpsertRequest(
                "2026-08",
                1_000_000L,
                List.of(new BudgetDto.CategoryBudgetRequest("FOOD", 300_000L))
        );
        when(budgetMapper.selectPlan(7L, "2026-08"))
                .thenReturn(new BudgetDto.Plan(12L, "2026-08", 1_000_000L));
        when(budgetMapper.selectApplicablePlan(7L, "2026-08"))
                .thenReturn(new BudgetDto.Plan(12L, "2026-08", 1_000_000L));
        when(budgetMapper.selectPlanAllocations(12L)).thenReturn(List.of(
                new BudgetDto.Allocation("FOOD", 300_000L)
        ));
        when(budgetMapper.selectCategoryExpenses(7L, "2026-08-01", "2026-08-31"))
                .thenReturn(List.of());
        when(budgetMapper.selectSpentAmount(7L, "2026-08-01", "2026-08-31"))
                .thenReturn(0L);

        budgetService.upsertCategoryBudgets(7L, request);

        ArgumentCaptor<BudgetDto.CategoryUpsertRequest> planCaptor =
                ArgumentCaptor.forClass(BudgetDto.CategoryUpsertRequest.class);
        verify(budgetMapper).upsertPlan(eq(7L), planCaptor.capture());
        assertEquals("2026-08", planCaptor.getValue().getTargetMonth());
        assertEquals(1_000_000L, planCaptor.getValue().getTotalAmount());
        assertEquals(11, planCaptor.getValue().getCategoryBudgets().size());
        verify(budgetMapper).deletePlanAllocations(12L);
        verify(budgetMapper).insertPlanAllocations(eq(12L), any());
        ArgumentCaptor<BudgetDto.UpsertRequest> legacyRequestCaptor =
                ArgumentCaptor.forClass(BudgetDto.UpsertRequest.class);
        verify(budgetMapper).upsertBudget(eq(7L), legacyRequestCaptor.capture());
        assertEquals("2026-08", legacyRequestCaptor.getValue().getTargetMonth());
        assertEquals(1_000_000L, legacyRequestCaptor.getValue().getTotalAmount());
    }
}
