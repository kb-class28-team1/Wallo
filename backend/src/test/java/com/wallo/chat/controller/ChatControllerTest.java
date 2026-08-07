package com.wallo.chat.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ChatControllerTest {

    @Test
    void delegatesChatUsingAuthenticatedUserId() {
        ChatService chatService = mock(ChatService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        ChatController controller = new ChatController(chatService, currentUserProvider);
        ChatRequest request = new ChatRequest("목표를 세우고 싶어");
        ChatResponse expected = new ChatResponse("목표를 알려주세요.", null);
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
        when(chatService.chat(request, 7L)).thenReturn(expected);

        ResponseEntity<ChatResponse> response = controller.chat(request);

        assertEquals(expected, response.getBody());
        verify(chatService).chat(request, 7L);
    }
}
