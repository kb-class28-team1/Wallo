package com.wallo.goal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.goal.dto.GoalInterviewDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalFeasibilityCalculatorTest {

    @Test
    void calculatesAchievableGoal() {
        GoalInterviewDto.Feasibility result = GoalFeasibilityCalculator.calculate(
                draft(1_000_000L, LocalDate.of(2026, 12, 7), 0L, 300_000L),
                LocalDate.of(2026, 8, 7)
        );

        assertEquals("ACHIEVABLE", result.getStatus());
        assertEquals(4, result.getRemainingMonths());
        assertEquals(250_000L, result.getRequiredMonthlyAmount());
        assertEquals(50_000L, result.getMonthlyGap());
    }

    @Test
    void marksGoalAsAdjustmentRequiredWhenMonthlyContributionIsInsufficient() {
        GoalInterviewDto.Feasibility result = GoalFeasibilityCalculator.calculate(
                draft(1_000_000L, LocalDate.of(2026, 12, 7), 0L, 100_000L),
                LocalDate.of(2026, 8, 7)
        );

        assertEquals("ADJUSTMENT_REQUIRED", result.getStatus());
        assertEquals(-150_000L, result.getMonthlyGap());
    }

    @Test
    void marksGoalAsAlreadyAchievedWhenCurrentAmountReachesTarget() {
        GoalInterviewDto.Feasibility result = GoalFeasibilityCalculator.calculate(
                draft(1_000_000L, LocalDate.of(2026, 12, 31), 1_000_000L, 0L),
                LocalDate.of(2026, 8, 7)
        );

        assertEquals("ALREADY_ACHIEVED", result.getStatus());
        assertEquals(0L, result.getRemainingAmount());
    }

    private GoalInterviewDto.Draft draft(
            Long targetAmount,
            LocalDate targetDate,
            Long currentAmount,
            Long monthlyContribution
    ) {
        return new GoalInterviewDto.Draft(
                "CONFIRMATION",
                "여행 자금",
                "TRAVEL",
                targetAmount,
                targetDate,
                null,
                null,
                currentAmount,
                monthlyContribution,
                List.of(),
                List.of(),
                false
        );
    }
}
