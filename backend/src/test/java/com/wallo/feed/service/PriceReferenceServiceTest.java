package com.wallo.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.feed.domain.PriceReferenceRow;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import com.wallo.feed.mapper.PriceReferenceMapper;
import com.wallo.feed.price.ShoppingPriceCandidate;
import com.wallo.feed.price.ShoppingPriceClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
