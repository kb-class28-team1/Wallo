package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.dto.ConsumptionAnalysisContextDto;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.mapper.BudgetMapper;
import com.wallo.asset.mapper.ExpenseMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumptionAnalysisContextServiceTest {

    @Test
    void forwardsTransactionsAlreadyFilteredByActiveInstitutionToAnalysisContext() {
        ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
        BudgetMapper budgetMapper = mock(BudgetMapper.class);
        List<ExpenseDto.AnalysisTransaction> transactions = List.of(
                new ExpenseDto.AnalysisTransaction(
                        "2026-08-10", "12:00:00", "FOOD", 120_000L, "활성 계좌 식비"),
                new ExpenseDto.AnalysisTransaction(
                        "2026-08-11", "13:00:00", "SHOPPING", 80_000L, "활성 카드 쇼핑")
        );
        when(expenseMapper.selectAllExpenseTransactions(106L)).thenReturn(transactions);
        when(budgetMapper.selectBudgets(106L)).thenReturn(List.of());

        ConsumptionAnalysisContextService service =
                new ConsumptionAnalysisContextService(expenseMapper, budgetMapper);

        ConsumptionAnalysisContextDto result = service.getContext(106L);

        assertEquals(transactions, result.transactions());
        assertEquals(2, result.transactions().size());
        assertEquals("활성 카드 쇼핑", result.transactions().get(1).getMerchantName());
    }

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
