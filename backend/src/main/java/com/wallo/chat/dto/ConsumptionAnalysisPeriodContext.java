package com.wallo.chat.dto;

public record ConsumptionAnalysisPeriodContext(
        String type,
        String label,
        String startDate,
        String endDate,
        String compareStart,
        String compareEnd
) {
    public static ConsumptionAnalysisPeriodContext from(
            ConsumptionAnalysisView.PeriodInfo period
    ) {
        if (period == null) {
            return null;
        }
        return new ConsumptionAnalysisPeriodContext(
                period.type(), period.label(), period.startDate(), period.endDate(),
                period.compareStart(), period.compareEnd()
        );
    }
}
