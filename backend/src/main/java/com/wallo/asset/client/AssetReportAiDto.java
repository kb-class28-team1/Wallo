package com.wallo.asset.client;

public final class AssetReportAiDto {

    private AssetReportAiDto() {
    }

    public record Request(
            String category,
            String categoryLabel,
            long previousAmount,
            long previousTotalAmount,
            long monthlyBudget,
            double categoryChangeRate,
            double totalChangeRate,
            boolean withinBudget
    ) {

        public Request(
                String category,
                String categoryLabel,
                long currentAmount,
                long previousAmount
        ) {
            this(
                    category,
                    categoryLabel,
                    previousAmount,
                    0L,
                    0L,
                    calculateChangeRate(currentAmount, previousAmount),
                    0.0,
                    false
            );
        }

        public Request(
                String category,
                String categoryLabel,
                long currentAmount,
                long previousAmount,
                long currentTotalAmount,
                long previousTotalAmount,
                long monthlyBudget
        ) {
            this(
                    category,
                    categoryLabel,
                    previousAmount,
                    previousTotalAmount,
                    monthlyBudget,
                    calculateChangeRate(currentAmount, previousAmount),
                    calculateChangeRate(currentTotalAmount, previousTotalAmount),
                    monthlyBudget > 0 && currentTotalAmount <= monthlyBudget
            );
        }

        private static double calculateChangeRate(long currentAmount, long previousAmount) {
            if (previousAmount <= 0) {
                return 0.0;
            }

            return ((double) currentAmount - previousAmount) / previousAmount * 100;
        }
    }

    public record Response(
            String reportTitle,
            String reportContent
    ) {
    }
}
