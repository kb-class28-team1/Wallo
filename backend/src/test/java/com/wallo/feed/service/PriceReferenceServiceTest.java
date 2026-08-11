package com.wallo.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.feed.domain.FoodCostReferenceRow;
import com.wallo.feed.domain.PriceReferenceRow;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import com.wallo.feed.mapper.FoodCostReferenceMapper;
import com.wallo.feed.mapper.PriceReferenceMapper;
import com.wallo.feed.price.RestaurantPriceCandidate;
import com.wallo.feed.price.RestaurantPriceClient;
import com.wallo.feed.price.ShoppingPriceCandidate;
import com.wallo.feed.price.ShoppingPriceClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class PriceReferenceServiceTest {
    private final PriceReferenceMapper mapper =
            org.mockito.Mockito.mock(PriceReferenceMapper.class);
    private final ShoppingPriceClient shoppingClient =
            org.mockito.Mockito.mock(ShoppingPriceClient.class);
    private PriceReferenceService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-11T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new PriceReferenceService(mapper, shoppingClient, Runnable::run, clock);
    }

    @Test
    void usesCachedPriceWithoutCallingShoppingApi() {
        when(mapper.findBestMatch("우유", "서울우유", "1l×1개", "FOOD"))
                .thenReturn(row("우유", "서울우유", "1L×1개", 2_360));

        AnalysisResponse result = service.enrich(analysis(
                "SAVED", 0, new DetectedItem(
                        "우유", "서울우유", "1L×1개", 2, 0, 0, 0.9, "두 개 확인")));

        assertEquals(4_720, result.referenceValue());
        assertEquals(4_720, result.savingDifference());
        assertEquals(4_720, result.estimatedSavingAmount());
        assertEquals(2_360, result.detectedItems().get(0).unitPrice());
        verify(shoppingClient, never()).search(anyString(), anyString(), anyString());
    }

    @Test
    void selectsLowestMatchingProductAndStoresIt() {
        PriceReferenceRow stored = row("우유", "서울우유", "1L×1개", 2_360);
        when(mapper.findBestMatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null, stored);
        when(mapper.upsert(any())).thenReturn(1);
        when(shoppingClient.search("우유", "서울우유", "1L×1개"))
                .thenReturn(List.of(
                        new ShoppingPriceCandidate(
                                "다른우유 1L", 1_000, "A몰", "https://example.com/wrong-brand", ""),
                        new ShoppingPriceCandidate(
                                "서울우유 나100% 2L", 1_500, "B몰", "https://example.com/wrong-unit", ""),
                        new ShoppingPriceCandidate(
                                "서울우유 나100% 1L", 2_800, "C몰", "https://example.com/high", ""),
                        new ShoppingPriceCandidate(
                                "서울우유 나100% 1L", 2_360, "D몰", "https://example.com/lowest", "")));

        AnalysisResponse result = service.enrich(analysis(
                "SAVED", 0, new DetectedItem(
                        "우유", "서울우유", "1L×1개", 1, 0, 0, 0.9, "상품명 확인")));

        assertEquals(2_360, result.referenceValue());
        ArgumentCaptor<PriceReferenceRow> rowCaptor = ArgumentCaptor.forClass(PriceReferenceRow.class);
        verify(mapper).upsert(rowCaptor.capture());
        assertEquals("https://example.com/lowest", rowCaptor.getValue().getSourceUrl());
    }

    @Test
    void convertsPackagePriceToSingleItemPriceBeforeStoring() {
        PriceReferenceRow stored = row("당고", "", "1꼬치", 543);
        stored.setCategory("CAFE");
        when(mapper.findBestMatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null, stored);
        when(mapper.upsert(any())).thenReturn(1);
        when(shoppingClient.search("당고", "", "1꼬치"))
                .thenReturn(List.of(new ShoppingPriceCandidate(
                        "모찌모찌 당고 23꼬치 1.035kg",
                        12_480,
                        "테스트몰",
                        "https://example.com/dango",
                        "")));

        AnalysisResponse analysis = new AnalysisResponse(
                "SAVED", "CAFE", 0, "당고를 직접 만들었습니다.", 0.9,
                List.of(new DetectedItem(
                        "당고", "", "1꼬치", 5, 0, 0, 0.9, "다섯 꼬치 확인")),
                0, 0, 0, List.of());

        AnalysisResponse result = service.enrich(analysis);

        ArgumentCaptor<PriceReferenceRow> rowCaptor = ArgumentCaptor.forClass(PriceReferenceRow.class);
        verify(mapper).upsert(rowCaptor.capture());
        assertEquals(543, rowCaptor.getValue().getLowestPrice());
        assertEquals(2_715, result.referenceValue());
        assertEquals(2_715, result.estimatedSavingAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"권", "그루", "벌", "대", "마리", "장", "켤레", "송이"})
    void convertsAdditionalKoreanCountUnits(String countUnit) {
        String unit = "1" + countUnit;
        PriceReferenceRow stored = row("테스트상품", "", unit, 1_000);
        when(mapper.findBestMatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null, stored);
        when(mapper.upsert(any())).thenReturn(1);
        when(shoppingClient.search("테스트상품", "", unit))
                .thenReturn(List.of(new ShoppingPriceCandidate(
                        "테스트상품 10" + countUnit,
                        10_000,
                        "테스트몰",
                        "https://example.com/product-" + countUnit,
                        "")));

        AnalysisResponse result = service.enrich(analysis(
                "SAVED", 0, new DetectedItem(
                        "테스트상품", "", unit, 2, 0, 0, 0.9, "두 개 확인")));

        ArgumentCaptor<PriceReferenceRow> rowCaptor = ArgumentCaptor.forClass(PriceReferenceRow.class);
        verify(mapper).upsert(rowCaptor.capture());
        assertEquals(1_000, rowCaptor.getValue().getLowestPrice());
        assertEquals(2_000, result.referenceValue());
    }

    @Test
    void searchesConvenienceCoffeeByCoreProductWords() {
        PriceReferenceRow stored = row("레쓰비캔커피", "", "250ml×1캔", 1_200);
        stored.setCategory("CAFE");
        when(mapper.findBestMatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null, stored);
        when(mapper.upsert(any())).thenReturn(1);
        when(shoppingClient.search(
                "레쓰비 캔커피(럭키플러스 카페라떼 250ml)", "", "250ml×1캔"))
                .thenReturn(List.of(
                        new ShoppingPriceCandidate(
                                "롯데 카페라떼 캔커피 250ml",
                                700, "테스트몰", "https://example.com/unrelated", ""),
                        new ShoppingPriceCandidate(
                                "레쓰비 카페라떼 240ml 1캔",
                                1_200, "테스트몰", "https://example.com/letsbe", "")));
        AnalysisResponse analysis = new AnalysisResponse(
                "SAVED", "CAFE", 0, "편의점 커피를 선택했습니다.", 0.9,
                List.of(new DetectedItem(
                        "레쓰비 캔커피(럭키플러스 카페라떼 250ml)", "", "250ml×1캔",
                        1, 0, 0, 0.9, "캔 제품 확인")),
                0, 0, 0, List.of());

        AnalysisResponse result = service.enrich(analysis);

        ArgumentCaptor<PriceReferenceRow> rowCaptor =
                ArgumentCaptor.forClass(PriceReferenceRow.class);
        verify(mapper).upsert(rowCaptor.capture());
        assertEquals("https://example.com/letsbe", rowCaptor.getValue().getSourceUrl());
        assertEquals(1_200, result.estimatedSavingAmount());
    }

    @Test
    void subtractsHomemadeIngredientCostFromRestaurantPrice() {
        FoodCostReferenceMapper foodMapper =
                org.mockito.Mockito.mock(FoodCostReferenceMapper.class);
        RestaurantPriceClient restaurantClient =
                org.mockito.Mockito.mock(RestaurantPriceClient.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-11T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        PriceReferenceService homemadeService = new PriceReferenceService(
                mapper, shoppingClient, foodMapper, restaurantClient, Runnable::run, clock);
        FoodCostReferenceRow stored = foodRow("당고", "1꼬치", 600, 2_500);
        when(foodMapper.findBestMatch("당고", "1꼬치", "CAFE"))
                .thenReturn(null, stored);
        when(foodMapper.upsert(any())).thenReturn(1);
        when(restaurantClient.search("당고", "1꼬치")).thenReturn(List.of(
                new RestaurantPriceCandidate(
                        "카페 수제 당고 1꼬치", 2_500, "카페A", "https://example.com/cafe-a"),
                new RestaurantPriceCandidate(
                        "당고 전문점 1꼬치", 3_000, "카페B", "https://example.com/cafe-b")));
        AnalysisResponse analysis = new AnalysisResponse(
                "REDUCED", "CAFE", 0, "당고를 직접 만들었습니다.", 0.9,
                List.of(new DetectedItem(
                        "당고", "", "1꼬치", 5, 0, 0, 0.9, "다섯 꼬치 확인",
                        "HOMEMADE", 600, 2_000, "쌀가루와 소스")),
                0, 0, 0, List.of());

        AnalysisResponse result = homemadeService.enrich(analysis);

        ArgumentCaptor<FoodCostReferenceRow> rowCaptor =
                ArgumentCaptor.forClass(FoodCostReferenceRow.class);
        verify(foodMapper).upsert(rowCaptor.capture());
        assertEquals(600, rowCaptor.getValue().getIngredientCost());
        assertEquals(2_500, rowCaptor.getValue().getRestaurantPrice());
        assertEquals(12_500, result.referenceValue());
        assertEquals(3_000, result.actualCost());
        assertEquals(9_500, result.savingDifference());
        assertEquals(9_500, result.estimatedSavingAmount());
        assertTrue(result.summary().contains("음식점 판매가 12,500원"));
        verify(shoppingClient, never()).search(anyString(), anyString(), anyString());
    }

    @Test
    void doesNotSearchLowConfidenceItems() {
        when(mapper.findBestMatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        AnalysisResponse result = service.enrich(analysis(
                "SAVED", 0, new DetectedItem(
                        "우유", "", "1L×1개", 1, 0, 0, 0.3, "흐릿함")));

        assertEquals(0, result.referenceValue());
        assertTrue(result.priceReferences().isEmpty());
        verify(shoppingClient, never()).search(anyString(), anyString(), anyString());
    }

    @Test
    void reducedTypeUsesReferenceValueWhenActualCostIsNotDetected() {
        when(mapper.findBestMatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(row("우유", "서울우유", "1L×1개", 2_360));

        AnalysisResponse result = service.enrich(analysis(
                "REDUCED", 0, new DetectedItem(
                        "우유", "서울우유", "1L×1개", 1, 0, 0, 0.9, "상품명 확인")));

        assertEquals(2_360, result.estimatedSavingAmount());
        assertEquals(2_360, result.savingDifference());
    }

    @Test
    void keepsCalculatedZeroWhenActualCostIsHigherThanReferenceValue() {
        when(mapper.findBestMatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(row("우유", "서울우유", "1L×1개", 2_360));

        AnalysisResponse result = service.enrich(analysis(
                "REDUCED", 3_000, new DetectedItem(
                        "우유", "서울우유", "1L×1개", 1, 0, 0, 0.9, "상품명 확인")));

        assertEquals(0, result.estimatedSavingAmount());
        assertEquals(0, result.savingDifference());
    }

    private AnalysisResponse analysis(
            String spendingType, long actualCost, DetectedItem item) {
        return new AnalysisResponse(
                spendingType, "FOOD", 900, "분석 완료", 0.9,
                List.of(item), 0, actualCost, 0, List.of());
    }

    private PriceReferenceRow row(
            String name, String brand, String unit, int price) {
        PriceReferenceRow row = new PriceReferenceRow();
        row.setNormalizedItemName(name);
        row.setDisplayItemName(brand + " " + name + " " + unit);
        row.setBrand(brand);
        row.setUnit(unit);
        row.setCategory("FOOD");
        row.setLowestPrice(price);
        row.setSource("테스트몰");
        row.setSourceUrl("https://example.com/product");
        row.setObservedAt(LocalDateTime.of(2026, 8, 11, 12, 0));
        row.setSearchConfidence(0.9);
        return row;
    }

    private FoodCostReferenceRow foodRow(
            String name, String unit, int ingredientCost, int restaurantPrice) {
        FoodCostReferenceRow row = new FoodCostReferenceRow();
        row.setNormalizedDishName(name);
        row.setDisplayDishName(name);
        row.setUnit(unit);
        row.setCategory("CAFE");
        row.setIngredientCost(ingredientCost);
        row.setRestaurantPrice(restaurantPrice);
        row.setRestaurantSource("테스트 카페");
        row.setRestaurantSourceUrl("https://example.com/menu");
        row.setIngredientBasis("재료 원가 테스트");
        row.setObservedAt(LocalDateTime.of(2026, 8, 11, 12, 0));
        row.setSearchConfidence(0.9);
        return row;
    }
}
