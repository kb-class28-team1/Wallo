package com.wallo.asset.dto;

import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class AssetReportDto {

    private AssetReportDto() {
    }

    public enum GenerationMode {
        AI,
        FALLBACK,
        RULE
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Insight {
        private String reportTitle;
        private String reportContent;
        private GenerationMode generationMode;
        private String category;

        public Insight(
                String reportTitle,
                String reportContent,
                GenerationMode generationMode
        ) {
            this(reportTitle, reportContent, generationMode, null);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryExpense {
        private String category;
        private long currentAmount;
        private long previousAmount;

        public String normalizedCategory() {
            String normalized = Objects.toString(category, "ETC").toUpperCase();
            return "OTHER".equals(normalized) ? "ETC" : normalized;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardSpending {
        private long cardSpentYtd;
        private long creditCardSpentYtd;
        private long checkCardSpentYtd;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxSettlement {
        private long annualSalary;
        private long creditCardThreshold;
        private long cardSpentYtd;
        private long creditCardSpentYtd;
        private long checkCardSpentYtd;
    }
}
