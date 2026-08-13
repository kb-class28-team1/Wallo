package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.exception.InvalidDashboardRequestException;
import com.wallo.asset.mapper.ExpenseMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpenseServiceTest {

    private final ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
    private final ExpenseService expenseService = new ExpenseService(expenseMapper);

    @Test
    void buildsDailyBreakdownAndPaginationResponse() {
        ExpenseDto.DailyBreakdown daily = new ExpenseDto.DailyBreakdown(
                "2026-07-20", 15_000L, 0L
        );
        when(expenseMapper.selectTransactions(eq(7L), any())).thenReturn(List.of());
        when(expenseMapper.countTransactions(eq(7L), any())).thenReturn(25L);
        when(expenseMapper.selectTotalExpense(eq(7L), any())).thenReturn(155_000L);
        when(expenseMapper.selectTotalIncome(eq(7L), any())).thenReturn(3_000_000L);
        when(expenseMapper.selectExpenseCategoryBreakdown(eq(7L), any())).thenReturn(List.of());
        when(expenseMapper.selectDailyBreakdown(eq(7L), any())).thenReturn(List.of(daily));

        ExpenseDto.Summary summary = expenseService.getExpenseSummary(
                7L,
                new ExpenseDto.SearchCondition("2026-07-01", "2026-07-31", 1, 10, 0)
        );

        assertEquals(1, summary.getDailyBreakdown().size());
        assertEquals("2026-07-20", summary.getDailyBreakdown().get(0).getDate());
        assertEquals(1, summary.getPagination().getCurrentPage());
        assertEquals(3, summary.getPagination().getTotalPages());
        assertEquals(25L, summary.getPagination().getTotalElements());
        assertTrue(summary.getPagination().isHasNext());
    }

    @Test
    void returnsZeroPagePaginationWhenThereAreNoTransactions() {
        when(expenseMapper.selectTransactions(eq(7L), any())).thenReturn(List.of());
        when(expenseMapper.countTransactions(eq(7L), any())).thenReturn(0L);
        when(expenseMapper.selectExpenseCategoryBreakdown(eq(7L), any())).thenReturn(List.of());
        when(expenseMapper.selectDailyBreakdown(eq(7L), any())).thenReturn(List.of());

        ExpenseDto.Summary summary = expenseService.getExpenseSummary(
                7L,
                new ExpenseDto.SearchCondition("2026-07-01", "2026-07-31", 0, 20, 0)
        );

        assertEquals(0, summary.getPagination().getTotalPages());
        assertEquals(0L, summary.getPagination().getTotalElements());
        assertEquals(false, summary.getPagination().isHasNext());
    }

    @Test
    void rejectsMissingOrInvalidRequiredConditions() {
        assertThrows(
                InvalidDashboardRequestException.class,
                () -> expenseService.getExpenseSummary(7L, null)
        );
        assertThrows(
                InvalidDashboardRequestException.class,
                () -> expenseService.getExpenseSummary(
                        7L,
                        new ExpenseDto.SearchCondition(null, "2026-07-31", 0, 20, 0)
                )
        );
        assertThrows(
                InvalidDashboardRequestException.class,
                () -> expenseService.getExpenseSummary(
                        7L,
                        new ExpenseDto.SearchCondition("2026-07-01", "2026-07-31", -1, 20, 0)
                )
        );
        assertThrows(
                InvalidDashboardRequestException.class,
                () -> expenseService.getExpenseSummary(
                        7L,
                        new ExpenseDto.SearchCondition("2026-07-01", "2026-07-31", 0, 0, 0)
                )
        );
    }

    @Test
    void normalizesOtherBeforeUpdatingTransactionCategory() {
        when(expenseMapper.updateTransactionCategory(7L, 42L, "ETC")).thenReturn(1);

        expenseService.updateTransactionCategory(
                7L,
                42L,
                new ExpenseDto.CategoryUpdateRequest(" other ")
        );

        verify(expenseMapper).updateTransactionCategory(7L, 42L, "ETC");
    }

    @Test
    void rejectsUnknownCategoryBeforeCallingMapper() {
        assertThrows(
                InvalidDashboardRequestException.class,
                () -> expenseService.updateTransactionCategory(
                        7L,
                        42L,
                        new ExpenseDto.CategoryUpdateRequest("UNKNOWN")
                )
        );

        org.mockito.Mockito.verifyNoInteractions(expenseMapper);
    }
}
