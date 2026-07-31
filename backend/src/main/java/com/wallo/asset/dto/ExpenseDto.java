package com.wallo.asset.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class ExpenseDto {

    private ExpenseDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchCondition {
        private String startDate;
        private String endDate;
        private int page;
        private int size;
        private int offset;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private long amount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transaction {
        private String date;
        private String type;
        private String category;
        private long amount;
        private String merchantName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long totalExpense;
        private long totalIncome;
        private List<CategoryBreakdown> expenseCategoryBreakdown;
        private List<Transaction> transactions;
        private long totalCount;
        private int page;
        private int size;
    }
}
