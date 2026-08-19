package com.wallo.chat.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class ProductRecommendationResultDto {

    private ProductRecommendationResultDto() {
    }

    @Getter
    @AllArgsConstructor
    public static class SaveCommand {
        private long userId;
        private long assistantMessageId;
        private String requestMessage;
        private String recommendationResultJson;
        private String aiResponse;
    }

    @Getter
    @AllArgsConstructor
    public static class StoredResult {
        private long assistantMessageId;
        private String recommendationResultJson;
    }

    @Getter
    @AllArgsConstructor
    public static class LatestStoredResult {
        private long assistantMessageId;
        private String requestMessage;
        private String recommendationResultJson;
        private String aiResponse;
        private LocalDateTime generatedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class LatestResponse {
        private long assistantMessageId;
        private String requestMessage;
        private Map<String, Object> productRecommendation;
        private String aiResponse;
        private LocalDateTime generatedAt;
    }
}
