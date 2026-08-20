package com.wallo.chat.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class ConsumptionAnalysisResultDto {

    private ConsumptionAnalysisResultDto() {
    }

    @Getter
    @AllArgsConstructor
    public static class SaveCommand {
        private long userId;
        private long assistantMessageId;
        private String requestMessage;
        private String calculatedResultJson;
        private String aiResponse;
    }

    @Getter
    @AllArgsConstructor
    public static class StoredResult {
        private long assistantMessageId;
        private String calculatedResultJson;
    }

    @Getter
    @AllArgsConstructor
    public static class LatestStoredResult {
        private long assistantMessageId;
        private String requestMessage;
        private String calculatedResultJson;
        private String aiResponse;
        private LocalDateTime generatedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class LatestResponse {
        private long assistantMessageId;
        private String requestMessage;
        private ConsumptionAnalysisView consumptionAnalysis;
        private String aiResponse;
        private LocalDateTime generatedAt;
    }
}
