package com.wallo.asset.dto;

import java.util.List;

public record ConsumptionAnalysisContextDto(
        List<ExpenseDto.AnalysisTransaction> transactions,
        Budget budget,
        List<Budget> budgets
) {
    public ConsumptionAnalysisContextDto {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
        budgets = budgets == null ? List.of() : List.copyOf(budgets);
    }

    public ConsumptionAnalysisContextDto(
            List<ExpenseDto.AnalysisTransaction> transactions,
            Budget budget
    ) {
        this(transactions, budget, budget == null ? List.of() : List.of(budget));
    }

    public record Budget(String targetMonth, long totalAmount) {
    }
}
