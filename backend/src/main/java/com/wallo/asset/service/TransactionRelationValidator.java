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

    private TransactionRelationValidator() {
    }

    public static void validate(String sourceType, Long cardId, Long accountId) {
        if (AssetTransactionConstants.CARD_APPROVAL_SOURCE_TYPE.equals(sourceType)) {
            require(cardId, "cardId", sourceType);
            return;
        }

        if (AssetTransactionConstants.BANK_TRANSACTION_SOURCE_TYPE.equals(sourceType)
                || AssetTransactionConstants.LOAN_TRANSACTION_SOURCE_TYPE.equals(sourceType)
                || AssetTransactionConstants.STOCK_TRANSACTION_SOURCE_TYPE.equals(sourceType)) {
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
