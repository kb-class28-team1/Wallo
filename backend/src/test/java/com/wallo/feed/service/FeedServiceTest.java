package com.wallo.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.feed.analysis.FeedAnalysisClient;
import com.wallo.feed.domain.Feed;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.CategoryExpenseAverage;
import com.wallo.feed.mapper.FeedMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class FeedServiceTest {

    private final FeedMapper feedMapper = org.mockito.Mockito.mock(FeedMapper.class);
    private final FeedAnalysisClient analysisClient = org.mockito.Mockito.mock(FeedAnalysisClient.class);
    private FeedService feedService;

    @BeforeEach
    void setUp() {
        feedService = new FeedService(feedMapper, analysisClient);
        when(feedMapper.isChallengeMember(7L, 10L)).thenReturn(1);
    }

    @Test
    void usesSixtyDayCategoryAverageWhenAiCannotEstimateAmount() {
        when(analysisClient.analyze(any(), eq("REDUCED"), eq("CAFE")))
                .thenReturn(new AnalysisResponse("REDUCED", "CAFE", 0, "금액을 확인하기 어렵습니다.", 0.2));
        when(feedMapper.findCategoryExpenseAverage(
                eq(7L), eq("CAFE"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(average(3, 4_500));

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "REDUCED", "CAFE");

        assertEquals(4_500, result.estimatedSavingAmount());
        assertTrue(result.summary().contains("최근 60일"));
        verify(feedMapper).findCategoryExpenseAverage(
                eq(7L), eq("CAFE"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void extendsHistoryToNinetyDaysWhenSixtyDaysHaveTooFewTransactions() {
        when(analysisClient.analyze(any(), eq("SAVED"), eq("FOOD")))
                .thenReturn(new AnalysisResponse("SAVED", "FOOD", 0, "금액을 확인하기 어렵습니다.", 0.1));
        when(feedMapper.findCategoryExpenseAverage(
                eq(7L), eq("FOOD"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(average(2, 10_000), average(3, 8_000));

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "SAVED", "FOOD");

        assertEquals(8_000, result.estimatedSavingAmount());
        assertTrue(result.summary().contains("최근 90일"));
        verify(feedMapper, org.mockito.Mockito.times(2)).findCategoryExpenseAverage(
                eq(7L), eq("FOOD"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void keepsAiAmountAndDoesNotApplyFallbackWhenAmountIsAlreadyKnown() {
        AnalysisResponse analysis = new AnalysisResponse("SAVED", "CAFE", 2_000, "분석 완료", 0.9);
        when(analysisClient.analyze(any(), eq("SAVED"), eq("CAFE"))).thenReturn(analysis);

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "SAVED", "CAFE");

        assertEquals(2_000, result.estimatedSavingAmount());
        verify(feedMapper, never()).findCategoryExpenseAverage(
                eq(7L), eq("CAFE"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void doesNotApplyCategoryAverageToSpentType() {
        when(analysisClient.analyze(any(), eq("SPENT"), eq("CAFE")))
                .thenReturn(new AnalysisResponse("SPENT", "CAFE", 0, "소비 유형은 썼다입니다.", 0.9));

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "SPENT", "CAFE");

        assertEquals(0, result.estimatedSavingAmount());
        verify(feedMapper, never()).findCategoryExpenseAverage(
                eq(7L), eq("CAFE"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void doesNotReplaceCalculatedZeroWithCategoryAverage() {
        PriceReferenceService priceReferenceService =
                org.mockito.Mockito.mock(PriceReferenceService.class);
        FeedService service = new FeedService(
                feedMapper, analysisClient, null, priceReferenceService, new ObjectMapper());
        AnalysisResponse raw = new AnalysisResponse(
                "REDUCED", "CAFE", 900, "분석 완료", 0.9);
        AnalysisResponse calculated = new AnalysisResponse(
                "REDUCED", "CAFE", 0, "시세 계산 완료", 0.9,
                java.util.List.of(), 2_000, 3_000, 0, java.util.List.of());
        when(analysisClient.analyze(any(), eq("REDUCED"), eq("CAFE"))).thenReturn(raw);
        when(priceReferenceService.enrich(raw)).thenReturn(calculated);

        AnalysisResponse result = service.analyze(7L, 10L, media(), "REDUCED", "CAFE");

        assertEquals(0, result.estimatedSavingAmount());
        verify(feedMapper, never()).findCategoryExpenseAverage(
                eq(7L), eq("CAFE"), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void rejectsFeedWithoutCaption() {
        assertThrows(IllegalArgumentException.class, () -> feedService.create(
                7L, 10L, media(), "REDUCED", "CAFE", null,
                "   ", 1_000, "분석 완료", 0.8));

        verify(feedMapper, never()).insertFeed(any());
    }

    @Test
    void storesCalculatedPriceEvidenceWhenCreatingFeed() {
        when(feedMapper.insertFeed(any(Feed.class))).thenAnswer(invocation -> {
            Feed feed = invocation.getArgument(0);
            feed.setId(51L);
            return 1;
        });
        String analysisDetails = """
                {
                  "spendingType":"SAVED",
                  "category":"FOOD",
                  "estimatedSavingAmount":2360,
                  "summary":"우유 구매를 줄였어요.",
                  "confidenceScore":0.9,
                  "detectedItems":[{
                    "itemName":"우유",
                    "brand":"서울우유",
                    "unit":"1L×1개",
                    "quantity":1,
                    "unitPrice":2360,
                    "totalValue":2360,
                    "confidence":0.9,
                    "evidence":"상품명 확인"
                  }],
                  "referenceValue":2360,
                  "actualCost":0,
                  "savingDifference":2360,
                  "priceReferences":[]
                }
                """;

        feedService.create(
                7L, 10L, media(), "SAVED", "FOOD", null,
                "오늘의 절약", 2_360, 2_360, "우유 구매를 줄였어요.", 0.9,
                "SAME", 2_360, null, analysisDetails);

        verify(feedMapper).insertAnalysis(
                eq(51L), eq("SAVED"), eq("FOOD"), eq(2_360),
                eq("우유 구매를 줄였어요."), eq(0.9),
                eq(2_360L), eq(0L), eq(2_360L), contains("서울우유"), eq("[]"));
    }

    private MultipartFile media() {
        return new MockMultipartFile("media", "feed.jpg", "image/jpeg", new byte[]{1});
    }

    private CategoryExpenseAverage average(long count, long amount) {
        CategoryExpenseAverage average = new CategoryExpenseAverage();
        average.setTransactionCount(count);
        average.setAverageAmount(amount);
        return average;
    }
}
