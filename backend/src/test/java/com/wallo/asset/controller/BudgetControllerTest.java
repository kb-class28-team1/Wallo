package com.wallo.asset.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.service.BudgetService;
import com.wallo.auth.CurrentUserProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BudgetControllerTest {

    private final BudgetService budgetService = mock(BudgetService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BudgetController(budgetService, currentUserProvider)).build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void getBudgetReturnsCommonResponse() throws Exception {
        when(budgetService.getBudgetSummary(7L, "2026-07"))
                .thenReturn(new BudgetDto.Summary("2026-07", 500_000L, 350_000L));

        String responseBody = mockMvc.perform(get("/api/budgets").param("targetMonth", "2026-07"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"targetMonth\":\"2026-07\""));
        assertTrue(responseBody.contains("\"totalAmount\":500000"));

        verify(budgetService).getBudgetSummary(7L, "2026-07");
    }

    @Test
    void upsertBudgetPassesRequestToService() throws Exception {
        when(budgetService.upsertBudget(eq(7L), org.mockito.ArgumentMatchers.any()))
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
        verify(budgetService).upsertBudget(eq(7L), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("2026-07", captor.getValue().getTargetMonth());
        org.junit.jupiter.api.Assertions.assertEquals(500_000L, captor.getValue().getTotalAmount());
    }

    @Test
    void getCategoryBudgetsReturnsCategorySummary() throws Exception {
        when(budgetService.getCategoryBudgetSummary(7L, "2026-08"))
                .thenReturn(new BudgetDto.CategorySummary(
                        "2026-08",
                        1_000_000L,
                        300_000L,
                        700_000L,
                        450_000L,
                        550_000L,
                        new java.math.BigDecimal("45.00"),
                        false,
                        List.of(new BudgetDto.Category(
                                "FOOD",
                                300_000L,
                                450_000L,
                                -150_000L,
                                new java.math.BigDecimal("150.00"),
                                true
                        ))
                ));

        mockMvc.perform(get("/api/budgets/categories").param("targetMonth", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(1_000_000))
                .andExpect(jsonPath("$.data.categories[0].category").value("FOOD"))
                .andExpect(jsonPath("$.data.categories[0].overBudget").value(true));

        verify(budgetService).getCategoryBudgetSummary(7L, "2026-08");
    }

    @Test
    void upsertCategoryBudgetsPassesBatchRequestToService() throws Exception {
        when(budgetService.upsertCategoryBudgets(eq(7L), any()))
                .thenReturn(new BudgetDto.CategorySummary(
                        "2026-08",
                        1_000_000L,
                        300_000L,
                        700_000L,
                        450_000L,
                        550_000L,
                        new java.math.BigDecimal("45.00"),
                        false,
                        List.of()
                ));

        mockMvc.perform(put("/api/budgets/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetMonth": "2026-08",
                                  "totalAmount": 1000000,
                                  "categoryBudgets": [
                                    {"category": "FOOD", "budgetAmount": 300000}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unallocatedAmount").value(700_000));

        ArgumentCaptor<BudgetDto.CategoryUpsertRequest> captor =
                ArgumentCaptor.forClass(BudgetDto.CategoryUpsertRequest.class);
        verify(budgetService).upsertCategoryBudgets(eq(7L), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("2026-08", captor.getValue().getTargetMonth());
        org.junit.jupiter.api.Assertions.assertEquals(1_000_000L, captor.getValue().getTotalAmount());
        org.junit.jupiter.api.Assertions.assertEquals(
                "FOOD",
                captor.getValue().getCategoryBudgets().get(0).getCategory()
        );
    }
}
