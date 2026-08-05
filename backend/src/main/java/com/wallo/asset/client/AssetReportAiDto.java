package com.wallo.asset.client;

public final class AssetReportAiDto {

    private AssetReportAiDto() {
    }

    public record Request(
            String category,
            String categoryLabel,
            long currentAmount,
            long previousAmount,
            long currentTotalAmount,
            long previousTotalAmount,
            long monthlyBudget
    ) {

        public Request(
                String category,
                String categoryLabel,
                long currentAmount,
                long previousAmount
        ) {
            this(category, categoryLabel, currentAmount, previousAmount, 0L, 0L, 0L);
        }
    }

    public record Response(
            String reportTitle,
            String reportContent
    ) {
    }
}
