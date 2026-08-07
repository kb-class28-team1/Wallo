package com.wallo.goal.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wallo.auth.CurrentUserProvider;
import com.wallo.goal.dto.GoalAccountDto;
import com.wallo.goal.dto.GoalDto;
import com.wallo.goal.service.GoalAccountService;
import com.wallo.goal.service.GoalService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GoalControllerTest {

    private final GoalService goalService = mock(GoalService.class);
    private final GoalAccountService goalAccountService = mock(GoalAccountService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new GoalController(goalService, goalAccountService, currentUserProvider)
        ).setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void getGoalsReturnsTheAuthenticatedUsersGoals() throws Exception {
        when(goalService.getGoals(7L)).thenReturn(List.of(goal()));

        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].goalId").value(31))
                .andExpect(jsonPath("$.data[0].conversationId").value(11))
                .andExpect(jsonPath("$.data[0].title").value("비상금 마련"))
                .andExpect(jsonPath("$.data[0].targetAmount").value(10_000_000))
                .andExpect(jsonPath("$.data[0].targetDate").value("2027-08-01"));

        verify(goalService).getGoals(7L);
    }

    @Test
    void getGoalsReturnsAnEmptyArrayWhenNoGoalExists() throws Exception {
        when(goalService.getGoals(7L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getAvailableAccountsReturnsOnlyGoalAccountCandidates() throws Exception {
        when(goalAccountService.getAvailableAccounts(7L)).thenReturn(List.of(account()));

        mockMvc.perform(get("/api/goals/available-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].accountId").value(101))
                .andExpect(jsonPath("$.data[0].bankName").value("Wallo Bank"))
                .andExpect(jsonPath("$.data[0].accountType").value("입출금"))
                .andExpect(jsonPath("$.data[0].selected").value(false));

        verify(goalAccountService).getAvailableAccounts(7L);
    }

    @Test
    void selectAccountPassesAuthenticatedUserGoalAndAccountIds() throws Exception {
        when(goalAccountService.selectAccount(7L, 31L, 101L)).thenReturn(account());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/goals/31/account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountId").value(101));

        verify(goalAccountService).selectAccount(7L, 31L, 101L);
    }

    @Test
    void getConversationGoalPassesConversationAndAuthenticatedUserIds() throws Exception {
        when(goalService.getGoalByConversationId(7L, 11L)).thenReturn(goal());

        mockMvc.perform(get("/api/conversations/11/goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.goalId").value(31))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(goalService).getGoalByConversationId(7L, 11L);
    }

    @Test
    void getConversationGoalReturnsNullDataWhenNoGoalExists() throws Exception {
        when(goalService.getGoalByConversationId(7L, 11L)).thenReturn(null);

        String responseBody = mockMvc.perform(get("/api/conversations/11/goal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"data\":null"));
    }

    private GoalDto.Response goal() {
        return new GoalDto.Response(
                31L,
                11L,
                "비상금 마련",
                "EMERGENCY_FUND",
                10_000_000L,
                LocalDate.of(2027, 8, 1),
                "비상 상황 대비",
                "HIGH",
                2_000_000L,
                600_000L,
                "ACTIVE",
                LocalDateTime.of(2026, 8, 7, 12, 30),
                LocalDateTime.of(2026, 8, 7, 12, 30)
        );
    }

    private GoalAccountDto.AvailableAccount account() {
        return new GoalAccountDto.AvailableAccount(
                101L,
                "Wallo Bank",
                "생활비 통장",
                "1234-****-7890",
                "입출금",
                2_500_000L,
                "KRW",
                false
        );
    }
}
