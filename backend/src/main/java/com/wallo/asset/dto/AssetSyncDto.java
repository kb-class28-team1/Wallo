package com.wallo.asset.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class AssetSyncDto {

    private AssetSyncDto() {
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncTarget {
        private Long userId;
        private Long connectionId;
        private Long institutionId;
        private String codefOrganizationCode;
        private String institutionName;
        private String financialGroupCode;
        private String financialGroupName;
        private String institutionType;
        private String logoUrl;
        private String loginId;
    }

    @Getter
    @AllArgsConstructor
    public static class SyncResponse {
        private final LocalDateTime syncedAt;
        private final int inserted;
        private final int updated;
        private final int failedConnections;
        private final int fallbackCount;

        public SyncResponse(
                LocalDateTime syncedAt,
                int inserted,
                int updated,
                int failedConnections
        ) {
            this(syncedAt, inserted, updated, failedConnections, 0);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class SyncStats {
        private final int inserted;
        private final int updated;
        private final int fallbackCount;

        public SyncStats(int inserted, int updated) {
            this(inserted, updated, 0);
        }

        public static SyncStats empty() {
            return new SyncStats(0, 0, 0);
        }

        public int total() {
            return inserted + updated;
        }

        public SyncStats plus(SyncStats other) {
            if (other == null) {
                return this;
            }
            return new SyncStats(
                    inserted + other.inserted,
                    updated + other.updated,
                    fallbackCount + other.fallbackCount
            );
        }
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
            this.sourceType = requiredSourceField(sourceType, "sourceType");
            this.sourceOrganizationCode = requiredSourceField(sourceOrganizationCode, "sourceOrganizationCode");
            this.sourceTransactionId = requiredSourceField(sourceTransactionId, "sourceTransactionId");
            this.sourceDedupKey = requiredSourceField(sourceDedupKey, "sourceDedupKey");
        }

        private String requiredSourceField(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " is required for a persisted transaction.");
            }
            return value.trim();
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
