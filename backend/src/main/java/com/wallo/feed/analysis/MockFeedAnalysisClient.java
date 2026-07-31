package com.wallo.feed.analysis;

import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MockFeedAnalysisClient implements FeedAnalysisClient {
    private static final Map<String, Integer> CATEGORY_AMOUNTS = Map.of(
            "COFFEE", 4500, "DELIVERY", 12000, "TRANSPORT", 1500,
            "GROCERY", 8000, "DINING", 10000, "CUSTOM", 5000);

    @Override
    public AnalysisResponse analyze(
            MultipartFile media, String spendingType, String category) {
        int base = CATEGORY_AMOUNTS.getOrDefault(category, 5000);
        int amount = "SPENT".equals(spendingType) ? 0
                : ("SAVED".equals(spendingType) ? base : Math.max(1000, base / 2));
        return new AnalysisResponse(
                spendingType, category, amount,
                "선택한 소비 종류와 카테고리를 기준으로 분석한 예상 절약 금액입니다.",
                0.82);
    }
}
