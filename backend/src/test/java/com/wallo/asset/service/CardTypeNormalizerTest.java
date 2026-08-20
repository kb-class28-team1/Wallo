package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CardTypeNormalizerTest {

    @Test
    void normalizesSupportedCardTypes() {
        assertEquals("CREDIT", CardTypeNormalizer.normalize("CREDIT"));
        assertEquals("CREDIT", CardTypeNormalizer.normalize("CREDIT/본인"));
        assertEquals("CREDIT", CardTypeNormalizer.normalize("신용/본인"));
        assertEquals("CHECK", CardTypeNormalizer.normalize("CHECK"));
        assertEquals("CHECK", CardTypeNormalizer.normalize("CHECK/본인"));
        assertEquals("CHECK", CardTypeNormalizer.normalize("체크/본인"));
        assertEquals("CHECK", CardTypeNormalizer.normalize("직불/본인"));
        assertEquals("CHECK", CardTypeNormalizer.normalize("DEBIT"));
        assertEquals("CHECK", CardTypeNormalizer.normalize("1"));
    }

    @Test
    void normalizesCaseAndWhitespaceForCanonicalValues() {
        assertEquals("CREDIT", CardTypeNormalizer.normalize(" credit "));
        assertEquals("CHECK", CardTypeNormalizer.normalize(" check/본인 "));
    }

    @Test
    void rejectsUnknownOrMissingCardTypes() {
        assertThrows(IllegalArgumentException.class, () -> CardTypeNormalizer.normalize("PREPAID"));
        assertThrows(IllegalArgumentException.class, () -> CardTypeNormalizer.normalize(""));
        assertThrows(IllegalArgumentException.class, () -> CardTypeNormalizer.normalize(null));
    }
}
