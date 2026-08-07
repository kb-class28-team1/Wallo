package com.wallo.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.GoalAssetContextDto;
import com.wallo.asset.service.AssetService;
import com.wallo.chat.client.PythonAiClient;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    private final PythonAiClient pythonAiClient = mock(PythonAiClient.class);
    private final AssetService assetService = mock(AssetService.class);
    private final ChatService chatService = new ChatService(pythonAiClient, assetService);

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
