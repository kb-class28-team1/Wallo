package com.wallo.asset.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

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
    public static class Transaction {
        private final long userId;
        private final Long cardId;
        private final Long accountId;
        private final String type;
        private final String category;
        private final long amount;
        private final String merchantName;
        private final String approvalNo;
        private final LocalDate date;
        private final LocalTime time;
    }
}
