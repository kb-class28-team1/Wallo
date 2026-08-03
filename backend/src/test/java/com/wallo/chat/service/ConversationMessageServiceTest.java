package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.client.AiServerException;
import com.wallo.chat.domain.ChatMessage;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.dto.SendConversationMessageRequest;
import com.wallo.chat.dto.SendConversationMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ConversationMessageServiceTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private ChatMessagePersistenceService persistenceService;

    @Mock
    private ChatService chatService;

    private ConversationMessageService conversationMessageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        conversationMessageService = new ConversationMessageService(
                conversationService,
                persistenceService,
                chatService
        );
    }

    @Test
    void sendMessageStoresUserMessageBeforeAssistantMessage() {
        SendConversationMessageRequest request = request(1L, "저축 계획을 알려줘");
        ChatMessage userMessage = message(1L, "USER", request.getMessage());
        ChatMessage assistantMessage = message(2L, "ASSISTANT", "답변");

        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(userMessage);
        when(chatService.chat(new ChatRequest(request.getMessage())))
                .thenReturn(new ChatResponse("답변"));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "답변"))
                .thenReturn(assistantMessage);

        SendConversationMessageResponse response =
                conversationMessageService.sendMessage(1L, request);

        assertEquals(1L, response.getUserMessage().getMessageId());
        assertEquals(2L, response.getAssistantMessage().getMessageId());

        InOrder order = inOrder(
                conversationService,
                persistenceService,
                chatService
        );
        order.verify(conversationService).validateOwnership(1L, 1L);
        order.verify(persistenceService)
                .saveMessage(1L, "USER", request.getMessage());
        order.verify(conversationService)
                .updateAfterUserMessage(1L, request.getMessage());
        order.verify(chatService).chat(new ChatRequest(request.getMessage()));
        order.verify(persistenceService)
                .saveMessage(1L, "ASSISTANT", "답변");
        order.verify(conversationService).touch(1L);
    }

    @Test
    void aiFailureDoesNotTryToStoreAssistantMessage() {
        SendConversationMessageRequest request = request(1L, "질문");
        when(persistenceService.saveMessage(1L, "USER", "질문"))
                .thenReturn(message(1L, "USER", "질문"));
        when(chatService.chat(new ChatRequest("질문")))
                .thenThrow(new AiServerException("AI 호출 실패"));

        assertThrows(
                AiServerException.class,
                () -> conversationMessageService.sendMessage(1L, request)
        );

        verify(persistenceService)
                .saveMessage(1L, "USER", "질문");
        verify(conversationService)
                .updateAfterUserMessage(1L, "질문");
    }

    private SendConversationMessageRequest request(Long userId, String content) {
        SendConversationMessageRequest request =
                new SendConversationMessageRequest();
        request.setUserId(userId);
        request.setMessage(content);
        return request;
    }

    private ChatMessage message(Long id, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(id);
        message.setConversationId(1L);
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
