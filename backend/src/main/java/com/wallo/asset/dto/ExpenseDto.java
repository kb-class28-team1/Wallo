package com.wallo.asset.dto;

import java.util.Arrays;
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
    public static class SearchCondition {
        private String startDate;
        private String endDate;
        private int page;
        private int size;
        private String category;
        private int offset;

        public SearchCondition(
                String startDate,
                String endDate,
                int page,
                int size,
                int offset
        ) {
            this(startDate, endDate, page, size, null, offset);
        }

        public SearchCondition(
                String startDate,
                String endDate,
                int page,
                int size,
                String category,
                int offset
        ) {
            this.startDate = startDate;
            this.endDate = endDate;
            this.page = page;
            this.size = size;
            this.category = category;
            this.offset = offset;
        }

        public List<String> getCategories() {
            if (category == null || category.isBlank()) {
                return List.of();
            }

            return Arrays.stream(category.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryUpdateRequest {
        private String category;
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
    public static class Transaction {
        private long transactionId;
        private String date;
        private String type;
        private String category;
        private long amount;
        private String merchantName;

        public Transaction(
                String date,
                String type,
                String category,
                long amount,
                String merchantName
        ) {
            this(0L, date, type, category, amount, merchantName);
        }

        public Transaction(
                long transactionId,
                String date,
                String type,
                String category,
                long amount,
                String merchantName
        ) {
            this.transactionId = transactionId;
            this.date = date;
            this.type = type;
            this.category = category;
            this.amount = amount;
            this.merchantName = merchantName;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisTransaction {
        private String date;
        private String time;
        private String category;
        private long amount;
        private String merchantName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyCashflow {
        private long monthlyIncome;
        private long monthlyExpense;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBreakdown {
        private String date;
        private long totalExpense;
        private long totalIncome;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {
        private int currentPage;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long totalExpense;
        private long totalIncome;
        private List<CategoryBreakdown> expenseCategoryBreakdown;
        private List<DailyBreakdown> dailyBreakdown;
        private List<Transaction> transactions;
        private Pagination pagination;
    }
}
