package com.wallo.goal.dto;

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
    public static class Draft {
        private String state;
        private String title;
        private String goalType;
        private Long targetAmount;
        private LocalDate targetDate;
        private String motivation;
        private String priority;
        private Long currentAmount;
        private Long monthlyContribution;
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
        private Long monthlyGap;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private Action action;
        private boolean active;
        private Draft draft;
        private Feasibility feasibility;
    }

    @Getter
    @AllArgsConstructor
    public static class ActiveDraftResponse {
        private final boolean active;
        private final Draft draft;
        private final Feasibility feasibility;
    }
}
