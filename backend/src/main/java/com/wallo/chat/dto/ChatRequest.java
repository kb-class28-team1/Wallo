package com.wallo.chat.dto;

import java.util.List;

public record ChatRequest(
        String message,
        boolean generateTitle,
        String summary,
        List<ChatHistoryMessage> history
) {

    public ChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public ChatRequest(String message) {
        this(message, false, null, List.of());
    }

    public ChatRequest(String message, boolean generateTitle) {
        this(message, generateTitle, null, List.of());
    }

    public ChatRequest(
            String message,
            boolean generateTitle,
            List<ChatHistoryMessage> history
    ) {
        this(message, generateTitle, null, history);
    }
}
