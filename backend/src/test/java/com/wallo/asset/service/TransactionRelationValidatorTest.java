package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TransactionRelationValidatorTest {

    @Test
    void requiresCardIdForCardApproval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransactionRelationValidator.validate("CARD_APPROVAL", null, null)
        );
    }

    @Test
    void requiresAccountIdForAccountBackedSources() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TransactionRelationValidator.validate("BANK_TRANSACTION", null, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> TransactionRelationValidator.validate("LOAN_TRANSACTION", null, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> TransactionRelationValidator.validate("STOCK_TRANSACTION", null, null)
        );
    }

    @Test
    void allowsOnlyTheRelationRequiredByTheSourceType() {
        assertDoesNotThrow(
                () -> TransactionRelationValidator.validate("CARD_APPROVAL", 10L, null)
        );
        assertDoesNotThrow(
                () -> TransactionRelationValidator.validate("BANK_TRANSACTION", null, 20L)
        );
        assertDoesNotThrow(
                () -> TransactionRelationValidator.validate("LOAN_TRANSACTION", null, 30L)
        );
        assertDoesNotThrow(
                () -> TransactionRelationValidator.validate("STOCK_TRANSACTION", null, 40L)
        );
    }
}
