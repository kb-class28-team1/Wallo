package com.wallo.asset.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.service.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BudgetControllerTest {

    private final BudgetService budgetService = mock(BudgetService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BudgetController(budgetService)).build();
    }

    @Test
    void getBudgetReturnsCommonResponse() throws Exception {
        when(budgetService.getBudgetSummary(1L, "2026-07"))
                .thenReturn(new BudgetDto.Summary("2026-07", 500_000L, 350_000L));

        String responseBody = mockMvc.perform(get("/api/budgets").param("targetMonth", "2026-07"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"targetMonth\":\"2026-07\""));
        assertTrue(responseBody.contains("\"totalAmount\":500000"));

        verify(budgetService).getBudgetSummary(1L, "2026-07");
    }

    @Test
    void upsertBudgetPassesRequestToService() throws Exception {
        when(budgetService.upsertBudget(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new BudgetDto.Summary("2026-07", 500_000L, 350_000L));

        String responseBody = mockMvc.perform(put("/api/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMonth\":\"2026-07\",\"totalAmount\":500000}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"spentAmount\":350000"));

        ArgumentCaptor<BudgetDto.UpsertRequest> captor = ArgumentCaptor.forClass(
                BudgetDto.UpsertRequest.class
        );
        verify(budgetService).upsertBudget(eq(1L), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("2026-07", captor.getValue().getTargetMonth());
        org.junit.jupiter.api.Assertions.assertEquals(500_000L, captor.getValue().getTotalAmount());
    }
}
