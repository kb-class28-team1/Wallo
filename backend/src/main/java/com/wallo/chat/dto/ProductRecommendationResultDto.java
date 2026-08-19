package com.wallo.chat.dto;

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
}
