package com.wallo.chat.service;

import com.wallo.chat.domain.ChatMessage;
import com.wallo.chat.dto.ChatMessageResponse;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.dto.SendConversationMessageRequest;
import com.wallo.chat.dto.SendConversationMessageResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ConversationMessageService {

    private static final String USER_ROLE = "USER";
    private static final String ASSISTANT_ROLE = "ASSISTANT";

    private final ConversationService conversationService;
    private final ChatMessagePersistenceService persistenceService;
    private final ChatService chatService;

    public ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService
    ) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.chatService = chatService;
    }

    public List<ChatMessageResponse> getMessages(
            Long conversationId,
            Long userId
    ) {
        conversationService.validateOwnership(conversationId, userId);
        return persistenceService.getMessages(conversationId)
                .stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());
    }

    public SendConversationMessageResponse sendMessage(
            Long conversationId,
            SendConversationMessageRequest request
    ) {
        validateRequest(request);
        conversationService.validateOwnership(
                conversationId,
                request.getUserId()
        );

        String content = request.getMessage().trim();
        ChatMessage userMessage = persistenceService.saveMessage(
                conversationId,
                USER_ROLE,
                content
        );
        conversationService.updateAfterUserMessage(conversationId, content);

        ChatResponse aiResponse = chatService.chat(new ChatRequest(content));
        ChatMessage assistantMessage = persistenceService.saveMessage(
                conversationId,
                ASSISTANT_ROLE,
                aiResponse.answer()
        );
        conversationService.touch(conversationId);

        return new SendConversationMessageResponse(
                ChatMessageResponse.from(userMessage),
                ChatMessageResponse.from(assistantMessage)
        );
    }

    private void validateRequest(SendConversationMessageRequest request) {
        if (request == null
                || request.getUserId() == null
                || request.getUserId() < 1) {
            throw new IllegalArgumentException("올바른 사용자 ID가 필요합니다.");
        }
        if (request.getMessage() == null
                || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
    }
}
