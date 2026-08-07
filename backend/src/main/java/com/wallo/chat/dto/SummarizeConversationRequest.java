package com.wallo.chat.dto;

import java.util.List;

public record SummarizeConversationRequest(
        String existingSummary,
        List<ChatHistoryMessage> messages
) {
}
