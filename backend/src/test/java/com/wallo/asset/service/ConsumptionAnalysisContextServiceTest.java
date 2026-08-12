package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.dto.ConsumptionAnalysisContextDto;
import com.wallo.asset.mapper.BudgetMapper;
import com.wallo.asset.mapper.ExpenseMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumptionAnalysisContextServiceTest {

    @Test
    void includesAllMonthlyBudgetsForPastPeriodAnalysis() {
        ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
        BudgetMapper budgetMapper = mock(BudgetMapper.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-11T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        when(expenseMapper.selectAllExpenseTransactions(106L)).thenReturn(List.of());
        when(budgetMapper.selectBudgets(106L)).thenReturn(List.of(
                new BudgetDto.Budget(1L, "2026-07", 1_500_000L),
                new BudgetDto.Budget(2L, "2026-08", 2_000_000L)
        ));
        ConsumptionAnalysisContextService service =
                new ConsumptionAnalysisContextService(
                        expenseMapper, budgetMapper, clock);

        ConsumptionAnalysisContextDto result = service.getContext(106L);

        assertEquals("2026-08", result.budget().targetMonth());
        assertEquals(2, result.budgets().size());
        assertEquals("2026-07", result.budgets().get(0).targetMonth());
        assertEquals(1_500_000L, result.budgets().get(0).totalAmount());
    }
}
