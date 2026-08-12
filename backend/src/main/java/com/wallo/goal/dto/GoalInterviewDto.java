package com.wallo.goal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class GoalInterviewDto {

    private GoalInterviewDto() {
    }

    public enum Action {
        CONTINUE,
        CONFIRM,
        CANCEL
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Draft {
        private String state;
        private String title;
        private String goalType;
        private Long targetAmount;
        private LocalDate targetDate;
        private String motivation;
        private String priority;
        private Long currentAmount;
        private List<String> missingFields = new ArrayList<>();
        private List<String> assumptions = new ArrayList<>();
        private boolean confirmed;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Feasibility {
        private String status;
        private Long remainingAmount;
        private Integer remainingMonths;
        private Long requiredMonthlyAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Result {
        private Action action;
        private boolean active;
        private Draft draft;
        private Feasibility feasibility;
        private JsonNode roadmap;
        private String roadmapError;

        public Result(Action action, boolean active, Draft draft, Feasibility feasibility) {
            this(action, active, draft, feasibility, null, null);
        }

        public Result(
                Action action,
                boolean active,
                Draft draft,
                Feasibility feasibility,
                JsonNode roadmap,
                String roadmapError
        ) {
            this.action = action;
            this.active = active;
            this.draft = draft;
            this.feasibility = feasibility;
            this.roadmap = roadmap;
            this.roadmapError = roadmapError;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ActiveDraftResponse {
        private final boolean active;
        private final Draft draft;
        private final Feasibility feasibility;
    }
}
