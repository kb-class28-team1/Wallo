package com.wallo.asset.dto;

import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class ReportDto {

    private ReportDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Insight {
        private String reportTitle;
        private String reportContent;
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
            return Objects.toString(category, "ETC").toUpperCase();
        }
    }
}
