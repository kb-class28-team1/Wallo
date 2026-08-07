package com.wallo.challenge.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collections;
import com.wallo.auth.CurrentUserProvider;
import com.wallo.challenge.dto.response.WeeklyRankingResponse;
import com.wallo.challenge.service.ChallengeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChallengeControllerTest {

    private ChallengeService challengeService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        challengeService = mock(ChallengeService.class);
        currentUserProvider = mock(CurrentUserProvider.class);

        ChallengeController controller =
                new ChallengeController(challengeService, currentUserProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsCurrentUsersWeeklyRanking() throws Exception {
        WeeklyRankingResponse response = WeeklyRankingResponse.of(
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2026, 8, 2),
                Collections.emptyList(),
                null);
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);
        when(challengeService.getWeeklyRanking(1L)).thenReturn(response);

        mockMvc.perform(get("/api/challenges/rankings/weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-07-27"))
                .andExpect(jsonPath("$.endDate").value("2026-08-02"))
                .andExpect(jsonPath("$.rankings").isArray())
                .andExpect(jsonPath("$.rankings").isEmpty())
                .andExpect(jsonPath("$.myRanking").doesNotExist());

        verify(currentUserProvider).getCurrentUserId();
        verify(challengeService).getWeeklyRanking(1L);
    }

    @Test
    void leavesCurrentChallengeForAuthenticatedUser() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(1L);

        mockMvc.perform(delete("/api/challenges/102/membership"))
                .andExpect(status().isNoContent());

        verify(currentUserProvider).getCurrentUserId();
        verify(challengeService).leaveChallenge(1L, 102L);
    }
}
