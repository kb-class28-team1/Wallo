package com.wallo.asset.dto;

import java.util.List;

public record AssetAnalysisContextDto(
        long totalAssets,
        long totalDebt,
        long netAssets,
        long monthlyIncome,
        long monthlyExpense,
        long monthlySaving,
        Double savingRatePercent,
        List<AssetComposition> assetComposition,
        String asOf
) {

    public AssetAnalysisContextDto {
        assetComposition = assetComposition == null
                ? List.of()
                : List.copyOf(assetComposition);
    }

    public record AssetComposition(
            String category,
            long amount,
            double sharePercent
    ) {
    }
}
