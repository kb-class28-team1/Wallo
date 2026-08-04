package com.wallo.asset.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public final class TransactionReconciliationDto {

    private TransactionReconciliationDto() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private long transactionId;
        private long amount;
        private LocalDate date;
        private LocalTime time;
        private String category;
    }
}
