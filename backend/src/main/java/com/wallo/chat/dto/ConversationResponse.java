package com.wallo.chat.dto;

import com.wallo.chat.domain.Conversation;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConversationResponse {

    private final Long conversationId;
    private final String title;
    private final String status;
    private final String createdAt;
    private final String updatedAt;

    private ConversationResponse(Conversation conversation) {
        this.conversationId = conversation.getConversationId();
        this.title = conversation.getTitle();
        this.status = conversation.getStatus();
        this.createdAt = formatDateTime(conversation.getCreatedAt());
        this.updatedAt = formatDateTime(conversation.getUpdatedAt());
    }

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(conversation);
    }

    public Long getConversationId() {
        return conversationId;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null
                ? null
                : dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
