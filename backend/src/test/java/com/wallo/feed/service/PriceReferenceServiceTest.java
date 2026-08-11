package com.wallo.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.feed.analysis.PriceSearchClient;
import com.wallo.feed.domain.PriceReferenceRow;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import com.wallo.feed.mapper.PriceReferenceMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PriceReferenceServiceTest {
    private final PriceReferenceMapper mapper = org.mockito.Mockito.mock(PriceReferenceMapper.class);
    private final PriceSearchClient searchClient = org.mockito.Mockito.mock(PriceSearchClient.class);
    private final PriceReferenceService service = new PriceReferenceService(mapper, searchClient);

    @Test
    void searchesCacheMissStoresLowestPriceAndCalculatesDifference() {
        DetectedItem item = new DetectedItem("생수", "", "개", 2, 0, 0, 0.9, "상품명이 보임");
        PriceReferenceRow stored = row(1_200);
        when(mapper.findByKey("생수", null, "개")).thenReturn(null, stored);
        when(searchClient.findLowestPrice("생수", null, "개", "SHOPPING"))
                .thenReturn(Optional.of(new PriceSearchClient.PriceSearchResult(
                        "생수", null, "개", 1_200, "판매 페이지", "https://example.com/water", 0.9)));

        AnalysisResponse result = service.enrich(new AnalysisResponse(
                "REDUCED", "SHOPPING", 0, "물품이 확인되었습니다.", 0.6,
                List.of(item), 0, 1_000, 0, List.of()));

        assertEquals(2_400, result.referenceValue());
        assertEquals(1_400, result.savingDifference());
        assertEquals(1_400, result.estimatedSavingAmount());
        verify(mapper).upsert(any(PriceReferenceRow.class));
    }

    private PriceReferenceRow row(int price) {
        PriceReferenceRow row = new PriceReferenceRow();
        row.setLowestPrice(price);
        row.setBrand(null);
        row.setUnit("개");
        row.setSource("판매 페이지");
        row.setSourceUrl("https://example.com/water");
        row.setObservedAt(java.time.LocalDateTime.now());
        return row;
    }
}
