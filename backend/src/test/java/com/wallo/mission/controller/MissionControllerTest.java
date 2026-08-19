package com.wallo.mission.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.dto.MissionVerificationDto;
import com.wallo.mission.service.DailyMissionService;
import com.wallo.mission.service.MissionCompletionService;
import com.wallo.mission.service.MissionVerificationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MissionControllerTest {
    @Test
    void completesSelfCheckMissionForCurrentUser() throws Exception {
        DailyMissionService dailyService = org.mockito.Mockito.mock(DailyMissionService.class);
        MissionVerificationService verificationService =
                org.mockito.Mockito.mock(MissionVerificationService.class);
        MissionCompletionService completionService =
                org.mockito.Mockito.mock(MissionCompletionService.class);
        CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(completionService.selfCheck(7L, 1L)).thenReturn(new MissionVerificationDto.Response(
                1L, null, "PASS", 1.0, "완료", "COMPLETED", 10));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MissionController(
                dailyService, currentUser, verificationService, completionService)).build();

        mockMvc.perform(post("/api/missions/1/self-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionStatus").value("COMPLETED"));
        verify(completionService).selfCheck(7L, 1L);
    }

    @Test
    void verifiesTransactionMissionForCurrentUser() throws Exception {
        DailyMissionService dailyService = org.mockito.Mockito.mock(DailyMissionService.class);
        MissionVerificationService verificationService =
                org.mockito.Mockito.mock(MissionVerificationService.class);
        MissionCompletionService completionService =
                org.mockito.Mockito.mock(MissionCompletionService.class);
        CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(completionService.verifyTransaction(7L, 2L)).thenReturn(
                new MissionVerificationDto.Response(
                        2L, null, "PASS", 1.0, "거래 확인", "COMPLETED", 10));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MissionController(
                dailyService, currentUser, verificationService, completionService)).build();

        mockMvc.perform(post("/api/missions/2/transaction/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PASS"));
        verify(completionService).verifyTransaction(7L, 2L);
    }

    @Test
    void getsTodayMissionsForAuthenticatedUserOnly() throws Exception {
        DailyMissionService service = org.mockito.Mockito.mock(DailyMissionService.class);
        CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(service.getOrAssignToday(7L)).thenReturn(new TodayMissionResponse(
                LocalDate.of(2026, 8, 17), List.of(new TodayMissionResponse.Item(
                1L, "집밥 먹기", "한 끼를 만들어 먹어요", "FOOD", 10,
                "MEDIA_AI", "음식이 보이도록 촬영하세요", "ASSIGNED", false))));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MissionController(service, currentUser)).build();

        mockMvc.perform(get("/api/missions/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.missions.length()").value(1))
                .andExpect(jsonPath("$.missions[0].title").value("집밥 먹기"))
                .andExpect(jsonPath("$.missions[0].completed").value(false));
        verify(currentUser).getCurrentUserId();
        verify(service).getOrAssignToday(7L);
    }

    @Test
    void returnsWaitingAnalysisStatusWithHttpOk() throws Exception {
        DailyMissionService service = org.mockito.Mockito.mock(DailyMissionService.class);
        CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(service.getOrAssignToday(7L)).thenReturn(
                TodayMissionResponse.waitingForAnalysis(LocalDate.of(2026, 8, 17)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MissionController(service, currentUser)).build();

        mockMvc.perform(get("/api/missions/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_ANALYSIS"))
                .andExpect(jsonPath("$.missions").isEmpty());
    }
}
