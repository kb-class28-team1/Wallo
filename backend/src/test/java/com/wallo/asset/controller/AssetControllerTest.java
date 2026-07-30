package com.wallo.asset.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.service.AssetService;
import com.wallo.asset.service.ExpenseService;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssetControllerTest {

    private final AssetService assetService = mock(AssetService.class);
    private final ExpenseService expenseService = mock(ExpenseService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AssetController(assetService, expenseService)
        ).build();
    }

    @Test
    void getAssetsReturnsCommonResponse() throws Exception {
        when(assetService.getAssets(1L)).thenReturn(new AssetDto.Response(
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

        verify(assetService).getAssets(1L);
    }

    @Test
    void getExpensesPassesPaginationAndDatesToService() throws Exception {
        when(expenseService.getExpenseSummary(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExpenseDto.Summary(
                        155_000L,
                        3_000_000L,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        0L,
                        0,
                        20
                ));

        String responseBody = mockMvc.perform(get("/api/assets/expense")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"totalExpense\":155000"));

        ArgumentCaptor<ExpenseDto.SearchCondition> captor = ArgumentCaptor.forClass(
                ExpenseDto.SearchCondition.class
        );
        verify(expenseService).getExpenseSummary(eq(1L), captor.capture());
        assertEquals("2026-07-01", captor.getValue().getStartDate());
        assertEquals("2026-07-31", captor.getValue().getEndDate());
        assertEquals(1, captor.getValue().getPage());
        assertEquals(10, captor.getValue().getSize());
    }
}
