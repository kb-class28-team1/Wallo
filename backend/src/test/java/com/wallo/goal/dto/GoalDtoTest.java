package com.wallo.goal.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.goal.domain.FinancialGoal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GoalDtoTest {

    @Test
    void calculatesAchievementRateFromCurrentAmount() {
        FinancialGoal goal = goal(2_000_000L, 3_250_000L, 10_000_000L);

        GoalDto.Response response = GoalDto.Response.from(goal);

        assertEquals(2_000_000L, response.getInitialAmount());
        assertEquals(3_250_000L, response.getCurrentAmount());
        assertEquals(33, response.getAchievementRate());
    }

    @Test
    void capsAchievementRateAtOneHundredPercent() {
        GoalDto.Response response = GoalDto.Response.from(
                goal(2_000_000L, 12_000_000L, 10_000_000L)
        );

        assertEquals(100, response.getAchievementRate());
    }

    @Test
    void returnsZeroAchievementRateForNonPositiveTarget() {
        GoalDto.Response response = GoalDto.Response.from(
                goal(2_000_000L, 2_000_000L, 0L)
        );

        assertEquals(0, response.getAchievementRate());
    }

    @Test
    void treatsNegativeCurrentAmountAsZero() {
        GoalDto.Response response = GoalDto.Response.from(
                goal(2_000_000L, -100L, 10_000_000L)
        );

        assertEquals(-100L, response.getCurrentAmount());
        assertEquals(0, response.getAchievementRate());
    }

    private FinancialGoal goal(
            long initialAmount,
            long currentAmount,
            long targetAmount
    ) {
        FinancialGoal goal = new FinancialGoal();
        goal.setGoalId(31L);
        goal.setConversationId(11L);
        goal.setTitle("비상금 마련");
        goal.setGoalType("EMERGENCY_FUND");
        goal.setInitialAmount(initialAmount);
        goal.setCurrentAmount(currentAmount);
        goal.setTargetAmount(targetAmount);
        goal.setTargetDate(LocalDate.of(2027, 8, 1));
        goal.setRequiredMonthlyAmount(600_000L);
        goal.setStatus("ACTIVE");
        return goal;
    }
}
