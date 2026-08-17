package com.wallo.asset.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpenseCategoryClassifierTest {

    private ExpenseCategoryClassifier classifier;
    private CategoryClassificationClient categoryClassificationClient;

    @BeforeEach
    void setUp() {
        categoryClassificationClient = mock(CategoryClassificationClient.class);
        classifier = new ExpenseCategoryClassifier(List.of(
                new MerchantSectorCategoryRule(),
                new MerchantKeywordCategoryRule(),
                new AiCategoryRule(categoryClassificationClient)
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
    void classifiesKnownMerchantsBeforeAi() {
        assertEquals("CAFE", classify("스타벅스", "기타"));
        assertEquals("TRANSPORT", classify("카카오T", "기타"));
        assertEquals("SHOPPING", classify("쿠팡", "기타"));
        assertEquals("HEALTH", classify("동네 병원", "기타"));
        assertEquals("CULTURE", classify("CGV", "기타"));
    }

    @Test
    void appliesSpecificDeliveryKeywordBeforeBroadShoppingKeyword() {
        ExpenseCategoryClassifier.Result result = classifier.classify(
                new ExpenseCategoryClassifier.Context("쿠팡이츠", "온라인쇼핑")
        );

        assertEquals("DELIVERY", result.category());
        assertEquals("MERCHANT_KEYWORD", result.source());
    }

    @Test
    void sourceSectorClassifiesKnownMerchantSector() {
        assertEquals("FOOD", classify("동네식당", "요식/음료"));
        assertEquals("TRANSPORT", classify("SK에너지", "주유"));
        assertEquals("SHOPPING", classify("무신사", "온라인쇼핑"));
        assertEquals("SHOPPING", classify("편의점", "편의점"));
        assertEquals("CULTURE", classify("서점", "서점"));
        assertEquals("HOUSING", classify("통신요금", "통신"));
        assertEquals("HEALTH", classify("동네 의원", "의료"));
        assertEquals("HEALTH", classify("우리 약국", "약국"));
        assertEquals("CAFE", classify("커피 전문점", "카페"));
        assertEquals("TRANSPORT", classify("서울 택시", "택시"));
        assertEquals("TRANSPORT", classify("버스 정기권", "대중교통"));
        assertEquals("SHOPPING", classify("온라인몰", "온라인몰"));
        assertEquals("SHOPPING", classify("생활 쇼핑", "쇼핑"));
        assertEquals("HOUSING", classify("아파트 관리비", "관리비"));
    }

    @Test
    void livingServiceKeywordClassifiesBeforeAi() {
        ExpenseCategoryClassifier.Result result = classifier.classify(
                new ExpenseCategoryClassifier.Context("우리동네 세탁소", "기타", 18_000L)
        );

        assertEquals("LIVING", result.category());
        assertEquals("MERCHANT_KEYWORD", result.source());
        assertEquals("living-service-keyword-v1", result.classifierVersion());
    }

    @Test
    void unknownMerchantFallsBackToEtc() {
        ExpenseCategoryClassifier.Result result = classifier.classify(
                new ExpenseCategoryClassifier.Context("알 수 없는 상점", "미분류")
        );

        assertEquals("ETC", result.category());
        assertEquals("FALLBACK", result.source());
    }

    @Test
    void aiClassifiesOnlyAfterKeywordAndSectorRules() {
        org.mockito.Mockito.when(categoryClassificationClient.classify(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new CategoryClassificationDto.Response(
                        "LIVING",
                        new java.math.BigDecimal("0.8600")
                ));

        ExpenseCategoryClassifier.Result result = classifier.classify(
                new ExpenseCategoryClassifier.Context("알 수 없는 상점", "기타", 12_000L)
        );

        assertEquals("LIVING", result.category());
        assertEquals("AI", result.source());
    }

    private String classify(String merchantName, String sector) {
        return classifier.classify(
                new ExpenseCategoryClassifier.Context(merchantName, sector)
        ).category();
    }
}
