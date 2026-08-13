package com.wallo.asset.service;

/**
 * Immutable CODEF source identity carried from collection to persistence.
 *
 * <p>The source transaction ID is retained for traceability while the
 * normalized SHA-256 key is used by the database unique constraint. The
 * canonical asset identifier is carried with the identity so local card,
 * account, and loan lookups do not normalize the same source value again.</p>
 */
public record TransactionSourceIdentity(
        String sourceType,
        String sourceOrganizationCode,
        String sourceTransactionId,
        String sourceDedupKey,
        String normalizedAssetIdentifier
) {

    public TransactionSourceIdentity {
        sourceType = required(sourceType, "source type");
        sourceOrganizationCode = required(sourceOrganizationCode, "organization code");
        sourceTransactionId = required(sourceTransactionId, "source transaction id");
        sourceDedupKey = required(sourceDedupKey, "source dedup key");
        normalizedAssetIdentifier = required(normalizedAssetIdentifier, "asset identifier");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}
