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

    /** 선택된 목표 계좌의 최신 잔액을 동기화하기 위한 내부 대상 정보다. */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncTarget {
        private Long connectionId;
        private Long institutionId;
        private String codefOrganizationCode;
        private String institutionName;
        private String financialGroupCode;
        private String financialGroupName;
        private String institutionType;
        private String logoUrl;
        private String loginType;
        private String loginId;
        private String loginPassword;
    }
}
