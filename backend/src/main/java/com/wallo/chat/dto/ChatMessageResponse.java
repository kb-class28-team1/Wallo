package com.wallo.chat.dto;

import com.wallo.chat.domain.ChatMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatMessageResponse {

    private final Long messageId;
    private final String role;
    private final String content;
    private final String createdAt;

    private ChatMessageResponse(ChatMessage message) {
        this.messageId = message.getMessageId();
        this.role = message.getRole();
        this.content = message.getContent();
        this.createdAt = formatDateTime(message.getCreatedAt());
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message);
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

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null
                ? null
                : dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
