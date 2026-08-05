package com.wallo.asset.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MerchantNamePreprocessorTest {

    @Test
    void removesWhitespaceLowercasesAndRemovesCardIssuerPrefix() {
        assertEquals(
                "편의점",
                MerchantNamePreprocessor.preprocess(" 국민카드 편의점 ")
        );
        assertEquals(
                "starbuckscoffee",
                MerchantNamePreprocessor.preprocess("신한카드 Starbucks Coffee")
        );
    }

    @Test
    void keepsMerchantNameWhenItOnlyContainsCardIssuerPrefix() {
        assertEquals(
                "국민카드",
                MerchantNamePreprocessor.preprocess("국민카드")
        );
    }

    @Test
    void doesNotRemoveAnIssuerLikeMerchantNameWithoutASeparator() {
        assertEquals(
                "현대백화점",
                MerchantNamePreprocessor.preprocess("현대백화점")
        );
    }

    @Test
    void keepsNullAsNull() {
        assertNull(MerchantNamePreprocessor.preprocess(null));
    }

    @Test
    void contextUsesPreprocessedMerchantName() {
        ExpenseCategoryClassifier.Context context = new ExpenseCategoryClassifier.Context(
                "국민카드 서점",
                "서점",
                22_000L
        );

        assertEquals("서점", context.merchantName());
    }
}
