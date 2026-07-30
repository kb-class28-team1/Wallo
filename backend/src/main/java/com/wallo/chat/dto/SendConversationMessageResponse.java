package com.wallo.chat.dto;

public class SendConversationMessageResponse {

    private final ChatMessageResponse userMessage;
    private final ChatMessageResponse assistantMessage;

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage
    ) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
    }

    public ChatMessageResponse getUserMessage() {
        return userMessage;
    }

    public ChatMessageResponse getAssistantMessage() {
        return assistantMessage;
    }
}
