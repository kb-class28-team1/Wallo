package com.wallo.chat.dto;

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
    public static class RecentResult {
        private long analysisResultId;
        private String calculatedResultJson;
        private String aiResponse;
    }
}
