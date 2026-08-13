package com.wallo.chat.dto;

import java.util.List;

/**
 * Frontend-facing contract for asset analysis cards.
 */
public record AssetAnalysisView(
        SummaryInfo summary,
        CashFlowInfo cashflow,
        List<AssetItem> composition,
        List<String> dataQualityNotes
) {
    public record SummaryInfo(
            Long totalAssetsKrw,
            Long totalDebtKrw,
            Long netAssetsKrw
    ) {
    }

    public record CashFlowInfo(
            Long monthlyNetIncomeKrw,
            Long monthlySavingKrw,
            Long monthlyExpenseKrw,
            Long monthlySurplusKrw,
            Long annualSavingKrw,
            Double savingRatePercent
    ) {
    }

    public record AssetItem(
            String name,
            String category,
            Long amountKrw,
            Long amountMinKrw,
            Long amountMaxKrw,
            Double sharePercent,
            boolean estimated
    ) {
    }
}
