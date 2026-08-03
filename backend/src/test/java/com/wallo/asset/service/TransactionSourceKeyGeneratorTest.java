package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class TransactionSourceKeyGeneratorTest {

    private final TransactionSourceKeyGenerator generator = new TransactionSourceKeyGenerator();

    @Test
    void sameSourceIdentityProducesSameSha256Key() {
        String firstKey = generator.forCardApproval("0311", 25L, "87654321");
        String secondKey = generator.forCardApproval("0311", 25L, "87654321");

        assertEquals(firstKey, secondKey);
        assertEquals(64, firstKey.length());
    }

    @Test
    void differentCardsProduceDifferentKeysForSameApprovalNumber() {
        String firstKey = generator.forCardApproval("0311", 25L, "87654321");
        String secondKey = generator.forCardApproval("0311", 26L, "87654321");

        assertNotEquals(firstKey, secondKey);
    }
}
