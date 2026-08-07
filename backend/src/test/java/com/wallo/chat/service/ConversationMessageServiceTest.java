package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.chat.client.AiServerException;
import com.wallo.chat.domain.ChatMessage;
import com.wallo.chat.domain.Conversation;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatHistoryMessage;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.dto.SendConversationMessageRequest;
import com.wallo.chat.dto.SendConversationMessageResponse;
import com.wallo.chat.dto.SummarizeConversationRequest;
import com.wallo.chat.dto.SummarizeConversationResponse;
import com.wallo.goal.service.GoalPersistenceService;
import com.wallo.goal.dto.GoalInterviewDto;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;
import java.util.ArrayList;

class ConversationMessageServiceTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private ChatMessagePersistenceService persistenceService;

    @Mock
    private ChatService chatService;

    @Mock
    private GoalPersistenceService goalPersistenceService;

    private ConversationMessageService conversationMessageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        conversationMessageService = new ConversationMessageService(
                conversationService,
                persistenceService,
                chatService,
                goalPersistenceService
        );
    }

    @Test
    void sendMessageStoresUserMessageBeforeAssistantMessage() {
        SendConversationMessageRequest request = request(1L, "저축 계획을 알려줘");
        ChatMessage userMessage = message(1L, "USER", request.getMessage());
        ChatMessage assistantMessage = message(2L, "ASSISTANT", "답변");

        when(persistenceService.hasNoMessages(1L)).thenReturn(true);
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(userMessage);
        when(chatService.chat(new ChatRequest(request.getMessage(), true), 1L))
                .thenReturn(new ChatResponse("답변", "맞춤 저축 계획"));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "답변"))
                .thenReturn(assistantMessage);

        SendConversationMessageResponse response =
                conversationMessageService.sendMessage(1L, 1L, request);

        assertEquals(1L, response.getUserMessage().getMessageId());
        assertEquals(2L, response.getAssistantMessage().getMessageId());

        InOrder order = inOrder(
                conversationService,
                persistenceService,
                chatService
        );
        order.verify(conversationService).validateOwnership(1L, 1L);
        order.verify(persistenceService).hasNoMessages(1L);
        order.verify(persistenceService)
                .saveMessage(1L, "USER", request.getMessage());
        order.verify(chatService)
                .chat(new ChatRequest(request.getMessage(), true), 1L);
        order.verify(persistenceService)
                .saveMessage(1L, "ASSISTANT", "답변");
        order.verify(conversationService)
                .updateAfterUserMessage(1L, "맞춤 저축 계획");
    }

    @Test
    void aiFailureDoesNotTryToStoreAssistantMessage() {
        SendConversationMessageRequest request = request(1L, "질문");
        when(persistenceService.saveMessage(1L, "USER", "질문"))
                .thenReturn(message(1L, "USER", "질문"));
        when(chatService.chat(new ChatRequest("질문"), 1L))
                .thenThrow(new AiServerException("AI 호출 실패"));

        assertThrows(
                AiServerException.class,
                () -> conversationMessageService.sendMessage(1L, 1L, request)
        );

        verify(persistenceService)
                .saveMessage(1L, "USER", "질문");
    }

    @Test
    void sendMessageIncludesStoredConversationHistory() {
        SendConversationMessageRequest request = request(1L, "그중 두 번째는?");
        ChatMessage previousUser = message(1L, "USER", "방법 세 가지를 알려줘");
        ChatMessage previousAssistant = message(2L, "ASSISTANT", "예산, 자동이체, 소비 점검입니다.");
        ChatRequest expectedRequest = new ChatRequest(
                request.getMessage(),
                false,
                List.of(
                        new ChatHistoryMessage("user", previousUser.getContent()),
                        new ChatHistoryMessage("assistant", previousAssistant.getContent())
                )
        );

        when(persistenceService.getMessages(1L))
                .thenReturn(List.of(previousUser, previousAssistant));
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(3L, "USER", request.getMessage()));
        when(chatService.chat(expectedRequest, 1L))
                .thenReturn(new ChatResponse("자동이체를 설명할게요.", null));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "자동이체를 설명할게요."))
                .thenReturn(message(4L, "ASSISTANT", "자동이체를 설명할게요."));

        conversationMessageService.sendMessage(1L, 1L, request);

        verify(chatService).chat(expectedRequest, 1L);
    }

    @Test
    void sendMessageSummarizesMessagesOlderThanRecentTwenty() {
        SendConversationMessageRequest request = request(1L, "이어서 알려줘");
        Conversation memory = new Conversation();
        memory.setSummary("기존 장기 기억");
        memory.setSummarizedMessageId(null);
        List<ChatMessage> storedMessages = new ArrayList<>();
        for (long id = 1; id <= 22; id++) {
            storedMessages.add(message(
                    id,
                    id % 2 == 1 ? "USER" : "ASSISTANT",
                    "메시지 " + id
            ));
        }
        SummarizeConversationRequest summaryRequest = new SummarizeConversationRequest(
                "기존 장기 기억",
                List.of(
                        new ChatHistoryMessage("user", "메시지 1"),
                        new ChatHistoryMessage("assistant", "메시지 2")
                )
        );

        when(conversationService.getConversationMemory(1L, 1L)).thenReturn(memory);
        when(persistenceService.getMessages(1L)).thenReturn(storedMessages);
        when(chatService.summarize(summaryRequest))
                .thenReturn(new SummarizeConversationResponse("갱신된 장기 기억"));
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(23L, "USER", request.getMessage()));
        when(chatService.chat(
                org.mockito.ArgumentMatchers.any(ChatRequest.class),
                org.mockito.ArgumentMatchers.eq(1L)
        ))
                .thenReturn(new ChatResponse("답변", null));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "답변"))
                .thenReturn(message(24L, "ASSISTANT", "답변"));

        conversationMessageService.sendMessage(1L, 1L, request);

        verify(chatService).summarize(summaryRequest);
        verify(conversationService).updateSummary(1L, "갱신된 장기 기억", 2L);
        verify(chatService).chat(
                org.mockito.ArgumentMatchers.argThat(chatRequest ->
                        "갱신된 장기 기억".equals(chatRequest.summary())
                                && chatRequest.history().size() == 20
                                && "메시지 3".equals(chatRequest.history().get(0).content())
                ),
                org.mockito.ArgumentMatchers.eq(1L)
        );
    }

    @Test
    void usesAuthenticatedUserInsteadOfClientProvidedUserId() {
        SendConversationMessageRequest request = request(999L, "목표를 만들고 싶어");
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(new ChatRequest(request.getMessage()), 7L))
                .thenReturn(new ChatResponse("목표를 알려주세요.", null));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "목표를 알려주세요."))
                .thenReturn(message(2L, "ASSISTANT", "목표를 알려주세요."));

        conversationMessageService.sendMessage(1L, 7L, request);

        verify(conversationService).validateOwnership(1L, 7L);
        verify(chatService).chat(new ChatRequest(request.getMessage()), 7L);
    }

    @Test
    void tellsAiWhenTheConversationAlreadyHasAFinancialGoal() {
        SendConversationMessageRequest request = request(7L, "새로운 여행 목표를 만들고 싶어");
        ChatRequest expectedRequest = new ChatRequest(request.getMessage())
                .withGoalAlreadyExists(true);

        when(goalPersistenceService.hasFinancialGoal(7L, 1L)).thenReturn(true);
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(expectedRequest, 7L))
                .thenReturn(new ChatResponse(
                        "이 채팅방에는 이미 금융 목표가 설정되어 있습니다.",
                        null
                ));
        when(persistenceService.saveMessage(
                1L,
                "ASSISTANT",
                "이 채팅방에는 이미 금융 목표가 설정되어 있습니다."
        )).thenReturn(message(
                2L,
                "ASSISTANT",
                "이 채팅방에는 이미 금융 목표가 설정되어 있습니다."
        ));

        conversationMessageService.sendMessage(1L, 7L, request);

        verify(chatService).chat(expectedRequest, 7L);
    }

    @Test
    void tellsAiWhenAnotherConversationAlreadyHasTheUsersFinancialGoal() {
        SendConversationMessageRequest request = request(7L, "새로운 여행 목표를 만들고 싶어");
        ChatRequest expectedRequest = new ChatRequest(request.getMessage())
                .withGoalAlreadyExists(true);

        when(goalPersistenceService.hasFinancialGoalForUser(7L)).thenReturn(true);
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(expectedRequest, 7L))
                .thenReturn(new ChatResponse(
                        "이미 금융 목표가 설정되어 있습니다.",
                        null
                ));
        when(persistenceService.saveMessage(
                1L,
                "ASSISTANT",
                "이미 금융 목표가 설정되어 있습니다."
        )).thenReturn(message(
                2L,
                "ASSISTANT",
                "이미 금융 목표가 설정되어 있습니다."
        ));

        conversationMessageService.sendMessage(1L, 7L, request);

        verify(chatService).chat(expectedRequest, 7L);
    }

    @Test
    void doesNotRestoreAnInterviewWhenTheConversationAlreadyHasAFinancialGoal() {
        when(goalPersistenceService.hasFinancialGoal(7L, 1L)).thenReturn(true);

        GoalInterviewDto.ActiveDraftResponse response =
                conversationMessageService.getActiveGoalInterview(1L, 7L);

        assertFalse(response.isActive());
        assertNull(response.getDraft());
        assertNull(response.getFeasibility());
        verify(goalPersistenceService, org.mockito.Mockito.never()).getActiveDraft(7L, 1L);
    }

    @Test
    void continuesStoredGoalInterviewAndPersistsTheAiResult() {
        SendConversationMessageRequest request = request(7L, "천만 원이 필요해");
        GoalInterviewDto.Draft storedDraft = goalDraft("COLLECTING", false);
        GoalInterviewDto.Draft updatedDraft = goalDraft("COLLECTING", false);
        updatedDraft.setMissingFields(List.of("targetDate"));
        GoalInterviewDto.Result goalResult = new GoalInterviewDto.Result(
                GoalInterviewDto.Action.CONTINUE,
                true,
                updatedDraft,
                null
        );
        ChatRequest expectedRequest = new ChatRequest(request.getMessage())
                .withGoalDraft(storedDraft);

        when(goalPersistenceService.getActiveDraft(7L, 1L)).thenReturn(storedDraft);
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(expectedRequest, 7L))
                .thenReturn(new ChatResponse("목표 시점은 언제인가요?", null, goalResult));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "목표 시점은 언제인가요?"))
                .thenReturn(message(2L, "ASSISTANT", "목표 시점은 언제인가요?"));

        conversationMessageService.sendMessage(1L, 7L, request);

        verify(chatService).chat(expectedRequest, 7L);
        verify(goalPersistenceService).applyResult(7L, 1L, goalResult);
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

    private GoalInterviewDto.Draft goalDraft(String state, boolean confirmed) {
        return new GoalInterviewDto.Draft(
                state,
                "유럽 여행 자금",
                "TRAVEL",
                10_000_000L,
                LocalDate.of(2027, 8, 1),
                "가족과 여행",
                "MEDIUM",
                2_000_000L,
                List.of(),
                List.of(),
                confirmed
        );
    }
}
