package com.wallo.chat.dto;

import com.wallo.chat.domain.ChatMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatMessageResponse {

    private final Long messageId;
    private final String role;
    private final String content;
    private final String createdAt;
    private final ConsumptionAnalysisView consumptionAnalysis;
    private final AssetAnalysisView assetAnalysis;

    private ChatMessageResponse(
            ChatMessage message,
            ConsumptionAnalysisView consumptionAnalysis,
            AssetAnalysisView assetAnalysis
    ) {
        this.messageId = message.getMessageId();
        this.role = message.getRole();
        this.content = message.getContent();
        this.createdAt = formatDateTime(message.getCreatedAt());
        this.consumptionAnalysis = consumptionAnalysis;
        this.assetAnalysis = assetAnalysis;
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message, null, null);
    }

    public static ChatMessageResponse from(
            ChatMessage message,
            ConsumptionAnalysisView consumptionAnalysis
    ) {
        return new ChatMessageResponse(message, consumptionAnalysis, null);
    }

    public static ChatMessageResponse from(
            ChatMessage message,
            ConsumptionAnalysisView consumptionAnalysis,
            AssetAnalysisView assetAnalysis
    ) {
        return new ChatMessageResponse(message, consumptionAnalysis, assetAnalysis);
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

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null
                ? null
                : dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
