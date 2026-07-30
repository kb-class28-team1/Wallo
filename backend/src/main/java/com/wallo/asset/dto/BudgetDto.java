package com.wallo.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class BudgetDto {

    private BudgetDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertRequest {
        private String targetMonth;
        private long totalAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Budget {
        private long budgetId;
        private String targetMonth;
        private long totalAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private String targetMonth;
        private long totalAmount;
        private long spentAmount;
    }
}
