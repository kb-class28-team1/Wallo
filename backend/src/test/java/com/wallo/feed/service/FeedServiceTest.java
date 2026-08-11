package com.wallo.feed.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.feed.analysis.FeedAnalysisClient;
import com.wallo.feed.domain.Feed;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.mapper.FeedMapper;
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
    void keepsZeroWhenAiCannotEstimateAmount() {
        when(analysisClient.analyze(any(), eq("REDUCED"), eq("CAFE")))
                .thenReturn(new AnalysisResponse("REDUCED", "CAFE", 0, "금액을 확인하기 어렵습니다.", 0.2));

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "REDUCED", "CAFE");

        assertEquals(0, result.estimatedSavingAmount());
        verify(feedMapper, never()).findCategoryExpenseAverage(any(), any(), any(), any());
    }

    @Test
    void keepsZeroForSavedTypeWithoutAnAiAmount() {
        when(analysisClient.analyze(any(), eq("SAVED"), eq("FOOD")))
                .thenReturn(new AnalysisResponse("SAVED", "FOOD", 0, "금액을 확인하기 어렵습니다.", 0.1));

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "SAVED", "FOOD");

        assertEquals(0, result.estimatedSavingAmount());
        verify(feedMapper, never()).findCategoryExpenseAverage(any(), any(), any(), any());
    }

    @Test
    void keepsAiAmountAndDoesNotApplyFallbackWhenAmountIsAlreadyKnown() {
        AnalysisResponse analysis = new AnalysisResponse("SAVED", "CAFE", 2_000, "분석 완료", 0.9);
        when(analysisClient.analyze(any(), eq("SAVED"), eq("CAFE"))).thenReturn(analysis);

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "SAVED", "CAFE");

        assertEquals(2_000, result.estimatedSavingAmount());
        verify(feedMapper, never()).findCategoryExpenseAverage(
                eq(7L), eq("CAFE"), any(), any());
    }

    @Test
    void doesNotApplyCategoryAverageToSpentType() {
        when(analysisClient.analyze(any(), eq("SPENT"), eq("CAFE")))
                .thenReturn(new AnalysisResponse("SPENT", "CAFE", 0, "소비 유형은 썼다입니다.", 0.9));

        AnalysisResponse result = feedService.analyze(7L, 10L, media(), "SPENT", "CAFE");

        assertEquals(0, result.estimatedSavingAmount());
        verify(feedMapper, never()).findCategoryExpenseAverage(
                eq(7L), eq("CAFE"), any(), any());
    }

    @Test
    void allowsManualAmountAfterAiFailure() {
        Feed result = feedService.create(
                7L, 10L, media(), "REDUCED", "CAFE", null,
                "직접 금액을 입력했어요.", 2_500, "AI 분석에 실패했습니다.", 0.0,
                null, "MANUAL", "AI_FAILED");

        assertEquals(2_500, result.getSavingAmount());
        verify(feedMapper).insertAnalysis(
                null, "REDUCED", "CAFE", null, 2_500,
                "AI 분석에 실패했습니다.", 0.0, "MANUAL", "AI_FAILED");
    }

    @Test
    void rejectsFeedWithoutCaption() {
        assertThrows(IllegalArgumentException.class, () -> feedService.create(
                7L, 10L, media(), "REDUCED", "CAFE", null,
                "   ", 1_000, "분석 완료", 0.8));

        verify(feedMapper, never()).insertFeed(any());
    }

    private MultipartFile media() {
        return new MockMultipartFile("media", "feed.jpg", "image/jpeg", new byte[]{1});
    }

}
