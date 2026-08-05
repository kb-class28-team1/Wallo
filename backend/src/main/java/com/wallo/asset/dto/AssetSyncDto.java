package com.wallo.asset.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class AssetSyncDto {

    private AssetSyncDto() {
    }

    @Getter
    @AllArgsConstructor
    public static class Account {
        private final String number;
        private final String displayNumber;
        private final String name;
        private final String type;
        private final String subtype;
        private final long balance;
        private final long evaluationAmount;
        private final String currency;
        private final String status;
    }

    @Getter
    @AllArgsConstructor
    public static class Card {
        private final String number;
        private final String name;
        private final String type;
        private final String status;
        private final String validPeriod;
    }

    @Getter
    @AllArgsConstructor
    public static class AssetSnapshot {
        private final String month;
        private final long totalAssets;
    }

    @Getter
    public static class Transaction {
        private final long userId;
        private final Long cardId;
        private final Long accountId;
        private final String type;
        private final String category;
        private final long amount;
        private final String merchantName;
        private final String originalMerchantName;
        private final String originalSector;
        private final String approvalNo;
        private final LocalDate date;
        private final LocalTime time;
        private final String categorySource;
        private final java.math.BigDecimal categoryConfidence;
        private final String classifierVersion;
        private final String sourceType;
        private final String sourceOrganizationCode;
        private final String sourceTransactionId;
        private final String sourceDedupKey;

        public Transaction(
                long userId,
                Long cardId,
                Long accountId,
                String type,
                String category,
                long amount,
                String merchantName,
                String approvalNo,
                LocalDate date,
                LocalTime time
        ) {
            this(
                    userId, cardId, accountId, type, category, amount, merchantName,
                    merchantName, null, approvalNo, date, time,
                    "LEGACY", null, null, null, null, null, null
            );
        }

        public Transaction(
                long userId,
                Long cardId,
                Long accountId,
                String type,
                String category,
                long amount,
                String merchantName,
                String originalMerchantName,
                String originalSector,
                String approvalNo,
                LocalDate date,
                LocalTime time,
                String categorySource,
                java.math.BigDecimal categoryConfidence,
                String classifierVersion,
                String sourceType,
                String sourceOrganizationCode,
                String sourceTransactionId,
                String sourceDedupKey
        ) {
            this.userId = userId;
            this.cardId = cardId;
            this.accountId = accountId;
            this.type = type;
            this.category = category;
            this.amount = amount;
            this.merchantName = merchantName;
            this.originalMerchantName = originalMerchantName;
            this.originalSector = originalSector;
            this.approvalNo = approvalNo;
            this.date = date;
            this.time = time;
            this.categorySource = categorySource;
            this.categoryConfidence = categoryConfidence;
            this.classifierVersion = classifierVersion;
            this.sourceType = sourceType;
            this.sourceOrganizationCode = sourceOrganizationCode;
            this.sourceTransactionId = sourceTransactionId;
            this.sourceDedupKey = sourceDedupKey;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExistingClassification {
        private String category;
        private String categorySource;
        private java.math.BigDecimal categoryConfidence;
        private String classifierVersion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationCandidate {
        private long transactionId;
        private long amount;
        private LocalDate date;
        private LocalTime time;
        private String category;
    }
}
