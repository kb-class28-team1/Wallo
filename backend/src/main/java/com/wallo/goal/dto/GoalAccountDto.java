package com.wallo.goal.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class GoalAccountDto {

    private GoalAccountDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableAccount {
        private Long accountId;
        private String bankName;
        private String accountName;
        private String displayNumber;
        private String accountType;
        private long balance;
        private String currency;
        private boolean selected;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SelectionRequest {
        private Long accountId;
    }
}
