package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TransactionSourceKeyGeneratorTest {

    private final TransactionSourceKeyGenerator generator = new TransactionSourceKeyGenerator();

    @Test
    void sameSourceIdentityProducesSameSha256Key() {
        String firstKey = generator.forCardApproval("0311", "4321-0000-0000-8765", "87654321");
        String secondKey = generator.forCardApproval("0311", "4321-0000-0000-8765", "87654321");

        assertEquals(firstKey, secondKey);
        assertEquals(64, firstKey.length());
    }

    @Test
    void sameCardNumberProducesSameKeyAfterReconnection() {
        String firstKey = generator.forCardApproval("0311", "4321-0000-0000-8765", "87654321");
        String secondKey = generator.forCardApproval("0311", "4321000000008765", "87654321");

        assertEquals(firstKey, secondKey);
    }

    @Test
    void bankTransactionKeyUsesStableAccountIdentity() {
        String firstKey = generator.forBankTransaction("0004", "123456-01-789012", "BANK-202607-0001");
        String sameKey = generator.forBankTransaction("0004", "12345601789012", "BANK-202607-0001");
        String otherAccountKey = generator.forBankTransaction("0004", "987654-01-321098", "BANK-202607-0001");

        assertEquals(firstKey, sameKey);
        assertNotEquals(firstKey, otherAccountKey);
        assertEquals(64, firstKey.length());
    }

    @Test
    void genericAssetTransactionKeyUsesSourceIdentity() {
        String firstKey = generator.forAssetTransaction(
                "LOAN_TRANSACTION", "0004", "STUDENT-LOAN-2021-001", "LOAN-202607-0001"
        );
        String sameKey = generator.forAssetTransaction(
                "LOAN_TRANSACTION", "0004", "STUDENTLOAN2021001", "LOAN-202607-0001"
        );
        String otherSourceTypeKey = generator.forAssetTransaction(
                "STOCK_TRANSACTION", "0004", "STUDENT-LOAN-2021-001", "LOAN-202607-0001"
        );

        assertEquals(firstKey, sameKey);
        assertNotEquals(firstKey, otherSourceTypeKey);
        assertEquals(64, firstKey.length());
    }

    @Test
    void assetIdentifierNormalizerUsesOneCanonicalCardAndAccountFormat() {
        assertEquals(
                "12345601789012",
                AssetIdentifierNormalizer.normalize(" 123456-01-789012 ", "account number")
        );
        assertEquals(
                "4321000000008765",
                AssetIdentifierNormalizer.normalize("4321 0000-0000 8765", "card number")
        );
    }

    @Test
    void assetIdentifierNormalizerRejectsMissingIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AssetIdentifierNormalizer.normalize("- -", "account number")
        );
    }
}
