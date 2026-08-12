package com.wallo.feed.analysis;

import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.SavingAmountFeedbackSummary;
import org.springframework.web.multipart.MultipartFile;

/** 사용자별 금액 검증 요약을 분석 프롬프트에 반영할 수 있는 클라이언트. */
public interface SavingFeedbackAwareFeedAnalysisClient {
    AnalysisResponse analyze(
            MultipartFile media,
            String spendingType,
            String category,
            SavingAmountFeedbackSummary feedbackSummary);
}
