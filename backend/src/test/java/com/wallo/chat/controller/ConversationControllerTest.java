package com.wallo.chat.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.chat.dto.SendConversationMessageRequest;
import com.wallo.chat.dto.SendConversationMessageResponse;
import com.wallo.chat.service.ConversationMessageService;
import com.wallo.chat.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ConversationControllerTest {

    @Test
    void ignoresClientUserIdAndDelegatesWithAuthenticatedUserId() {
        ConversationService conversationService = mock(ConversationService.class);
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        ConversationController controller = new ConversationController(
                conversationService,
                messageService,
                currentUserProvider
        );
        SendConversationMessageRequest request = new SendConversationMessageRequest();
        request.setUserId(999L);
        request.setMessage("목표를 만들고 싶어");
        SendConversationMessageResponse expected =
                new SendConversationMessageResponse(null, null);
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
        when(messageService.sendMessage(1L, 7L, request)).thenReturn(expected);

        ResponseEntity<SendConversationMessageResponse> response =
                controller.sendMessage(1L, request);

        assertEquals(expected, response.getBody());
        verify(messageService).sendMessage(1L, 7L, request);
    }
}
