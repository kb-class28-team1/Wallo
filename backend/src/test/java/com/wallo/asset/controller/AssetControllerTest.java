package com.wallo.asset.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.service.AssetService;
import com.wallo.asset.service.AssetSyncOrchestrator;
import com.wallo.asset.service.ExpenseService;
import com.wallo.auth.CurrentUserProvider;
import java.util.Collections;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetControllerTest {

    private final AssetService assetService = mock(AssetService.class);
    private final ExpenseService expenseService = mock(ExpenseService.class);
    private final AssetSyncOrchestrator assetSyncOrchestrator = mock(AssetSyncOrchestrator.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AssetController(
                        assetService,
                        expenseService,
                        assetSyncOrchestrator,
                        currentUserProvider
                )
        ).build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void getAssetsReturnsCommonResponse() throws Exception {
        when(assetService.getAssets(7L)).thenReturn(new AssetDto.Response(
                39_900_000L,
                38_400_000L,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        ));

        String responseBody = mockMvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"totalAssets\":39900000"));

        verify(assetService).getAssets(7L);
    }

    @Test
    void getExpensesPassesPaginationAndDatesToService() throws Exception {
        when(expenseService.getExpenseSummary(eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExpenseDto.Summary(
                        155_000L,
                        3_000_000L,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        new ExpenseDto.Pagination(1, 3, 25L, true)
                ));

        String responseBody = mockMvc.perform(get("/api/assets/expense")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("page", "1")
                        .param("size", "10")
                        .param("category", "FOOD,CAFE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"totalExpense\":155000"));
        assertTrue(responseBody.contains("\"dailyBreakdown\":[]"));
        assertTrue(responseBody.contains("\"pagination\":{"));
        assertTrue(responseBody.contains("\"currentPage\":1"));
        assertTrue(responseBody.contains("\"totalPages\":3"));
        assertTrue(responseBody.contains("\"totalElements\":25"));
        assertTrue(responseBody.contains("\"hasNext\":true"));

        ArgumentCaptor<ExpenseDto.SearchCondition> captor = ArgumentCaptor.forClass(
                ExpenseDto.SearchCondition.class
        );
        verify(expenseService).getExpenseSummary(eq(7L), captor.capture());
        assertEquals("2026-07-01", captor.getValue().getStartDate());
        assertEquals("2026-07-31", captor.getValue().getEndDate());
        assertEquals(1, captor.getValue().getPage());
        assertEquals(10, captor.getValue().getSize());
        assertEquals("FOOD,CAFE", captor.getValue().getCategory());
    }

    @Test
    void getExpensesReturnsBadRequestWhenAnyRequiredParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/assets/expense")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateExpenseCategoryPassesCurrentUserTransactionAndRequestToService() throws Exception {
        mockMvc.perform(patch("/api/assets/expense/123/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"INCOME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<ExpenseDto.CategoryUpdateRequest> captor = ArgumentCaptor.forClass(
                ExpenseDto.CategoryUpdateRequest.class
        );
        verify(expenseService).updateTransactionCategory(eq(7L), eq(123L), captor.capture());
        assertEquals("INCOME", captor.getValue().getCategory());
    }

    @Test
    void syncAssetsUsesCurrentUserAndReturnsSyncSummary() throws Exception {
        when(assetSyncOrchestrator.syncNow(7L)).thenReturn(
                new AssetSyncDto.SyncResponse(
                        LocalDateTime.of(2026, 8, 11, 15, 30),
                        3,
                        42,
                        0
                )
        );

        mockMvc.perform(post("/api/assets/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inserted").value(3))
                .andExpect(jsonPath("$.data.updated").value(42))
                .andExpect(jsonPath("$.data.failedConnections").value(0));

        verify(assetSyncOrchestrator).syncNow(7L);
    }

    @Test
    void syncAssetsReturnsFailedConnectionCountFromOrchestrator() throws Exception {
        when(assetSyncOrchestrator.syncNow(7L)).thenReturn(
                new AssetSyncDto.SyncResponse(
                        LocalDateTime.of(2026, 8, 11, 15, 30),
                        0,
                        12,
                        1
                )
        );

        mockMvc.perform(post("/api/assets/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inserted").value(0))
                .andExpect(jsonPath("$.data.updated").value(12))
                .andExpect(jsonPath("$.data.failedConnections").value(1));

        verify(assetSyncOrchestrator).syncNow(7L);
    }
}
