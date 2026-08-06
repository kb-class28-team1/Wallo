package com.wallo.asset.dto;

import com.wallo.asset.domain.AccountSubtype;
import com.wallo.asset.domain.GoalFundAvailability;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class GoalAssetContextDto {

    private GoalAssetContextDto() {
    }

    @Getter
    public static class Response {
        private final boolean hasConnectedAccounts;
        private final long readyAmount;
        private final long conditionalAmount;
        private final long riskAssetAmount;
        private final long excludedAmount;
        private final long unknownAmount;
        private final long debtAmount;
        private final List<Account> accounts;

        public Response(
                boolean hasConnectedAccounts,
                long readyAmount,
                long conditionalAmount,
                long riskAssetAmount,
                long excludedAmount,
                long unknownAmount,
                long debtAmount,
                List<Account> accounts
        ) {
            this.hasConnectedAccounts = hasConnectedAccounts;
            this.readyAmount = readyAmount;
            this.conditionalAmount = conditionalAmount;
            this.riskAssetAmount = riskAssetAmount;
            this.excludedAmount = excludedAmount;
            this.unknownAmount = unknownAmount;
            this.debtAmount = debtAmount;
            this.accounts = accounts == null ? new ArrayList<>() : new ArrayList<>(accounts);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Account {
        private final AccountSubtype subtype;
        private final String sourceSubtype;
        private final long amount;
        private final GoalFundAvailability availability;
    }

    /** MyBatis가 목표 컨텍스트 계산에 필요한 원본 계좌 값만 조회하는 내부 모델이다. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountRecord {
        private String accountType;
        private String accountSubtype;
        private long balance;
        private long evaluationAmount;
    }
}
