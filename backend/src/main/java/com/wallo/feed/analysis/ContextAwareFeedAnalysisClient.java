package com.wallo.feed.analysis;

import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.AnalysisFeedbackSummary;
import org.springframework.web.multipart.MultipartFile;

/** 사용자별 과거 정확도 요약을 프롬프트에 반영할 수 있는 분석 클라이언트. */
public interface ContextAwareFeedAnalysisClient {
    AnalysisResponse analyze(
            MultipartFile media, String spendingType, String category,
            AnalysisFeedbackSummary feedbackSummary);
}
