package com.wallo.goal.dto;

import com.wallo.goal.domain.FinancialGoal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class GoalDto {

    private GoalDto() {
    }

    @Getter
    @AllArgsConstructor
    public static class Response {
        private final Long goalId;
        private final Long conversationId;
        private final String title;
        private final String goalType;
        private final long targetAmount;
        private final LocalDate targetDate;
        private final String motivation;
        private final String priority;
        private final long initialAmount;
        private final long monthlyContribution;
        private final String status;
        private final LocalDateTime createdAt;
        private final LocalDateTime updatedAt;

        public static Response from(FinancialGoal goal) {
            if (goal == null) {
                return null;
            }

            return new Response(
                    goal.getGoalId(),
                    goal.getConversationId(),
                    goal.getTitle(),
                    goal.getGoalType(),
                    goal.getTargetAmount(),
                    goal.getTargetDate(),
                    goal.getMotivation(),
                    goal.getPriority(),
                    goal.getInitialAmount(),
                    goal.getMonthlyContribution(),
                    goal.getStatus(),
                    goal.getCreatedAt(),
                    goal.getUpdatedAt()
            );
        }
    }
}
