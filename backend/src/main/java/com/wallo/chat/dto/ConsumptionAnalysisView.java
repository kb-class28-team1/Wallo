package com.wallo.chat.dto;

import java.util.List;
import java.util.Map;

/**
 * 프론트엔드 소비분석 블록이 사용하는 단일 응답 계약입니다.
 * AI 원본 계산값은 별도로 저장하고, 화면에는 이 계약만 노출합니다.
 */
public record ConsumptionAnalysisView(
        String focus,
        PeriodInfo period,
        boolean hasEnoughData,
        InsufficientInfo insufficient,
        SummaryInfo summary,
        SignalSet signals
) {
    public record PeriodInfo(
            String type,
            String label,
            String startDate,
            String endDate,
            String compareStart,
            String compareEnd
    ) {
    }

    public record InsufficientInfo(int txCount, long totalAmount, String message) {
    }

    public record SummaryInfo(
            long currentTotal,
            long previousTotal,
            long deltaAmount,
            Double deltaRate,
            boolean warningIncrease
    ) {
    }

    public record SignalSet(
            List<Map<String, Object>> categoryOverview,
            List<Map<String, Object>> criteriaEvaluations,
            List<Map<String, Object>> categorySpikes,
            List<Map<String, Object>> newSpendings,
            List<Map<String, Object>> oneTimeLarge,
            List<Map<String, Object>> repeatingCategories,
            Map<String, Object> budget,
            List<Map<String, Object>> recurringPatterns,
            List<Map<String, Object>> subscriptions,
            List<Map<String, Object>> positives,
            List<Map<String, Object>> streaks
    ) {
    }
}
