package com.wallo.goal.service;

import com.wallo.goal.dto.GoalInterviewDto;
import java.math.BigInteger;
import java.time.LocalDate;

/**
 * 저장된 목표 초안으로 달성 가능성을 계산한다.
 * Python 목표 인터뷰 서비스와 동일한 상태값과 계산 규칙을 사용한다.
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
                || draft.getCurrentAmount() == null
                || draft.getMonthlyContribution() == null) {
            return new GoalInterviewDto.Feasibility(
                    "INSUFFICIENT_INFORMATION",
                    null,
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
                    0L,
                    draft.getMonthlyContribution()
            );
        }

        LocalDate today = referenceDate == null ? LocalDate.now() : referenceDate;
        int remainingMonths = monthsUntil(today, draft.getTargetDate());
        if (remainingMonths <= 0) {
            return new GoalInterviewDto.Feasibility(
                    "ADJUSTMENT_REQUIRED",
                    remainingAmount,
                    0,
                    null,
                    null
            );
        }

        long requiredMonthlyAmount = (remainingAmount + remainingMonths - 1L)
                / remainingMonths;
        long monthlyGap = draft.getMonthlyContribution() - requiredMonthlyAmount;
        String status;
        if (draft.getMonthlyContribution() < requiredMonthlyAmount) {
            status = "ADJUSTMENT_REQUIRED";
        } else if (BigInteger.valueOf(draft.getMonthlyContribution())
                .multiply(BigInteger.TEN)
                .compareTo(BigInteger.valueOf(requiredMonthlyAmount).multiply(BigInteger.valueOf(11))) < 0) {
            status = "TIGHT";
        } else {
            status = "ACHIEVABLE";
        }

        return new GoalInterviewDto.Feasibility(
                status,
                remainingAmount,
                remainingMonths,
                requiredMonthlyAmount,
                monthlyGap
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
