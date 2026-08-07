package com.wallo.chat.dto;

import com.wallo.goal.dto.GoalInterviewDto;

public record ChatResponse(
        String answer,
        String title,
        GoalInterviewDto.Result goalInterview
) {

    public ChatResponse(String answer) {
        this(answer, null, null);
    }

    public ChatResponse(String answer, String title) {
        this(answer, title, null);
    }
}
