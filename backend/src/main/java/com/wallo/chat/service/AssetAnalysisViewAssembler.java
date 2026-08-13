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

        Map<String, Object> directionSource = map(calculation.get("direction"));
        AssetAnalysisView.DirectionInfo direction = directionSource.isEmpty()
                ? null
                : direction(directionSource);
        Object priorityActionSource = calculation.get("priorityActions");
        if (priorityActionSource == null) {
            priorityActionSource = directionSource.get("priorityActions");
        }
        List<AssetAnalysisView.PriorityAction> priorityActions = priorityActions(
                priorityActionSource, directionSource);

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
                texts(profile.get("data_quality_notes")),
                direction,
                priorityActions
        );
    }

    private AssetAnalysisView.DirectionInfo direction(Map<String, Object> source) {
        return new AssetAnalysisView.DirectionInfo(
                text(source.get("headline")),
                text(source.get("currentStage")),
                texts(source.get("reasons")),
                text(source.get("keep")),
                text(source.get("firstChange")),
                text(source.get("threeMonthDirection")),
                text(source.get("oneYearDirection")),
                texts(source.get("riskSignals")),
                texts(source.get("additionalInfo"))
        );
    }

    private List<AssetAnalysisView.PriorityAction> priorityActions(
            Object value,
            Map<String, Object> direction
    ) {
        List<AssetAnalysisView.PriorityAction> actions = priorityActionMaps(value);
        if (!actions.isEmpty()) {
            return actions;
        }

        List<AssetAnalysisView.PriorityAction> derived = new ArrayList<>();
        addAction(derived, "우선", "가장 먼저 바꿀 것", direction.get("firstChange"));
        addAction(derived, "3개월", "3개월 실행 방향", direction.get("threeMonthDirection"));
        addAction(derived, "1년", "1년 실행 방향", direction.get("oneYearDirection"));
        return derived;
    }

    private List<AssetAnalysisView.PriorityAction> priorityActionMaps(Object value) {
        List<AssetAnalysisView.PriorityAction> result = new ArrayList<>();
        for (Map<String, Object> item : maps(value)) {
            String description = text(item.get("description"));
            if (description == null) {
                description = text(item.get("detail"));
            }
            if (description == null) {
                continue;
            }
            result.add(new AssetAnalysisView.PriorityAction(
                    text(item.get("period")),
                    text(item.get("title")),
                    description
            ));
        }
        return result;
    }

    private void addAction(
            List<AssetAnalysisView.PriorityAction> actions,
            String period,
            String title,
            Object description
    ) {
        String text = text(description);
        if (text != null && !text.isBlank()) {
            actions.add(new AssetAnalysisView.PriorityAction(period, title, text));
        }
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
