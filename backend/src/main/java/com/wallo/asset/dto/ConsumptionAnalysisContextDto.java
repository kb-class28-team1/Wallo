package com.wallo.asset.dto;

import java.util.List;

public record ConsumptionAnalysisContextDto(
        List<ExpenseDto.AnalysisTransaction> transactions,
        Budget budget
) {
    public ConsumptionAnalysisContextDto {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public record Budget(String targetMonth, long totalAmount) {
    }
}
