package com.wallo.asset.service;

import java.util.Locale;

/**
 * Converts CODEF and legacy card type values to the two card types used internally.
 */
public final class CardTypeNormalizer {

    public static final String CREDIT = "CREDIT";
    public static final String CHECK = "CHECK";

    private CardTypeNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Card type is required.");
        }

        String trimmed = value.trim();
        String upperCase = trimmed.toUpperCase(Locale.ROOT);
        if (CREDIT.equals(upperCase)
                || upperCase.startsWith(CREDIT + "/")
                || "신용/본인".equals(trimmed)) {
            return CREDIT;
        }
        if (CHECK.equals(upperCase)
                || upperCase.startsWith(CHECK + "/")
                || "체크/본인".equals(trimmed)
                || trimmed.startsWith("직불/")) {
            return CHECK;
        }
        if ("DEBIT".equals(upperCase) || "1".equals(trimmed)) {
            return CHECK;
        }

        throw new IllegalArgumentException("Unsupported card type: " + value);
    }
}
