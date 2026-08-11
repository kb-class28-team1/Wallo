package com.wallo.asset.service;

/**
 * Validates the local asset relation required by each external transaction source.
 *
 * <p>The relation IDs are internal database foreign keys. They are intentionally
 * nullable on the DTO because the opposite relation is empty for a transaction
 * (a card approval has no account ID, and an account transaction has no card ID),
 * but the relation matching the source type must always be present before a
 * service persists the transaction.</p>
 */
public final class TransactionRelationValidator {

    private static final String CARD_APPROVAL = "CARD_APPROVAL";
    private static final String BANK_TRANSACTION = "BANK_TRANSACTION";
    private static final String LOAN_TRANSACTION = "LOAN_TRANSACTION";
    private static final String STOCK_TRANSACTION = "STOCK_TRANSACTION";

    private TransactionRelationValidator() {
    }

    public static void validate(String sourceType, Long cardId, Long accountId) {
        if (CARD_APPROVAL.equals(sourceType)) {
            require(cardId, "cardId", sourceType);
            return;
        }

        if (BANK_TRANSACTION.equals(sourceType)
                || LOAN_TRANSACTION.equals(sourceType)
                || STOCK_TRANSACTION.equals(sourceType)) {
            require(accountId, "accountId", sourceType);
        }
    }

    private static void require(Long id, String fieldName, String sourceType) {
        if (id == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required for " + sourceType + " transaction."
            );
        }
    }
}
