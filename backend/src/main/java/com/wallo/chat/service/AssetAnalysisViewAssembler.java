package com.wallo.chat.service;

import com.wallo.chat.dto.AssetAnalysisView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AssetAnalysisViewAssembler {

    public AssetAnalysisView assemble(Map<String, Object> calculation) {
        if (calculation == null || calculation.isEmpty()) {
            return null;
        }

        Map<String, Object> metrics = map(calculation.get("calculatedMetrics"));
        Map<String, Object> profile = map(calculation.get("profile"));
        Map<String, Object> profileAssets = map(profile.get("assets"));
        Map<String, Object> profileCashflow = map(profile.get("cashflow"));

        Long totalAssets = firstNumber(
                metrics.get("totalAssetsKrw"),
                profileAssets.get("total_assets_krw")
        );
        Long totalDebt = firstNumber(
                metrics.get("totalDebtKrw"),
                profile.get("total_debt_krw")
        );
        Long netAssets = firstNumber(metrics.get("netAssetsKrw"), null);
        if (netAssets == null && totalAssets != null && totalDebt != null) {
            netAssets = totalAssets - totalDebt;
        }

        Long monthlyIncome = firstNumber(
                metrics.get("monthlyNetIncomeKrw"),
                profileIncome(profile).get("current_monthly_net_income_krw")
        );
        Long monthlySaving = firstNumber(
                metrics.get("monthlySavingKrw"),
                profileCashflow.get("monthly_saving_krw")
        );
        Long monthlyExpense = firstNumber(
                metrics.get("monthlyExpenseKrw"),
                monthlyExpense(profileCashflow)
        );
        Long monthlySurplus = firstNumber(metrics.get("monthlySurplusKrw"), null);
        if (monthlySurplus == null && monthlyIncome != null && monthlyExpense != null) {
            monthlySurplus = monthlyIncome - monthlyExpense;
        }

        Long annualSaving = firstNumber(metrics.get("annualSavingKrw"), null);
        if (annualSaving == null && monthlySaving != null) {
            annualSaving = monthlySaving * 12;
        }

        Double savingRate = decimal(metrics.get("savingRatePercent"));
        if (savingRate == null && monthlySaving != null
                && monthlyIncome != null && monthlyIncome != 0) {
            savingRate = roundOneDecimal(monthlySaving * 100.0 / monthlyIncome);
        }

        return new AssetAnalysisView(
                new AssetAnalysisView.SummaryInfo(totalAssets, totalDebt, netAssets),
                new AssetAnalysisView.CashFlowInfo(
                        monthlyIncome,
                        monthlySaving,
                        monthlyExpense,
                        monthlySurplus,
                        annualSaving,
                        savingRate
                ),
                composition(profileAssets.get("items"), totalAssets),
                texts(profile.get("data_quality_notes"))
        );
    }

    private List<AssetAnalysisView.AssetItem> composition(Object value, Long totalAssets) {
        List<AssetAnalysisView.AssetItem> result = new ArrayList<>();
        for (Map<String, Object> item : maps(value)) {
            result.add(assetItem(item, totalAssets));
        }
        return result;
    }

    private AssetAnalysisView.AssetItem assetItem(
            Map<String, Object> item,
            Long totalAssets
    ) {
        Long amount = number(item.get("amount_krw"));
        boolean estimated = false;
        if (amount == null) {
            amount = number(item.get("amount_krw_derived"));
            estimated = amount != null;
        }
        if (amount == null) {
            amount = number(item.get("amount_krw_derived_approx"));
            estimated = amount != null;
        }

        Map<String, Object> range = map(item.get("amount_range_krw"));
        Long amountMin = number(range.get("min"));
        Long amountMax = number(range.get("max"));
        estimated = estimated || amountMin != null || amountMax != null
                || item.get("share_percent_approx") != null;

        Double share = decimal(item.get("share_percent_approx"));
        if (share == null && amount != null && totalAssets != null && totalAssets > 0) {
            share = roundOneDecimal(amount * 100.0 / totalAssets);
        }

        return new AssetAnalysisView.AssetItem(
                text(item.get("name")),
                text(item.get("category")),
                amount,
                amountMin,
                amountMax,
                share,
                estimated
        );
    }

    private Map<String, Object> profileIncome(Map<String, Object> profile) {
        return map(profile.get("income"));
    }

    private Long monthlyExpense(Map<String, Object> cashflow) {
        Long expense = number(cashflow.get("monthly_expense_krw"));
        if (expense != null) {
            return expense;
        }
        Long fixed = number(cashflow.get("monthly_fixed_expense_krw"));
        Long variable = number(cashflow.get("monthly_variable_expense_krw"));
        if (fixed == null && variable == null) {
            return null;
        }
        return (fixed == null ? 0 : fixed) + (variable == null ? 0 : variable);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                result.add(map(item));
            }
        }
        return result;
    }

    private List<String> texts(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private Long firstNumber(Object primary, Object fallback) {
        Long value = number(primary);
        return value == null ? number(fallback) : value;
    }

    private Long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private Double decimal(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
