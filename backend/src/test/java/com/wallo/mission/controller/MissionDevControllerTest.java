package com.wallo.mission.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.mission.dto.MissionGenerationDto;
import com.wallo.mission.service.MissionGenerationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MissionDevControllerTest {
    @Test
    void previewsAuthenticatedUsersMissionsWhenEnabled() throws Exception {
        MissionGenerationService service = org.mockito.Mockito.mock(MissionGenerationService.class);
        CurrentUserProvider user = org.mockito.Mockito.mock(CurrentUserProvider.class);
        when(user.getCurrentUserId()).thenReturn(7L);
        when(service.preview(7L)).thenReturn(new MissionGenerationDto.Response(
                List.of(), "personalized-mission-v1"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MissionDevController(service, user, true)).build();

        mvc.perform(post("/api/dev/missions/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promptVersion").value("personalized-mission-v1"));
        verify(service).preview(7L);
    }

    @Test
    void hidesDeveloperApiWhenDisabled() throws Exception {
        MissionGenerationService service = org.mockito.Mockito.mock(MissionGenerationService.class);
        CurrentUserProvider user = org.mockito.Mockito.mock(CurrentUserProvider.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new MissionDevController(service, user, false)).build();

        mvc.perform(post("/api/dev/missions/generate"))
                .andExpect(status().isNotFound());
        verify(user, never()).getCurrentUserId();
    }
}
