package com.wallo.chat.service;

import com.wallo.chat.dto.ConsumptionAnalysisView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConsumptionAnalysisViewAssembler {

    public ConsumptionAnalysisView assemble(Map<String, Object> calculation) {
        if (calculation == null || calculation.isEmpty()) {
            return null;
        }
        Map<String, Object> sufficiency = map(calculation.get("dataSufficiency"));
        boolean enough = bool(sufficiency.get("sufficient"));
        ConsumptionAnalysisView.PeriodInfo period = period(calculation);
        ConsumptionAnalysisView.InsufficientInfo insufficient = enough ? null
                : new ConsumptionAnalysisView.InsufficientInfo(
                        integer(sufficiency.get("transactionCount")),
                        number(sufficiency.get("totalExpense")),
                        text(calculation.get("message"))
                );

        Map<String, Object> totalChange = map(calculation.get("totalChange"));
        ConsumptionAnalysisView.SummaryInfo summary = totalChange.isEmpty() ? null
                : new ConsumptionAnalysisView.SummaryInfo(
                        number(totalChange.get("currentAmount")),
                        number(totalChange.get("previousAmount")),
                        number(totalChange.get("changeAmount")),
                        decimal(totalChange.get("changeRatePercent")),
                        bool(totalChange.get("warning"))
                );

        return new ConsumptionAnalysisView(
                defaultText(calculation.get("focus"), "OVERVIEW"),
                period,
                enough,
                insufficient,
                summary,
                signals(calculation, summary)
        );
    }

    private ConsumptionAnalysisView.PeriodInfo period(Map<String, Object> calculation) {
        String type = text(calculation.get("periodType"));
        Map<String, Object> analysis = map(calculation.get("analysisPeriod"));
        Map<String, Object> comparison = map(calculation.get("comparisonPeriod"));
        String label = text(calculation.get("periodLabel"));
        if (label == null || label.isBlank()) {
            label = "WEEKLY".equals(type) ? "이번 주"
                    : "CUSTOM".equals(type) ? "지정 기간" : "이번 달";
        }
        return new ConsumptionAnalysisView.PeriodInfo(
                type, label,
                text(analysis.get("start")), text(analysis.get("end")),
                text(comparison.get("start")), text(comparison.get("end"))
        );
    }

    private ConsumptionAnalysisView.SignalSet signals(
            Map<String, Object> calculation,
            ConsumptionAnalysisView.SummaryInfo summary
    ) {
        List<Map<String, Object>> categoryOverview = transformList(
                calculation.get("categoryOverview"), item -> linked(
                        "categoryCode", item.get("category"),
                        "transactionCount", item.get("transactionCount"),
                        "maxTransactionAmount", item.get("maxTransactionAmount"),
                        "previous", item.get("previousAmount"),
                        "current", item.get("currentAmount"),
                        "deltaAmount", item.get("changeAmount"),
                        "deltaRate", item.get("changeRatePercent")
                ));
        List<Map<String, Object>> criteriaEvaluations = maps(
                calculation.get("criteriaEvaluations"));
        List<Map<String, Object>> spikes = transformList(
                calculation.get("categorySurges"), item -> linked(
                        "categoryCode", item.get("category"),
                        "previous", item.get("previousAmount"),
                        "current", item.get("currentAmount"),
                        "deltaAmount", item.get("changeAmount"),
                        "deltaRate", item.get("changeRatePercent")
                ));
        List<Map<String, Object>> newSpendings = transformList(
                calculation.get("newSpending"), item -> linked(
                        "categoryCode", item.get("category"),
                        "current", item.get("amount"),
                        "txCount", item.get("count"),
                        "important", true
                ));
        List<Map<String, Object>> oneTime = transformList(
                calculation.get("oneOffHighSpending"), item -> linked(
                        "categoryCode", item.get("category"),
                        "maxTransactionAmount", item.get("maxTransactionAmount"),
                        "categoryAmount", item.get("categoryAmount"),
                        "shareOfTotalPercent", percentage(
                                item.get("categoryAmount"),
                                summary == null ? 0 : summary.currentTotal()
                        ),
                        "excludedFromMission", item.get("limitAutomaticReductionMission")
                ));
        List<Map<String, Object>> repeatingCategories = transformList(
                calculation.get("repeatingCategories"), item -> linked(
                        "categoryCode", item.get("category"),
                        "amount", item.get("amount"),
                        "transactionCount", item.get("count"),
                        "maxTransactionAmount", item.get("max")
                ));
        Map<String, Object> budgetSource = map(calculation.get("budgetStatus"));
        Map<String, Object> budget = budgetSource.isEmpty() ? null : linked(
                "hasBudget", true,
                "budgetAmount", budgetSource.get("budgetAmount"),
                "spent", summary == null ? 0 : summary.currentTotal(),
                "usageRate", budgetSource.get("usageRatePercent"),
                "monthProgress", budgetSource.get("monthProgressPercent"),
                "gap", budgetSource.get("gapPercentagePoints"),
                "status", budgetSource.get("status")
        );

        List<Map<String, Object>> patterns = patterns(map(calculation.get("patterns")));
        List<Map<String, Object>> subscriptions = transformList(
                calculation.get("recurringPaymentCandidates"), item -> linked(
                        "merchant", item.get("merchant"),
                        "amount", item.get("typicalAmount"),
                        "count", item.get("count"),
                        "confirmationRequired", item.get("confirmationRequired")
                ));
        List<Map<String, Object>> positives = transformList(
                calculation.get("positiveImprovements"), item -> linked(
                        "categoryCode", item.get("category"),
                        "previous", item.get("previousAmount"),
                        "current", item.get("currentAmount"),
                        "decreaseRate", absolute(item.get("changeRatePercent")),
                        "decreaseAmount", absolute(item.get("changeAmount"))
                ));
        List<Map<String, Object>> streaks = new ArrayList<>();
        if (bool(map(calculation.get("continuousImprovement"))
                .get("twoConsecutiveMonthsDecreased"))) {
            streaks.add(linked(
                    "kind", "MONTHLY_DECREASE",
                    "label", "조절 가능 소비",
                    "streakCount", 2
            ));
        }
        return new ConsumptionAnalysisView.SignalSet(
                categoryOverview, criteriaEvaluations,
                spikes, newSpendings, oneTime, repeatingCategories, budget, patterns,
                subscriptions, positives, streaks
        );
    }

    private List<Map<String, Object>> patterns(Map<String, Object> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : maps(source.get("weekdayHabits"))) {
            result.add(linked(
                    "type", "WEEKDAY",
                    "weekday", item.get("weekday"),
                    "repeatWeeks", item.get("weeks"),
                    "transactionCount", item.get("transactionCount")
            ));
        }
        Map<String, Object> weekend = map(source.get("weekendConcentration"));
        if (bool(weekend.get("detected"))) {
            result.add(linked(
                    "type", "WEEKEND",
                    "weekdayAvg", weekend.get("weekdayDailyAverage"),
                    "weekendAvg", weekend.get("weekendDailyAverage")
            ));
        }
        for (Map<String, Object> item : maps(source.get("timeSlotHabits"))) {
            result.add(linked(
                    "type", "TIME_OF_DAY",
                    "timeSlot", item.get("timeSlot"),
                    "repeatWeeks", item.get("weeks"),
                    "transactionCount", item.get("transactionCount")
            ));
        }
        return result;
    }

    private List<Map<String, Object>> transformList(
            Object value,
            java.util.function.Function<Map<String, Object>, Map<String, Object>> mapper
    ) {
        return maps(value).stream().map(mapper).toList();
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

    private Map<String, Object> linked(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private Double decimal(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private double absolute(Object value) {
        Double decimal = decimal(value);
        return decimal == null ? 0 : Math.abs(decimal);
    }

    private double percentage(Object part, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round(number(part) * 1000.0 / total) / 10.0;
    }

    private boolean bool(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultText(Object value, String fallback) {
        String result = text(value);
        return result == null || result.isBlank() ? fallback : result;
    }
}
