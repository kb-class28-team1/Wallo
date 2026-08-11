package com.wallo.chat.dto;

import com.wallo.goal.dto.GoalInterviewDto;

public class SendConversationMessageResponse {

    private final ChatMessageResponse userMessage;
    private final ChatMessageResponse assistantMessage;
    private final GoalInterviewDto.Result goalInterview;

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage
    ) {
        this(userMessage, assistantMessage, null);
    }

    public SendConversationMessageResponse(
            ChatMessageResponse userMessage,
            ChatMessageResponse assistantMessage,
            GoalInterviewDto.Result goalInterview
    ) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
        this.goalInterview = goalInterview;
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
}
