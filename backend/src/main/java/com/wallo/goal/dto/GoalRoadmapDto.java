package com.wallo.goal.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.wallo.goal.domain.GoalRoadmap;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class GoalRoadmapDto {
    private GoalRoadmapDto() {
    }

    @Getter
    @AllArgsConstructor
    public static class Response {
        private final Long roadmapId;
        private final Long goalId;
        private final String generationStatus;
        private final JsonNode roadmap;
        private final String failureReason;
        private final String promptVersion;
        private final Integer currentStepNumber;
        private final List<Integer> completedStepNumbers;
        private final LocalDateTime progressUpdatedAt;
        private final LocalDateTime generatedAt;

        public static Response from(
                GoalRoadmap value,
                JsonNode roadmap,
                List<Integer> completedStepNumbers
        ) {
            if (value == null) return null;
            return new Response(value.getRoadmapId(), value.getGoalId(),
                    value.getGenerationStatus(), roadmap, value.getFailureReason(),
                    value.getPromptVersion(), value.getCurrentStepNumber(),
                    completedStepNumbers, value.getProgressUpdatedAt(), value.getGeneratedAt());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class StepProgressRequest {
        private Boolean completed;
    }
}
