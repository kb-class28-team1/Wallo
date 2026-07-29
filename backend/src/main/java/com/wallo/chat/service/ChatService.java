package com.wallo.chat.service;

import com.wallo.chat.client.PythonAiClient;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
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
}
