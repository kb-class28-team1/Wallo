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

    @Test
    void bankTransactionKeyIncludesAccountIdentity() {
        String firstKey = generator.forBankTransaction("0004", 17L, "BANK-202607-0001");
        String sameKey = generator.forBankTransaction("0004", 17L, "BANK-202607-0001");
        String otherAccountKey = generator.forBankTransaction("0004", 18L, "BANK-202607-0001");

        assertEquals(firstKey, sameKey);
        assertNotEquals(firstKey, otherAccountKey);
        assertEquals(64, firstKey.length());
    }
}
