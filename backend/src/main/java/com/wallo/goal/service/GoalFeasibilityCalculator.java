package com.wallo.goal.service;

import com.wallo.goal.dto.GoalInterviewDto;
import java.time.LocalDate;

/**
 * 저장된 목표 초안으로 목표 달성에 필요한 월 납입액을 계산한다.
 * Python 목표 인터뷰 서비스와 동일한 계산 규칙을 사용한다.
 */
public final class GoalFeasibilityCalculator {

    private GoalFeasibilityCalculator() {
    }

    public static GoalInterviewDto.Feasibility calculate(
            GoalInterviewDto.Draft draft,
            LocalDate referenceDate
    ) {
        if (draft == null) {
            return null;
        }

        if (draft.getTargetAmount() == null
                || draft.getTargetDate() == null
                || draft.getCurrentAmount() == null) {
            return new GoalInterviewDto.Feasibility(
                    "INSUFFICIENT_INFORMATION",
                    null,
                    null,
                    null
            );
        }

        long remainingAmount = Math.max(
                0L,
                draft.getTargetAmount() - draft.getCurrentAmount()
        );
        if (remainingAmount == 0L) {
            return new GoalInterviewDto.Feasibility(
                    "ALREADY_ACHIEVED",
                    0L,
                    0,
                    0L
            );
        }

        LocalDate today = referenceDate == null ? LocalDate.now() : referenceDate;
        int remainingMonths = monthsUntil(today, draft.getTargetDate());
        if (remainingMonths <= 0) {
            return new GoalInterviewDto.Feasibility(
                    "ADJUSTMENT_REQUIRED",
                    remainingAmount,
                    0,
                    null
            );
        }

        long requiredMonthlyAmount = (remainingAmount + remainingMonths - 1L)
                / remainingMonths;

        return new GoalInterviewDto.Feasibility(
                "CALCULATED",
                remainingAmount,
                remainingMonths,
                requiredMonthlyAmount
        );
    }

    static int monthsUntil(LocalDate start, LocalDate target) {
        int months = (target.getYear() - start.getYear()) * 12
                + target.getMonthValue() - start.getMonthValue();
        if (target.getDayOfMonth() > start.getDayOfMonth()) {
            months += 1;
        }
        return Math.max(0, months);
    }
}
