package com.wallo.chat.service;

import com.wallo.chat.client.PythonAiClient;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.dto.SummarizeConversationRequest;
import com.wallo.chat.dto.SummarizeConversationResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final PythonAiClient pythonAiClient;

    public ChatService(PythonAiClient pythonAiClient) {
        this.pythonAiClient = pythonAiClient;
    }

    public ChatResponse chat(ChatRequest request) {
        if (request == null
                || request.message() == null
                || request.message().isBlank()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }

        return pythonAiClient.chat(request);
    }

    public SummarizeConversationResponse summarize(SummarizeConversationRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("요약할 대화가 필요합니다.");
        }
        return pythonAiClient.summarize(request);
    }
}
