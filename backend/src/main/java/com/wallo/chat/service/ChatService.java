package com.wallo.chat.service;

import com.wallo.asset.dto.GoalAssetContextDto;
import com.wallo.asset.service.AssetService;
import com.wallo.asset.service.ConsumptionAnalysisContextService;
import com.wallo.chat.client.PythonAiClient;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.dto.SummarizeConversationRequest;
import com.wallo.chat.dto.SummarizeConversationResponse;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ChatService {
    private final PythonAiClient pythonAiClient;
    private final AssetService assetService;
    private final ConsumptionAnalysisContextService consumptionAnalysisContextService;
    private final ConsumptionAnalysisResultService consumptionAnalysisResultService;

    @Autowired
    public ChatService(PythonAiClient pythonAiClient, AssetService assetService,
                       ConsumptionAnalysisContextService consumptionAnalysisContextService,
                       ConsumptionAnalysisResultService consumptionAnalysisResultService) {
        this.pythonAiClient = pythonAiClient;
        this.assetService = assetService;
        this.consumptionAnalysisContextService = consumptionAnalysisContextService;
        this.consumptionAnalysisResultService = consumptionAnalysisResultService;
    }

    ChatService(PythonAiClient pythonAiClient, AssetService assetService) {
        this(pythonAiClient, assetService, null, null);
    }

    public ChatResponse chat(ChatRequest request, long currentUserId) {
        if (request == null
                || request.message() == null
                || request.message().isBlank()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }

        GoalAssetContextDto.Response financialContext =
                assetService.getGoalAssetContext(currentUserId);
        ChatRequest aiRequest = request.withFinancialContext(financialContext);
        if (consumptionAnalysisContextService != null) {
            aiRequest = aiRequest.withConsumptionContext(
                    consumptionAnalysisContextService.getContext(currentUserId));
        }
        ChatResponse response = pythonAiClient.chat(aiRequest);
        if (consumptionAnalysisResultService != null
                && response.consumptionAnalysis() != null) {
            consumptionAnalysisResultService.save(
                    currentUserId,
                    request.message(),
                    response.consumptionAnalysis(),
                    response.answer()
            );
        }
        return response;
    }

    public SummarizeConversationResponse summarize(SummarizeConversationRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("요약할 대화가 필요합니다.");
        }
        return pythonAiClient.summarize(request);
    }
}
