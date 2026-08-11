package com.wallo.chat.dto;

import com.wallo.asset.dto.GoalAssetContextDto;
import com.wallo.asset.dto.ConsumptionAnalysisContextDto;
import com.wallo.goal.dto.GoalInterviewDto;
import java.util.List;

public record ChatRequest(
        String message,
        boolean generateTitle,
        String summary,
        List<ChatHistoryMessage> history,
        GoalAssetContextDto.Response financialContext,
        GoalInterviewDto.Draft goalDraft,
        boolean goalAlreadyExists,
        ConsumptionAnalysisContextDto consumptionContext
) {

    public ChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public ChatRequest(String message) {
        this(message, false, null, List.of(), null, null, false, null);
    }

    public ChatRequest(String message, boolean generateTitle) {
        this(message, generateTitle, null, List.of(), null, null, false, null);
    }

    public ChatRequest(
            String message,
            boolean generateTitle,
            List<ChatHistoryMessage> history
    ) {
        this(message, generateTitle, null, history, null, null, false, null);
    }

    public ChatRequest(
            String message,
            boolean generateTitle,
            String summary,
            List<ChatHistoryMessage> history
    ) {
        this(message, generateTitle, summary, history, null, null, false, null);
    }

    public ChatRequest withFinancialContext(GoalAssetContextDto.Response context) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                context,
                goalDraft,
                goalAlreadyExists,
                consumptionContext
        );
    }

    public ChatRequest withGoalDraft(GoalInterviewDto.Draft draft) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                financialContext,
                draft,
                goalAlreadyExists,
                consumptionContext
        );
    }

    public ChatRequest withGoalAlreadyExists(boolean exists) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                financialContext,
                goalDraft,
                exists,
                consumptionContext
        );
    }

    public ChatRequest withConsumptionContext(ConsumptionAnalysisContextDto context) {
        return new ChatRequest(message, generateTitle, summary, history, financialContext,
                goalDraft, goalAlreadyExists, context);
    }
}
