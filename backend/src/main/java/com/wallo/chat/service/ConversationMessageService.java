package com.wallo.chat.service;

import com.wallo.chat.domain.ChatMessage;
import com.wallo.chat.domain.Conversation;
import com.wallo.chat.dto.AssetAnalysisView;
import com.wallo.chat.dto.ChatHistoryMessage;
import com.wallo.chat.dto.ChatMessageResponse;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ConsumptionAnalysisPeriodContext;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.dto.ConsumptionAnalysisView;
import com.wallo.chat.dto.SendConversationMessageRequest;
import com.wallo.chat.dto.SendConversationMessageResponse;
import com.wallo.chat.dto.SummarizeConversationRequest;
import com.wallo.chat.dto.SummarizeConversationResponse;
import com.wallo.goal.dto.GoalInterviewDto;
import com.wallo.goal.service.GoalFeasibilityCalculator;
import com.wallo.goal.service.GoalPersistenceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ConversationMessageService {

    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final String USER_ROLE = "USER";
    private static final String ASSISTANT_ROLE = "ASSISTANT";

    private final ConversationService conversationService;
    private final ChatMessagePersistenceService persistenceService;
    private final ChatService chatService;
    private final GoalPersistenceService goalPersistenceService;
    private final ConsumptionAnalysisResultService consumptionAnalysisResultService;
    private final ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler;
    private final AssetAnalysisResultService assetAnalysisResultService;
    private final AssetAnalysisViewAssembler assetAnalysisViewAssembler;

    public ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService,
            GoalPersistenceService goalPersistenceService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler,
            AssetAnalysisResultService assetAnalysisResultService,
            AssetAnalysisViewAssembler assetAnalysisViewAssembler
    ) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.chatService = chatService;
        this.goalPersistenceService = goalPersistenceService;
        this.consumptionAnalysisResultService = consumptionAnalysisResultService;
        this.consumptionAnalysisViewAssembler = consumptionAnalysisViewAssembler;
        this.assetAnalysisResultService = assetAnalysisResultService;
        this.assetAnalysisViewAssembler = assetAnalysisViewAssembler;
    }

    public List<ChatMessageResponse> getMessages(
            Long conversationId,
            Long userId
    ) {
        conversationService.validateOwnership(conversationId, userId);
        List<ChatMessage> messages = persistenceService.getMessages(conversationId);
        List<Long> assistantMessageIds = messages.stream()
                .filter(message -> ASSISTANT_ROLE.equals(message.getRole()))
                .map(ChatMessage::getMessageId)
                .collect(Collectors.toList());
        Map<Long, ConsumptionAnalysisView> analyses =
                consumptionAnalysisResultService.findByAssistantMessageIds(
                        assistantMessageIds);
        Map<Long, AssetAnalysisView> loadedAssetAnalyses =
                assetAnalysisResultService.findByAssistantMessageIds(
                        assistantMessageIds);
        Map<Long, AssetAnalysisView> assetAnalyses = loadedAssetAnalyses == null
                ? Map.of()
                : loadedAssetAnalyses;
        return messages.stream()
                .map(message -> ChatMessageResponse.from(
                        message,
                        analyses.get(message.getMessageId()),
                        assetAnalyses.get(message.getMessageId())))
                .collect(Collectors.toList());
    }

    public GoalInterviewDto.ActiveDraftResponse getActiveGoalInterview(
            Long conversationId,
            Long currentUserId
    ) {
        conversationService.validateOwnership(conversationId, currentUserId);
        if (hasExistingFinancialGoal(currentUserId, conversationId)) {
            return new GoalInterviewDto.ActiveDraftResponse(false, null, null);
        }
        GoalInterviewDto.Draft draft = goalPersistenceService.getActiveDraft(
                currentUserId,
                conversationId
        );
        return new GoalInterviewDto.ActiveDraftResponse(
                draft != null,
                draft,
                GoalFeasibilityCalculator.calculate(draft, LocalDate.now())
        );
    }

    public SendConversationMessageResponse sendMessage(
            Long conversationId,
            Long currentUserId,
            SendConversationMessageRequest request
    ) {
        validateRequest(currentUserId, request);
        conversationService.validateOwnership(
                conversationId,
                currentUserId
        );
        boolean isFirstMessage = persistenceService.hasNoMessages(conversationId);

        String content = request.getMessage().trim();
        Conversation memory = conversationService.getConversationMemory(
                conversationId, currentUserId);
        List<ChatMessage> storedMessages = persistenceService.getMessages(conversationId);
        ConsumptionAnalysisPeriodContext previousConsumptionPeriod =
                consumptionAnalysisResultService.findLatestPeriod(
                        storedMessages.stream()
                                .filter(message -> ASSISTANT_ROLE.equals(message.getRole()))
                                .map(ChatMessage::getMessageId)
                                .toList()
                );
        String summary = refreshSummary(conversationId, memory, storedMessages);
        List<ChatHistoryMessage> history = buildRecentHistory(storedMessages);
        GoalInterviewDto.Draft goalDraft = goalPersistenceService.getActiveDraft(
                currentUserId,
                conversationId
        );
        boolean goalAlreadyExists = hasExistingFinancialGoal(currentUserId, conversationId);
        if (goalAlreadyExists) {
            goalDraft = null;
        }
        ChatMessage userMessage = persistenceService.saveMessage(
                conversationId,
                USER_ROLE,
                content
        );
        ChatResponse aiResponse = chatService.chat(
                new ChatRequest(content, isFirstMessage, summary, history)
                        .withGoalDraft(goalDraft)
                        .withGoalAlreadyExists(goalAlreadyExists)
                        .withPreviousConsumptionPeriod(previousConsumptionPeriod),
                currentUserId
        );
        GoalInterviewDto.Result persistedGoalInterview = goalPersistenceService.applyResult(
                currentUserId,
                conversationId,
                aiResponse.goalInterview()
        );
        ChatMessage assistantMessage = persistenceService.saveMessage(
                conversationId,
                ASSISTANT_ROLE,
                aiResponse.answer()
        );
        ConsumptionAnalysisView consumptionAnalysis =
                consumptionAnalysisViewAssembler.assemble(
                        aiResponse.consumptionAnalysis());
        if (consumptionAnalysis != null) {
            consumptionAnalysisResultService.save(
                    currentUserId,
                    assistantMessage.getMessageId(),
                    content,
                    aiResponse.consumptionAnalysis(),
                    aiResponse.answer()
            );
        }
        AssetAnalysisView assetAnalysis =
                assetAnalysisViewAssembler.assemble(aiResponse.assetAnalysis());
        if (assetAnalysis != null) {
            assetAnalysisResultService.save(
                    currentUserId,
                    assistantMessage.getMessageId(),
                    content,
                    aiResponse.assetAnalysis(),
                    aiResponse.answer()
            );
        }
        if (isFirstMessage) {
            String title = aiResponse.title() == null
                    || aiResponse.title().isBlank()
                    ? content
                    : aiResponse.title();
            conversationService.updateAfterUserMessage(conversationId, title);
        } else {
            conversationService.touch(conversationId);
        }

        return new SendConversationMessageResponse(
                ChatMessageResponse.from(userMessage),
                ChatMessageResponse.from(assistantMessage, consumptionAnalysis),
                persistedGoalInterview == null
                        ? aiResponse.goalInterview()
                        : persistedGoalInterview,
                consumptionAnalysis,
                assetAnalysis
        );
    }

    private String refreshSummary(
            Long conversationId,
            Conversation memory,
            List<ChatMessage> messages
    ) {
        String existingSummary = memory == null ? null : memory.getSummary();
        Long summarizedMessageId = memory == null
                ? null : memory.getSummarizedMessageId();
        int overflowCount = Math.max(0, messages.size() - MAX_CONTEXT_MESSAGES);
        if (overflowCount == 0) {
            return existingSummary;
        }

        List<ChatMessage> unsummarizedMessages = messages.subList(0, overflowCount)
                .stream()
                .filter(message -> summarizedMessageId == null
                        || message.getMessageId() > summarizedMessageId)
                .collect(Collectors.toList());
        if (unsummarizedMessages.isEmpty()) {
            return existingSummary;
        }

        List<ChatHistoryMessage> summaryTargets = unsummarizedMessages.stream()
                .map(this::toHistoryMessage)
                .collect(Collectors.toList());
        SummarizeConversationResponse response = chatService.summarize(
                new SummarizeConversationRequest(existingSummary, summaryTargets)
        );
        Long lastSummarizedMessageId = unsummarizedMessages
                .get(unsummarizedMessages.size() - 1)
                .getMessageId();
        conversationService.updateSummary(
                conversationId, response.summary(), lastSummarizedMessageId);
        return response.summary();
    }

    private List<ChatHistoryMessage> buildRecentHistory(List<ChatMessage> messages) {
        int fromIndex = Math.max(0, messages.size() - MAX_CONTEXT_MESSAGES);
        return messages.subList(fromIndex, messages.size())
                .stream()
                .map(this::toHistoryMessage)
                .collect(Collectors.toList());
    }

    private ChatHistoryMessage toHistoryMessage(ChatMessage message) {
        return new ChatHistoryMessage(
                message.getRole().toLowerCase(),
                message.getContent()
        );
    }

    private boolean hasExistingFinancialGoal(Long userId, Long conversationId) {
        return goalPersistenceService.hasFinancialGoalForUser(userId)
                || goalPersistenceService.hasFinancialGoal(userId, conversationId);
    }

    private void validateRequest(
            Long currentUserId,
            SendConversationMessageRequest request
    ) {
        if (currentUserId == null || currentUserId < 1) {
            throw new IllegalArgumentException("올바른 사용자 ID가 필요합니다.");
        }
        if (request == null
                || request.getMessage() == null
                || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
    }
}
