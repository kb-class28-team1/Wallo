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
        private String requestMessage;
        private String calculatedResultJson;
        private String aiResponse;
    }
}
