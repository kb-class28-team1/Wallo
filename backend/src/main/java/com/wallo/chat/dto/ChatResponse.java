package com.wallo.chat.dto;

import com.wallo.goal.dto.GoalInterviewDto;
import java.util.Map;

public record ChatResponse(
        String answer,
        String title,
        GoalInterviewDto.Result goalInterview,
        Map<String, Object> consumptionAnalysis
) {

    public ChatResponse(String answer) {
        this(answer, null, null, null);
    }

    public ChatResponse(String answer, String title) {
        this(answer, title, null, null);
    }

    public ChatResponse(String answer, String title, GoalInterviewDto.Result goalInterview) {
        this(answer, title, goalInterview, null);
    }
}
