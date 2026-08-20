package com.wallo.chat.dto;

import com.wallo.asset.dto.AssetAnalysisContextDto;
import com.wallo.asset.dto.ConsumptionAnalysisContextDto;
import com.wallo.asset.dto.GoalAssetContextDto;
import com.wallo.goal.dto.GoalInterviewDto;
import java.util.List;

public record ChatRequest(
        String message,
        boolean generateTitle,
        String summary,
        List<ChatHistoryMessage> history,
        GoalAssetContextDto.Response financialContext,
        AssetAnalysisContextDto assetAnalysisContext,
        GoalInterviewDto.Draft goalDraft,
        boolean goalAlreadyExists,
        ConsumptionAnalysisContextDto consumptionContext,
        ConsumptionAnalysisPeriodContext previousConsumptionPeriod,
        String chatMode
) {

    public ChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public ChatRequest(String message) {
        this(message, false, null, List.of(), null, null, null, false, null, null, null);
    }

    public ChatRequest(String message, boolean generateTitle) {
        this(message, generateTitle, null, List.of(), null, null, null, false, null, null, null);
    }

    public ChatRequest(
            String message,
            boolean generateTitle,
            List<ChatHistoryMessage> history
    ) {
        this(message, generateTitle, null, history, null, null, null, false, null, null, null);
    }

    public ChatRequest(
            String message,
            boolean generateTitle,
            String summary,
            List<ChatHistoryMessage> history
    ) {
        this(message, generateTitle, summary, history, null, null, null, false, null, null, null);
    }

    public ChatRequest withFinancialContext(GoalAssetContextDto.Response context) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                context,
                assetAnalysisContext,
                goalDraft,
                goalAlreadyExists,
                consumptionContext,
                previousConsumptionPeriod,
                chatMode
        );
    }

    public ChatRequest withAssetAnalysisContext(AssetAnalysisContextDto context) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                financialContext,
                context,
                goalDraft,
                goalAlreadyExists,
                consumptionContext,
                previousConsumptionPeriod,
                chatMode
        );
    }

    public ChatRequest withGoalDraft(GoalInterviewDto.Draft draft) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                financialContext,
                assetAnalysisContext,
                draft,
                goalAlreadyExists,
                consumptionContext,
                previousConsumptionPeriod,
                chatMode
        );
    }

    public ChatRequest withGoalAlreadyExists(boolean exists) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                financialContext,
                assetAnalysisContext,
                goalDraft,
                exists,
                consumptionContext,
                previousConsumptionPeriod,
                chatMode
        );
    }

    public ChatRequest withConsumptionContext(ConsumptionAnalysisContextDto context) {
        return new ChatRequest(message, generateTitle, summary, history, financialContext,
                assetAnalysisContext, goalDraft, goalAlreadyExists, context,
                previousConsumptionPeriod, chatMode);
    }

    public ChatRequest withPreviousConsumptionPeriod(
            ConsumptionAnalysisPeriodContext period
    ) {
        return new ChatRequest(message, generateTitle, summary, history, financialContext,
                assetAnalysisContext, goalDraft, goalAlreadyExists, consumptionContext,
                period, chatMode);
    }

    public ChatRequest withChatMode(String mode) {
        return new ChatRequest(
                message,
                generateTitle,
                summary,
                history,
                financialContext,
                assetAnalysisContext,
                goalDraft,
                goalAlreadyExists,
                consumptionContext,
                previousConsumptionPeriod,
                mode
        );
    }
}
