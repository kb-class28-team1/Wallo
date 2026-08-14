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
import com.wallo.chat.dto.AssetAnalysisView;
import com.wallo.chat.dto.ConsumptionAnalysisView;
import com.wallo.chat.dto.ConsumptionAnalysisPeriodContext;
import com.wallo.chat.dto.SendConversationMessageRequest;
import com.wallo.chat.dto.SendConversationMessageResponse;
import com.wallo.chat.dto.SummarizeConversationRequest;
import com.wallo.chat.dto.SummarizeConversationResponse;
import com.wallo.goal.service.GoalPersistenceService;
import com.wallo.goal.dto.GoalInterviewDto;
import com.wallo.mission.service.MissionGenerationService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

class ConversationMessageServiceTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private ChatMessagePersistenceService persistenceService;

    @Mock
    private ChatService chatService;

    @Mock
    private GoalPersistenceService goalPersistenceService;

    @Mock
    private ConsumptionAnalysisResultService consumptionAnalysisResultService;

    @Mock
    private ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler;

    @Mock
    private AssetAnalysisResultService assetAnalysisResultService;

    @Mock
    private AssetAnalysisViewAssembler assetAnalysisViewAssembler;

    private ConversationMessageService conversationMessageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        conversationMessageService = new ConversationMessageService(
                conversationService,
                persistenceService,
                chatService,
                goalPersistenceService,
                consumptionAnalysisResultService,
                consumptionAnalysisViewAssembler,
                assetAnalysisResultService,
                assetAnalysisViewAssembler
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
    void firstConsumptionAnalysisUsesFixedTitleWithoutGeneratingAiTitle() {
        SendConversationMessageRequest request = request(1L, "내 소비를 분석해줘");
        when(persistenceService.hasNoMessages(1L)).thenReturn(true);
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(new ChatRequest(request.getMessage(), false), 1L))
                .thenReturn(new ChatResponse("분석 결과입니다.", "AI가 만든 제목"));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "분석 결과입니다."))
                .thenReturn(message(2L, "ASSISTANT", "분석 결과입니다."));

        conversationMessageService.sendMessage(1L, 1L, request);

        verify(chatService).chat(new ChatRequest(request.getMessage(), false), 1L);
        verify(conversationService).updateAfterUserMessage(1L, "소비 분석");
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
    void sendMessagePassesLatestConsumptionPeriodToFollowUp() {
        SendConversationMessageRequest request = request(1L, "그 기간에 소비 습관이 있어?");
        ChatMessage previousUser = message(1L, "USER", "7월 소비를 분석해줘");
        ChatMessage previousAssistant = message(2L, "ASSISTANT", "7월 소비 분석 결과");
        ConsumptionAnalysisPeriodContext period = new ConsumptionAnalysisPeriodContext(
                "MONTHLY", "지난달", "2026-07-01", "2026-07-31",
                "2026-06-01", "2026-06-30"
        );
        ChatRequest expectedRequest = new ChatRequest(
                request.getMessage(), false,
                List.of(
                        new ChatHistoryMessage("user", previousUser.getContent()),
                        new ChatHistoryMessage("assistant", previousAssistant.getContent())
                )
        ).withPreviousConsumptionPeriod(period);

        when(persistenceService.getMessages(1L))
                .thenReturn(List.of(previousUser, previousAssistant));
        when(consumptionAnalysisResultService.findLatestPeriod(List.of(2L)))
                .thenReturn(period);
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(3L, "USER", request.getMessage()));
        when(chatService.chat(expectedRequest, 1L))
                .thenReturn(new ChatResponse("분석 결과", null));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "분석 결과"))
                .thenReturn(message(4L, "ASSISTANT", "분석 결과"));

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

    @Test
    void linksConsumptionAnalysisToSavedAssistantMessage() {
        SendConversationMessageRequest request = request(7L, "이번 달 소비를 분석해줘");
        ChatMessage assistantMessage = message(2L, "ASSISTANT", "분석 결과입니다.");
        Map<String, Object> calculation = Map.of("periodType", "MONTHLY");
        ConsumptionAnalysisView analysis = emptyAnalysis();

        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(new ChatRequest(request.getMessage()), 7L))
                .thenReturn(new ChatResponse(
                        "분석 결과입니다.", null, null, calculation));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "분석 결과입니다."))
                .thenReturn(assistantMessage);
        when(consumptionAnalysisViewAssembler.assemble(calculation))
                .thenReturn(analysis);

        SendConversationMessageResponse response =
                conversationMessageService.sendMessage(1L, 7L, request);

        verify(consumptionAnalysisResultService).save(
                7L, 2L, request.getMessage(), calculation, "분석 결과입니다.");
        assertEquals(analysis, response.getConsumptionAnalysis());
        assertEquals(analysis,
                response.getAssistantMessage().getConsumptionAnalysis());
    }

    @Test
    void generatesTodayMissionsAfterUsersFirstConsumptionAnalysis() {
        MissionGenerationService missionGenerationService =
                org.mockito.Mockito.mock(MissionGenerationService.class);
        ConversationMessageService service = new ConversationMessageService(
                conversationService, persistenceService, chatService,
                goalPersistenceService, consumptionAnalysisResultService,
                consumptionAnalysisViewAssembler, missionGenerationService);
        SendConversationMessageRequest request = request(7L, "소비분석해줘");
        Map<String, Object> calculation = Map.of("periodType", "MONTHLY");
        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(new ChatRequest(request.getMessage()), 7L))
                .thenReturn(new ChatResponse("첫 분석", null, null, calculation));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "첫 분석"))
                .thenReturn(message(2L, "ASSISTANT", "첫 분석"));
        when(consumptionAnalysisViewAssembler.assemble(calculation))
                .thenReturn(emptyAnalysis());
        when(consumptionAnalysisResultService.save(
                7L, 2L, request.getMessage(), calculation, "첫 분석"))
                .thenReturn(true);

        service.sendMessage(1L, 7L, request);

        verify(missionGenerationService).generate(7L, false);
    }

    @Test
    void restoresConsumptionAnalysisWithConversationMessages() {
        ChatMessage assistantMessage = message(2L, "ASSISTANT", "분석 결과입니다.");
        ConsumptionAnalysisView analysis = emptyAnalysis();
        when(persistenceService.getMessages(1L))
                .thenReturn(List.of(assistantMessage));
        when(consumptionAnalysisResultService.findByAssistantMessageIds(
                List.of(2L))).thenReturn(Map.of(2L, analysis));

        List<com.wallo.chat.dto.ChatMessageResponse> responses =
                conversationMessageService.getMessages(1L, 7L);

        assertEquals(analysis, responses.get(0).getConsumptionAnalysis());
    }

    @Test
    void linksAssetAnalysisToSavedAssistantMessage() {
        SendConversationMessageRequest request = request(7L, "analyze assets");
        ChatMessage assistantMessage = message(2L, "ASSISTANT", "asset analysis answer");
        Map<String, Object> calculation = Map.of(
                "calculatedMetrics",
                Map.of("totalAssetsKrw", 100_000_000L)
        );
        AssetAnalysisView analysis = emptyAssetAnalysis();

        when(persistenceService.saveMessage(1L, "USER", request.getMessage()))
                .thenReturn(message(1L, "USER", request.getMessage()));
        when(chatService.chat(new ChatRequest(request.getMessage()), 7L))
                .thenReturn(new ChatResponse(
                        "asset analysis answer", null, null, null, calculation));
        when(persistenceService.saveMessage(1L, "ASSISTANT", "asset analysis answer"))
                .thenReturn(assistantMessage);
        when(assetAnalysisViewAssembler.assemble(calculation)).thenReturn(analysis);

        SendConversationMessageResponse response =
                conversationMessageService.sendMessage(1L, 7L, request);

        verify(assetAnalysisResultService).save(
                7L, 2L, request.getMessage(), calculation, "asset analysis answer");
        assertEquals(analysis, response.getAssetAnalysis());
        assertEquals(analysis, response.getAssistantMessage().getAssetAnalysis());
    }

    @Test
    void restoresAssetAnalysisWithConversationMessages() {
        ChatMessage assistantMessage = message(2L, "ASSISTANT", "asset analysis answer");
        AssetAnalysisView analysis = emptyAssetAnalysis();
        when(persistenceService.getMessages(1L)).thenReturn(List.of(assistantMessage));
        when(consumptionAnalysisResultService.findByAssistantMessageIds(List.of(2L)))
                .thenReturn(Map.of());
        when(assetAnalysisResultService.findByAssistantMessageIds(List.of(2L)))
                .thenReturn(Map.of(2L, analysis));

        List<com.wallo.chat.dto.ChatMessageResponse> responses =
                conversationMessageService.getMessages(1L, 7L);

        assertEquals(analysis, responses.get(0).getAssetAnalysis());
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

    private ConsumptionAnalysisView emptyAnalysis() {
        return new ConsumptionAnalysisView(
                "OVERVIEW",
                new ConsumptionAnalysisView.PeriodInfo(
                        "MONTHLY", "이번 달", "2026-08-01", "2026-08-11",
                        "2026-07-01", "2026-07-31"),
                true,
                null,
                new ConsumptionAnalysisView.SummaryInfo(
                        100_000L, 120_000L, -20_000L, -16.7, false),
                new ConsumptionAnalysisView.SignalSet(
                        List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), null, List.of(), List.of(), List.of(), List.of())
        );
    }
    private AssetAnalysisView emptyAssetAnalysis() {
        return new AssetAnalysisView(
                new AssetAnalysisView.SummaryInfo(
                        100_000_000L, 10_000_000L, 90_000_000L),
                new AssetAnalysisView.CashFlowInfo(
                        5_000_000L, 2_000_000L, 3_000_000L,
                        2_000_000L, 24_000_000L, 40.0),
                List.of(new AssetAnalysisView.AssetItem(
                        "Savings", "saving_cash", 80_000_000L,
                        null, null, 80.0, false)),
                List.of()
        );
    }
}
