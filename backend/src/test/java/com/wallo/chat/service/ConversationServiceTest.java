package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.domain.Conversation;
import com.wallo.chat.dto.ConversationResponse;
import com.wallo.chat.dto.CreateConversationRequest;
import com.wallo.chat.dto.UpdateConversationTitleRequest;
import com.wallo.chat.mapper.ConversationMapper;
import com.wallo.goal.domain.FinancialGoal;
import com.wallo.goal.mapper.GoalMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ConversationServiceTest {

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private GoalMapper goalMapper;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        conversationService = new ConversationService(conversationMapper, goalMapper);
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

    @Test
    void updateTitleChangesOwnedActiveConversation() {
        Conversation existing = conversation(1L, 7L, "AI가 만든 제목");
        Conversation updated = conversation(1L, 7L, "직접 바꾼 제목");
        UpdateConversationTitleRequest request = new UpdateConversationTitleRequest();
        request.setUserId(7L);
        request.setTitle("  직접 바꾼 제목  ");
        when(conversationMapper.findByIdAndUserId(1L, 7L))
                .thenReturn(existing, updated);
        when(conversationMapper.updateTitle(1L, 7L, "직접 바꾼 제목"))
                .thenReturn(1);

        ConversationResponse result = conversationService.updateTitle(1L, request);

        assertEquals("직접 바꾼 제목", result.getTitle());
        verify(conversationMapper).updateTitle(1L, 7L, "직접 바꾼 제목");
    }

    @Test
    void updateTitleRejectsBlankTitle() {
        UpdateConversationTitleRequest request = new UpdateConversationTitleRequest();
        request.setUserId(7L);
        request.setTitle("  ");
        when(conversationMapper.findByIdAndUserId(1L, 7L))
                .thenReturn(conversation(1L, 7L, "기존 제목"));

        assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.updateTitle(1L, request)
        );
    }

    @Test
    void deleteConversationSoftDeletesOwnedConversation() {
        when(conversationMapper.findByIdAndUserId(1L, 7L))
                .thenReturn(conversation(1L, 7L, "삭제할 채팅"));
        when(goalMapper.findGoalByConversationId(7L, 1L)).thenReturn(null);
        when(conversationMapper.softDelete(1L, 7L)).thenReturn(1);

        conversationService.deleteConversation(1L, 7L);

        verify(conversationMapper).softDelete(1L, 7L);
    }

    @Test
    void doesNotDeleteConversationLinkedToFinancialGoal() {
        when(conversationMapper.findByIdAndUserId(1L, 7L))
                .thenReturn(conversation(1L, 7L, "목표 설정"));
        when(goalMapper.findGoalByConversationId(7L, 1L))
                .thenReturn(new FinancialGoal());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> conversationService.deleteConversation(1L, 7L)
        );

        assertEquals(
                "목표 설정이 완료된 채팅은 계좌 변경에 필요하므로 삭제할 수 없습니다.",
                exception.getMessage()
        );
        verify(conversationMapper, never()).softDelete(1L, 7L);
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
