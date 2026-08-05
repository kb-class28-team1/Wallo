package com.wallo.asset.client;

public final class AssetReportAiDto {

    private AssetReportAiDto() {
    }

    public record Request(
            String category,
            String categoryLabel,
            long currentAmount,
            long previousAmount
    ) {
    }

    public record Response(
            String reportTitle,
            String reportContent
    ) {
    }
}
