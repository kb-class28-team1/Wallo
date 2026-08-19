package com.wallo.chat.dto;

import com.wallo.chat.domain.ChatMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ChatMessageResponse {

    private final Long messageId;
    private final String role;
    private final String content;
    private final String createdAt;
    private final ConsumptionAnalysisView consumptionAnalysis;
    private final AssetAnalysisView assetAnalysis;
    private final Map<String, Object> productRecommendation;

    private ChatMessageResponse(
            ChatMessage message,
            ConsumptionAnalysisView consumptionAnalysis,
            AssetAnalysisView assetAnalysis,
            Map<String, Object> productRecommendation
    ) {
        this.messageId = message.getMessageId();
        this.role = message.getRole();
        this.content = message.getContent();
        this.createdAt = formatDateTime(message.getCreatedAt());
        this.consumptionAnalysis = consumptionAnalysis;
        this.assetAnalysis = assetAnalysis;
        this.productRecommendation = productRecommendation;
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message, null, null, null);
    }

    public static ChatMessageResponse from(
            ChatMessage message,
            ConsumptionAnalysisView consumptionAnalysis
    ) {
        return new ChatMessageResponse(message, consumptionAnalysis, null, null);
    }

    public static ChatMessageResponse from(
            ChatMessage message,
            ConsumptionAnalysisView consumptionAnalysis,
            AssetAnalysisView assetAnalysis
    ) {
        return new ChatMessageResponse(message, consumptionAnalysis, assetAnalysis, null);
    }

    public static ChatMessageResponse from(
            ChatMessage message,
            ConsumptionAnalysisView consumptionAnalysis,
            AssetAnalysisView assetAnalysis,
            Map<String, Object> productRecommendation
    ) {
        return new ChatMessageResponse(
                message,
                consumptionAnalysis,
                assetAnalysis,
                productRecommendation
        );
    }

    public Long getMessageId() {
        return messageId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCreatedAt() {
        return createdAt;
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

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null
                ? null
                : dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
