package com.wallo.mission.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.mission.dto.TodayMissionResponse;
import com.wallo.mission.service.DailyMissionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MissionControllerTest {
    @Test
    void getsTodayMissionsForAuthenticatedUserOnly() throws Exception {
        DailyMissionService service = org.mockito.Mockito.mock(DailyMissionService.class);
        CurrentUserProvider currentUser = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(currentUser.getCurrentUserId()).thenReturn(7L);
        when(service.getOrAssignToday(7L)).thenReturn(new TodayMissionResponse(
                LocalDate.of(2026, 8, 17), List.of(new TodayMissionResponse.Item(
                1L, 10L, "집밥 먹기", "한 끼를 만들어 먹어요", "FOOD", 10,
                "MEDIA_AI", "음식이 보이도록 촬영하세요", "ASSIGNED", false))));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new MissionController(service, currentUser)).build();

        mockMvc.perform(get("/api/missions/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions.length()").value(1))
                .andExpect(jsonPath("$.missions[0].title").value("집밥 먹기"))
                .andExpect(jsonPath("$.missions[0].completed").value(false));
        verify(currentUser).getCurrentUserId();
        verify(service).getOrAssignToday(7L);
    }
}
