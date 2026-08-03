package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.domain.Conversation;
import com.wallo.chat.dto.ConversationResponse;
import com.wallo.chat.dto.CreateConversationRequest;
import com.wallo.chat.mapper.ConversationMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ConversationServiceTest {

    @Mock
    private ConversationMapper conversationMapper;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        conversationService = new ConversationService(conversationMapper);
    }

    @Test
    void getConversationsReturnsMappedResponses() {
        Conversation conversation = conversation(1L, 1L, "저축 상담");
        when(conversationMapper.findAllByUserId(1L))
                .thenReturn(List.of(conversation));

        List<ConversationResponse> result =
                conversationService.getConversations(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getConversationId());
        assertEquals("저축 상담", result.get(0).getTitle());
        verify(conversationMapper).findAllByUserId(1L);
    }

    @Test
    void createConversationUsesDefaultTitleWhenTitleIsBlank() {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setUserId(1L);
        request.setTitle("  ");

        doAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setConversationId(10L);
            return 1;
        }).when(conversationMapper).insert(any(Conversation.class));

        ConversationResponse result =
                conversationService.createConversation(request);

        assertEquals(10L, result.getConversationId());
        assertEquals("새 채팅", result.getTitle());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void getConversationsRejectsInvalidUserId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.getConversations(0L)
        );
    }

    private Conversation conversation(Long id, Long userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setConversationId(id);
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversation;
    }
}
