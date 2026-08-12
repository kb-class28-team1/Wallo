package com.wallo.asset.dto;

import java.math.BigDecimal;
import java.util.List;
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
    public static class CategoryBudgetRequest {
        private String category;
        private long budgetAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryUpsertRequest {
        private String targetMonth;
        private long totalAmount;
        private List<CategoryBudgetRequest> categoryBudgets;
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
    public static class Plan {
        private long budgetPlanId;
        private String effectiveMonth;
        private long totalAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Allocation {
        private String category;
        private long budgetAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Category {
        private String category;
        private long budgetAmount;
        private long spentAmount;
        private long remainingAmount;
        private BigDecimal usageRate;
        private boolean overBudget;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private String targetMonth;
        private long totalAmount;
        private long allocatedAmount;
        private long unallocatedAmount;
        private long spentAmount;
        private long remainingAmount;
        private BigDecimal usageRate;
        private boolean overBudget;
        private List<Category> categories;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryExpense {
        private String category;
        private long spentAmount;
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
