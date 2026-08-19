package com.wallo.chat.dto;

import com.wallo.goal.dto.GoalInterviewDto;
import java.util.Map;

public class SendConversationMessageResponse {

    private final ChatMessageResponse userMessage;
    private final ChatMessageResponse assistantMessage;
    private final GoalInterviewDto.Result goalInterview;
    private final ConsumptionAnalysisView consumptionAnalysis;
    private final AssetAnalysisView assetAnalysis;
    private final Map<String, Object> productRecommendation;

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage
    ) {
        this(userMessage, assistantMessage, null, null, null, null);
    }

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage,
            GoalInterviewDto.Result goalInterview
    ) {
        this(userMessage, assistantMessage, goalInterview, null, null, null);
    }

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage,
            GoalInterviewDto.Result goalInterview,
            ConsumptionAnalysisView consumptionAnalysis
    ) {
        this(userMessage, assistantMessage, goalInterview, consumptionAnalysis, null, null);
    }

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage,
            GoalInterviewDto.Result goalInterview,
            ConsumptionAnalysisView consumptionAnalysis,
            AssetAnalysisView assetAnalysis
    ) {
        this(
                userMessage,
                assistantMessage,
                goalInterview,
                consumptionAnalysis,
                assetAnalysis,
                null
        );
    }

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage,
            GoalInterviewDto.Result goalInterview,
            ConsumptionAnalysisView consumptionAnalysis,
            AssetAnalysisView assetAnalysis,
            Map<String, Object> productRecommendation
    ) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
        this.goalInterview = goalInterview;
        this.consumptionAnalysis = consumptionAnalysis;
        this.assetAnalysis = assetAnalysis;
        this.productRecommendation = productRecommendation;
    }

    public ChatMessageResponse getUserMessage() {
        return userMessage;
    }

    public ChatMessageResponse getAssistantMessage() {
        return assistantMessage;
    }

    public GoalInterviewDto.Result getGoalInterview() {
        return goalInterview;
    }

    public ConsumptionAnalysisView getConsumptionAnalysis() {
        return consumptionAnalysis;
    }

    public AssetAnalysisView getAssetAnalysis() {
        return assetAnalysis;
    }

    public Map<String, Object> getProductRecommendation() {
        return productRecommendation;
    }
}
