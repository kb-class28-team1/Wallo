package com.wallo.chat.dto;

import com.wallo.goal.dto.GoalInterviewDto;
import java.util.Map;

public record ChatResponse(
        String answer,
        String title,
        GoalInterviewDto.Result goalInterview,
        Map<String, Object> consumptionAnalysis,
        Map<String, Object> assetAnalysis,
        boolean consumptionAnalysisReused
) {

    public ChatResponse(String answer) {
        this(answer, null, null, null, null, false);
    }

    public ChatResponse(String answer, String title) {
        this(answer, title, null, null, null, false);
    }

    public ChatResponse(String answer, String title, GoalInterviewDto.Result goalInterview) {
        this(answer, title, goalInterview, null, null, false);
    }

    public ChatResponse(
            String answer,
            String title,
            GoalInterviewDto.Result goalInterview,
            Map<String, Object> consumptionAnalysis
    ) {
        this(answer, title, goalInterview, consumptionAnalysis, null, false);
    }

    public ChatResponse(
            String answer,
            String title,
            GoalInterviewDto.Result goalInterview,
            Map<String, Object> consumptionAnalysis,
            Map<String, Object> assetAnalysis
    ) {
        this(answer, title, goalInterview, consumptionAnalysis, assetAnalysis, false);
    }

    public ChatResponse(
            String answer,
            String title,
            GoalInterviewDto.Result goalInterview,
            Map<String, Object> consumptionAnalysis,
            boolean consumptionAnalysisReused
    ) {
        this(answer, title, goalInterview, consumptionAnalysis, null, consumptionAnalysisReused);
    }
}
