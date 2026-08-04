package com.wallo.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class CodefDto {

    private CodefDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String organization;
        private String institutionType;
        private String loginType;
        private String id;
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CardApprovalRequest {
        private String organization;
        private String loginType;
        private String id;
        private String password;
        private String startDate;
        private String endDate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankTransactionRequest {
        private String organization;
        private String loginType;
        private String id;
        private String password;
        private String account;
        private String startDate;
        private String endDate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Result result;
        private Object data;

        public static Response success(Object data) {
            return new Response(new Result("CF-00000", "성공", ""), data);
        }

        public static Response failure(String code, String message, String extraMessage) {
            return new Response(new Result(code, message, extraMessage), null);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String code;
        private String message;
        private String extraMessage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetData {
        private List<Account> accounts;
        private List<Loan> loans;
        private List<Card> cards;
        private List<Transaction> transactions;
        private List<AssetSnapshot> assetSnapshots;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetSnapshot {
        private String snapshotMonth;
        private String totalAssets;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {
        private String resAccountName;
        private String resAccount;
        private String resAccountDisplay;
        private String resAccountBalance;
        private String resAccountEvalAmount;
        private String resAccountCurrency;
        private String resAccountStatus;
        private String resAccountSubtype;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Loan {
        private String resLoanName;
        private String resLoanAccount;
        private String resLoanDisplay;
        private String resLoanBalance;
        private String resLoanStatus;
        private String resLoanCurrency;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {
        private String resCardName;
        private String resCardNo;
        private String resCardType;
        private String resCardState;
        private String resValidPeriod;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CardApproval {
        private String resCardNo;
        private String resUsedDate;
        private String resUsedTime;
        private String resApprovalNo;
        private String resMemberName;
        private String resUsedAmount;
        private String resMemberSector;
        private String resCardType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankTransaction {
        private String resAccount;
        private String resTrNo;
        private String resTrDate;
        private String resTrTime;
        private String resAccountIn;
        private String resAccountOut;
        private String resAccountDesc;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transaction {
        private String resAccount;
        private String resAccountTrNo;
        private String resAccountTrDate;
        private String resAccountTrTime;
        private String resAccountTrType;
        private String resAccountTrAmount;
        private String resAccountTrDesc;
        private String resAccountTrCategory;
        private String resCardNo;
        private String resCardApprovalNo;
        private String resUsedDate;
        private String resUsedTime;
        private String resUsedAmount;
        private String resUsedMerchantName;
        private String resUsedCategory;
        private String resLoanAccount;
        private String resLoanPaymentNo;
        private String resLoanPaymentDate;
        private String resLoanPaymentTime;
        private String resLoanPaymentAmount;
        private String resLoanPaymentCategory;
    }
}
