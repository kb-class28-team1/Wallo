package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.classification.ExpenseCategoryClassifier;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.mapper.ExpenseMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FallbackClassificationServiceTest {

    private final ExpenseMapper expenseMapper = mock(ExpenseMapper.class);
    private final ExpenseCategoryClassifier categoryClassifier = mock(ExpenseCategoryClassifier.class);
    private final FallbackClassificationService service = new FallbackClassificationService(
            expenseMapper,
            categoryClassifier
    );

    @Test
    void reclassifiesOnlyFallbackTransactionsWithNonFallbackResults() {
        ExpenseDto.Transaction aiTarget = new ExpenseDto.Transaction(
                101L,
                "2026-08-10",
                "EXPENSE",
                "ETC",
                12_000L,
                "동네 식당"
        );
        ExpenseDto.Transaction unresolvedTarget = new ExpenseDto.Transaction(
                102L,
                "2026-08-11",
                "EXPENSE",
                "ETC",
                8_000L,
                "알 수 없는 가맹점"
        );
        when(expenseMapper.selectFallbackTransactions(7L)).thenReturn(
                List.of(aiTarget, unresolvedTarget)
        );
        when(categoryClassifier.classifyBatch(any())).thenReturn(List.of(
                new ExpenseCategoryClassifier.Result(
                        "FOOD",
                        "AI",
                        new BigDecimal("0.9200"),
                        "ai-v1"
                ),
                new ExpenseCategoryClassifier.Result(
                        "ETC",
                        "FALLBACK",
                        BigDecimal.ZERO,
                        "fallback-v1"
                )
        ));
        when(expenseMapper.updateReclassifiedCategory(
                7L,
                101L,
                "FOOD",
                "AI",
                new BigDecimal("0.9200"),
                "ai-v1"
        )).thenReturn(1);

        assertEquals(1, service.reclassify(7L));

        verify(expenseMapper).updateReclassifiedCategory(
                eq(7L),
                eq(101L),
                eq("FOOD"),
                eq("AI"),
                eq(new BigDecimal("0.9200")),
                eq("ai-v1")
        );
        verify(expenseMapper, never()).updateReclassifiedCategory(
                eq(7L),
                eq(102L),
                any(),
                any(),
                any(),
                any()
        );
    }
}
