package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.AssetAnalysisContextDto;
import com.wallo.asset.dto.GoalAssetContextDto;
import com.wallo.asset.service.AssetAnalysisContextService;
import com.wallo.asset.service.AssetService;
import com.wallo.asset.service.ConsumptionAnalysisContextService;
import com.wallo.chat.client.PythonAiClient;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    private final PythonAiClient pythonAiClient = mock(PythonAiClient.class);
    private final AssetService assetService = mock(AssetService.class);
    private final ChatService chatService = new ChatService(pythonAiClient, assetService);

    @Test
    void sendsCurrentUsersAssetAnalysisContextToAiRequest() {
        AssetAnalysisContextService contextService = mock(AssetAnalysisContextService.class);
        ChatService service = new ChatService(
                pythonAiClient, assetService, null, contextService);
        GoalAssetContextDto.Response financialContext = emptyContext(true);
        AssetAnalysisContextDto assetAnalysisContext = new AssetAnalysisContextDto(
                120_000_000L,
                20_000_000L,
                100_000_000L,
                5_000_000L,
                3_000_000L,
                2_000_000L,
                40.0,
                List.of(new AssetAnalysisContextDto.AssetComposition(
                        "DEPOSIT", 80_000_000L, 66.6666667
                )),
                "2026-08-20T12:00:00+09:00"
        );
        ChatRequest request = new ChatRequest("내 자산을 분석해줘");
        ChatRequest expected = request.withFinancialContext(financialContext)
                .withAssetAnalysisContext(assetAnalysisContext);
        when(assetService.getGoalAssetContext(7L)).thenReturn(financialContext);
        when(contextService.getContext(7L)).thenReturn(assetAnalysisContext);
        when(pythonAiClient.chat(expected)).thenReturn(new ChatResponse("분석 결과입니다.", null));

        ChatResponse response = service.chat(request, 7L);

        assertEquals("분석 결과입니다.", response.answer());
        verify(contextService).getContext(7L);
        assertEquals(120_000_000L, expected.assetAnalysisContext().totalAssets());
        assertEquals(100_000_000L, expected.assetAnalysisContext().netAssets());
        assertEquals(3_000_000L, expected.assetAnalysisContext().monthlyExpense());
        assertEquals("2026-08-20T12:00:00+09:00", expected.assetAnalysisContext().asOf());
        verify(pythonAiClient).chat(expected);
    }

    @Test
    void sendsConsumptionContextAndReturnsCalculation() {
        ConsumptionAnalysisContextService contextService =
                mock(ConsumptionAnalysisContextService.class);
        ChatService service = new ChatService(
                pythonAiClient, assetService, contextService);
        GoalAssetContextDto.Response financialContext = emptyContext(true);
        com.wallo.asset.dto.ConsumptionAnalysisContextDto consumptionContext =
                new com.wallo.asset.dto.ConsumptionAnalysisContextDto(List.of(), null);
        ChatRequest request = new ChatRequest("이번 달 소비를 분석해줘");
        Map<String, Object> calculation = Map.of("totalChange", Map.of("currentAmount", 100000));
        ChatResponse aiResponse = new ChatResponse("분석 결과입니다.", null, null, calculation);
        when(assetService.getGoalAssetContext(7L)).thenReturn(financialContext);
        when(contextService.getContext(7L)).thenReturn(consumptionContext);
        when(pythonAiClient.chat(request.withFinancialContext(financialContext)
                .withConsumptionContext(consumptionContext))).thenReturn(aiResponse);

        ChatResponse response = service.chat(request, 7L);

        assertEquals(calculation, response.consumptionAnalysis());
    }

    @Test
    void returnsAssetAnalysisFromAiResponse() {
        GoalAssetContextDto.Response financialContext = emptyContext(true);
        ChatRequest request = new ChatRequest("analyze assets");
        Map<String, Object> assetAnalysis = Map.of(
                "calculatedMetrics",
                Map.of("totalAssetsKrw", 100_000_000L)
        );
        ChatResponse aiResponse = new ChatResponse(
                "asset analysis answer",
                null,
                null,
                null,
                assetAnalysis
        );
        when(assetService.getGoalAssetContext(7L)).thenReturn(financialContext);
        when(pythonAiClient.chat(request.withFinancialContext(financialContext)))
                .thenReturn(aiResponse);

        ChatResponse response = chatService.chat(request, 7L);

        assertEquals(assetAnalysis, response.assetAnalysis());
        assertEquals(null, response.consumptionAnalysis());
    }

    @Test
    void returnsNonConsumptionResponseWithoutCalculation() {
        ConsumptionAnalysisContextService contextService =
                mock(ConsumptionAnalysisContextService.class);
        ChatService service = new ChatService(
                pythonAiClient, assetService, contextService);
        GoalAssetContextDto.Response financialContext = emptyContext(true);
        com.wallo.asset.dto.ConsumptionAnalysisContextDto consumptionContext =
                new com.wallo.asset.dto.ConsumptionAnalysisContextDto(List.of(), null);
        ChatRequest request = new ChatRequest("안녕");
        when(assetService.getGoalAssetContext(7L)).thenReturn(financialContext);
        when(contextService.getContext(7L)).thenReturn(consumptionContext);
        when(pythonAiClient.chat(request.withFinancialContext(financialContext)
                .withConsumptionContext(consumptionContext)))
                .thenReturn(new ChatResponse("안녕하세요.", null));

        ChatResponse response = service.chat(request, 7L);

        assertEquals(null, response.consumptionAnalysis());
    }

    @Test
    void recalculatesConsumptionAnalysisInsteadOfReusingRecentResult() {
        ConsumptionAnalysisContextService contextService =
                mock(ConsumptionAnalysisContextService.class);
        ChatService service = new ChatService(
                pythonAiClient, assetService, contextService);
        GoalAssetContextDto.Response financialContext = emptyContext(true);
        com.wallo.asset.dto.ConsumptionAnalysisContextDto consumptionContext =
                new com.wallo.asset.dto.ConsumptionAnalysisContextDto(List.of(), null);
        ChatRequest request = new ChatRequest("소비 분석해줘");
        ChatRequest expected = request.withFinancialContext(financialContext)
                .withConsumptionContext(consumptionContext);
        Map<String, Object> calculation = Map.of(
                "totalChange", Map.of("currentAmount", 100000));
        ChatResponse aiResponse = new ChatResponse(
                "새 분석 결과입니다.", null, null, calculation);
        when(assetService.getGoalAssetContext(7L)).thenReturn(financialContext);
        when(contextService.getContext(7L)).thenReturn(consumptionContext);
        when(pythonAiClient.chat(expected)).thenReturn(aiResponse);

        ChatResponse response = service.chat(request, 7L);

        assertEquals("새 분석 결과입니다.", response.answer());
        assertEquals(calculation, response.consumptionAnalysis());
        assertEquals(false, response.consumptionAnalysisReused());
        verify(pythonAiClient).chat(expected);
    }

    @Test
    void addsCurrentUsersFinancialContextToAiRequest() {
        GoalAssetContextDto.Response context = emptyContext(true);
        ChatRequest request = new ChatRequest("여행 목표를 세우고 싶어");
        ChatRequest expected = request.withFinancialContext(context);
        when(assetService.getGoalAssetContext(7L)).thenReturn(context);
        when(pythonAiClient.chat(expected)).thenReturn(new ChatResponse("좋아요.", null));

        ChatResponse response = chatService.chat(request, 7L);

        assertEquals("좋아요.", response.answer());
        verify(assetService).getGoalAssetContext(7L);
        verify(pythonAiClient).chat(expected);
    }

    @Test
    void preservesGoalSettingModeWhenAddingFinancialContext() {
        GoalAssetContextDto.Response context = emptyContext(false);
        ChatRequest request = new ChatRequest("목표를 설정하고 싶어요")
                .withChatMode("GOAL_SETTING");
        ChatRequest expected = request.withFinancialContext(context);
        when(assetService.getGoalAssetContext(7L)).thenReturn(context);
        when(pythonAiClient.chat(expected))
                .thenReturn(new ChatResponse("어떤 목표를 세우고 싶으신가요?", null));

        ChatResponse response = chatService.chat(request, 7L);

        assertEquals("어떤 목표를 세우고 싶으신가요?", response.answer());
        verify(pythonAiClient).chat(expected);
    }

    @Test
    void sendsEmptyFinancialContextWhenUserHasNoAccounts() {
        GoalAssetContextDto.Response context = emptyContext(false);
        ChatRequest request = new ChatRequest("목표를 만들고 싶어");
        when(assetService.getGoalAssetContext(7L)).thenReturn(context);
        when(pythonAiClient.chat(request.withFinancialContext(context)))
                .thenReturn(new ChatResponse("목표를 알려주세요.", null));

        chatService.chat(request, 7L);

        verify(pythonAiClient).chat(request.withFinancialContext(context));
    }

    @Test
    void rejectsBlankMessageBeforeLoadingAssets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chatService.chat(new ChatRequest("  "), 7L)
        );

        verifyNoInteractions(assetService, pythonAiClient);
    }

    private GoalAssetContextDto.Response emptyContext(boolean hasConnectedAccounts) {
        return new GoalAssetContextDto.Response(
                hasConnectedAccounts,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                List.of()
        );
    }
}
