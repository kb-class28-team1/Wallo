package com.wallo.asset.service;

/**
 * Canonicalizes external card and account identifiers before they are stored
 * or used for local-asset lookups.
 *
 * CODEF may format the same identifier with hyphens or spaces depending on
 * the endpoint. The request value sent back to CODEF can keep its original
 * formatting; this class is for the stable local identity only.
 */
public final class AssetIdentifierNormalizer {

    private AssetIdentifierNormalizer() {
    }

    public static String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        String normalized = value.trim()
                .replace("-", "")
                .replace(" ", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }
}
