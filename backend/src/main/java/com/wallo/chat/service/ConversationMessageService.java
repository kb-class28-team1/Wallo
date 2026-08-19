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
import com.wallo.mission.service.MissionGenerationService;
import com.wallo.mission.service.MissionGenerationWorker;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.logging.Logger;

@Service
public class ConversationMessageService {
    private static final Logger LOGGER = Logger.getLogger(ConversationMessageService.class.getName());
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final String USER_ROLE = "USER";
    private static final String ASSISTANT_ROLE = "ASSISTANT";
    private static final String CONSUMPTION_ANALYSIS_TITLE = "소비 분석";
    private static final String GOAL_SETTING_MODE = "GOAL_SETTING";
    private static final String GOAL_SETTING_TITLE = "목표 설정";

    private final ConversationService conversationService;
    private final ChatMessagePersistenceService persistenceService;
    private final ChatService chatService;
    private final GoalPersistenceService goalPersistenceService;
    private final ConsumptionAnalysisResultService consumptionAnalysisResultService;
    private final ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler;
    private final AssetAnalysisResultService assetAnalysisResultService;
    private final AssetAnalysisViewAssembler assetAnalysisViewAssembler;
    private final ProductRecommendationResultService productRecommendationResultService;
    private final MissionGenerationWorker missionGenerationWorker;

    @Autowired
    public ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService,
            GoalPersistenceService goalPersistenceService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler,
            AssetAnalysisResultService assetAnalysisResultService,
            AssetAnalysisViewAssembler assetAnalysisViewAssembler,
            ProductRecommendationResultService productRecommendationResultService,
            MissionGenerationWorker missionGenerationWorker
    ) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.chatService = chatService;
        this.goalPersistenceService = goalPersistenceService;
        this.consumptionAnalysisResultService = consumptionAnalysisResultService;
        this.consumptionAnalysisViewAssembler = consumptionAnalysisViewAssembler;
        this.assetAnalysisResultService = assetAnalysisResultService;
        this.assetAnalysisViewAssembler = assetAnalysisViewAssembler;
        this.productRecommendationResultService = productRecommendationResultService;
        this.missionGenerationWorker = missionGenerationWorker;
    }

    public ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService,
            GoalPersistenceService goalPersistenceService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler,
            AssetAnalysisResultService assetAnalysisResultService,
            AssetAnalysisViewAssembler assetAnalysisViewAssembler,
            MissionGenerationWorker missionGenerationWorker
    ) {
        this(
                conversationService,
                persistenceService,
                chatService,
                goalPersistenceService,
                consumptionAnalysisResultService,
                consumptionAnalysisViewAssembler,
                assetAnalysisResultService,
                assetAnalysisViewAssembler,
                null,
                missionGenerationWorker
        );
    }

    ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService,
            GoalPersistenceService goalPersistenceService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler,
            AssetAnalysisResultService assetAnalysisResultService,
            AssetAnalysisViewAssembler assetAnalysisViewAssembler
    ) {
        this(conversationService, persistenceService, chatService, goalPersistenceService,
                consumptionAnalysisResultService, consumptionAnalysisViewAssembler,
                assetAnalysisResultService, assetAnalysisViewAssembler, null, null);
    }

    ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService,
            GoalPersistenceService goalPersistenceService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler,
            AssetAnalysisResultService assetAnalysisResultService,
            AssetAnalysisViewAssembler assetAnalysisViewAssembler,
            ProductRecommendationResultService productRecommendationResultService
    ) {
        this(
                conversationService,
                persistenceService,
                chatService,
                goalPersistenceService,
                consumptionAnalysisResultService,
                consumptionAnalysisViewAssembler,
                assetAnalysisResultService,
                assetAnalysisViewAssembler,
                productRecommendationResultService,
                null
        );
    }

    ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService,
            GoalPersistenceService goalPersistenceService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler,
            MissionGenerationService missionGenerationService
    ) {
        this(conversationService, persistenceService, chatService, goalPersistenceService,
                consumptionAnalysisResultService, consumptionAnalysisViewAssembler,
                null, null,
                null,
                missionGenerationService == null
                        ? null
                        : new MissionGenerationWorker(missionGenerationService));
    }

    ConversationMessageService(
            ConversationService conversationService,
            ChatMessagePersistenceService persistenceService,
            ChatService chatService,
            GoalPersistenceService goalPersistenceService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            ConsumptionAnalysisViewAssembler consumptionAnalysisViewAssembler
    ) {
        this(conversationService, persistenceService, chatService, goalPersistenceService,
                consumptionAnalysisResultService, consumptionAnalysisViewAssembler,
                null, null, null, null);
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
        Map<Long, AssetAnalysisView> loadedAssetAnalyses = assetAnalysisResultService == null
                ? Map.of()
                : assetAnalysisResultService.findByAssistantMessageIds(assistantMessageIds);
        Map<Long, AssetAnalysisView> assetAnalyses = loadedAssetAnalyses == null
                ? Map.of()
                : loadedAssetAnalyses;
        Map<Long, Map<String, Object>> loadedProductRecommendations =
                productRecommendationResultService == null
                        ? Map.of()
                        : productRecommendationResultService.findByAssistantMessageIds(
                                assistantMessageIds);
        Map<Long, Map<String, Object>> productRecommendations =
                loadedProductRecommendations == null
                        ? Map.of()
                        : loadedProductRecommendations;
        return messages.stream()
                .map(message -> ChatMessageResponse.from(
                        message,
                        analyses.get(message.getMessageId()),
                        assetAnalyses.get(message.getMessageId()),
                        productRecommendations.get(message.getMessageId())))
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
        return sendMessage(conversationId, currentUserId, request, null);
    }

    public SendConversationMessageResponse sendMessage(
            Long conversationId,
            Long currentUserId,
            SendConversationMessageRequest request,
            String requestId
    ) {
        validateRequest(currentUserId, request);
        conversationService.validateOwnership(
                conversationId,
                currentUserId
        );
        long requestStartedAt = System.nanoTime();
        boolean isFirstMessage = persistenceService.hasNoMessages(conversationId);

        String content = request.getMessage().trim();
        boolean goalSettingRequest = isGoalSettingRequest(content, request.getChatMode());
        String chatMode = goalSettingRequest ? GOAL_SETTING_MODE : request.getChatMode();
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
        long summaryStartedAt = System.nanoTime();
        String summary = refreshSummary(conversationId, memory, storedMessages, requestId);
        LOGGER.info(String.format(
                "[WALLO_TIMING] conversation.summary requestId=%s conversationId=%d elapsedMs=%d",
                requestId,
                conversationId,
                elapsedMillis(summaryStartedAt)
        ));
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
        boolean consumptionAnalysisRequest = isConsumptionAnalysisRequest(content);
        long chatStartedAt = System.nanoTime();
        ChatRequest aiRequest = new ChatRequest(
                content,
                isFirstMessage && !consumptionAnalysisRequest && !goalSettingRequest,
                summary,
                history
        )
                .withChatMode(chatMode)
                .withGoalDraft(goalDraft)
                .withGoalAlreadyExists(goalAlreadyExists)
                .withPreviousConsumptionPeriod(previousConsumptionPeriod);
        ChatResponse aiResponse = requestId == null
                ? chatService.chat(aiRequest, currentUserId)
                : chatService.chat(aiRequest, currentUserId, requestId);
        LOGGER.info(String.format(
                "[WALLO_TIMING] conversation.chat requestId=%s conversationId=%d elapsedMs=%d",
                requestId,
                conversationId,
                elapsedMillis(chatStartedAt)
        ));
        long goalPersistenceStartedAt = System.nanoTime();
        GoalInterviewDto.Result persistedGoalInterview = goalPersistenceService.applyResult(
                currentUserId,
                conversationId,
                aiResponse.goalInterview()
        );
        LOGGER.info(String.format(
                "[WALLO_TIMING] conversation.goalPersistence requestId=%s conversationId=%d elapsedMs=%d action=%s",
                requestId,
                conversationId,
                elapsedMillis(goalPersistenceStartedAt),
                aiResponse.goalInterview() == null ? null : aiResponse.goalInterview().getAction()
        ));
        ChatMessage assistantMessage = persistenceService.saveMessage(
                conversationId,
                ASSISTANT_ROLE,
                aiResponse.answer()
        );
        ConsumptionAnalysisView consumptionAnalysis =
                consumptionAnalysisViewAssembler.assemble(
                        aiResponse.consumptionAnalysis());
        if (consumptionAnalysis != null && !aiResponse.consumptionAnalysisReused()) {
            boolean firstAnalysis = consumptionAnalysisResultService.save(
                    currentUserId,
                    assistantMessage.getMessageId(),
                    content,
                    aiResponse.consumptionAnalysis(),
                    aiResponse.answer()
            );
            if (firstAnalysis && missionGenerationWorker != null) {
                missionGenerationWorker.generate(currentUserId);
            }
        }
        AssetAnalysisView assetAnalysis = assetAnalysisViewAssembler == null
                ? null
                : assetAnalysisViewAssembler.assemble(aiResponse.assetAnalysis());
        if (assetAnalysis != null && assetAnalysisResultService != null) {
            assetAnalysisResultService.save(
                    currentUserId,
                    assistantMessage.getMessageId(),
                    content,
                    aiResponse.assetAnalysis(),
                    aiResponse.answer()
            );
        }
        Map<String, Object> productRecommendation = aiResponse.productRecommendation();
        if (productRecommendationResultService != null) {
            productRecommendationResultService.save(
                    currentUserId,
                    assistantMessage.getMessageId(),
                    content,
                    productRecommendation,
                    aiResponse.answer()
            );
        }
        if (isFirstMessage) {
            String title;
            if (consumptionAnalysisRequest) {
                title = CONSUMPTION_ANALYSIS_TITLE;
            } else if (goalSettingRequest) {
                title = GOAL_SETTING_TITLE;
            } else {
                title = aiResponse.title() == null
                        || aiResponse.title().isBlank()
                        ? content
                        : aiResponse.title();
            }
            conversationService.updateAfterUserMessage(conversationId, title);
        } else {
            conversationService.touch(conversationId);
        }

        LOGGER.info(String.format(
                "[WALLO_TIMING] conversation.total requestId=%s conversationId=%d userId=%d elapsedMs=%d goalSetting=%s",
                requestId,
                conversationId,
                currentUserId,
                elapsedMillis(requestStartedAt),
                goalSettingRequest
        ));

        return new SendConversationMessageResponse(
                ChatMessageResponse.from(userMessage),
                ChatMessageResponse.from(
                        assistantMessage,
                        consumptionAnalysis,
                        assetAnalysis,
                        productRecommendation),
                persistedGoalInterview == null
                        ? aiResponse.goalInterview()
                        : persistedGoalInterview,
                consumptionAnalysis,
                assetAnalysis,
                productRecommendation
        );
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean isConsumptionAnalysisRequest(String message) {
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return normalized.contains("소비분석")
                || (normalized.contains("소비") && normalized.contains("분석"));
    }

    private boolean isGoalSettingRequest(String message, String chatMode) {
        if (GOAL_SETTING_MODE.equalsIgnoreCase(chatMode)) {
            return true;
        }

        String normalized = message.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replaceAll("[.!?~]+", "");
        return "목표를설정하고싶어요".equals(normalized);
    }

    private String refreshSummary(
            Long conversationId,
            Conversation memory,
            List<ChatMessage> messages,
            String requestId
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
        SummarizeConversationRequest summaryRequest =
                new SummarizeConversationRequest(existingSummary, summaryTargets);
        SummarizeConversationResponse response = requestId == null
                ? chatService.summarize(summaryRequest)
                : chatService.summarize(summaryRequest, requestId);
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
