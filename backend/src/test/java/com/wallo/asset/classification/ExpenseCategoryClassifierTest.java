package com.wallo.asset.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpenseCategoryClassifierTest {

    private ExpenseCategoryClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ExpenseCategoryClassifier(List.of(
                new MerchantSectorCategoryRule(),
                new MerchantKeywordCategoryRule()
        ));
    }

    @Test
    void merchantKeywordTakesPriorityOverSector() {
        ExpenseCategoryClassifier.Result result = classifier.classify(
                new ExpenseCategoryClassifier.Context(" 배달의 민족 ", "요식/음료")
        );

        assertEquals("DELIVERY", result.category());
        assertEquals("MERCHANT_KEYWORD", result.source());
    }

    @Test
    void sourceSectorClassifiesKnownMerchantSector() {
        assertEquals("FOOD", classify("동네식당", "요식/음료"));
        assertEquals("TRANSPORT", classify("SK에너지", "주유"));
        assertEquals("SHOPPING", classify("무신사", "온라인쇼핑"));
        assertEquals("HOUSING", classify("통신요금", "통신"));
    }

    @Test
    void unknownMerchantFallsBackToEtc() {
        ExpenseCategoryClassifier.Result result = classifier.classify(
                new ExpenseCategoryClassifier.Context("알 수 없는 상점", "미분류")
        );

        assertEquals("ETC", result.category());
        assertEquals("FALLBACK", result.source());
    }

    private String classify(String merchantName, String sector) {
        return classifier.classify(
                new ExpenseCategoryClassifier.Context(merchantName, sector)
        ).category();
    }
}
