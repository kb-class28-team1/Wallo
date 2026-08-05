package com.wallo.asset.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.service.ConnectionService;
import com.wallo.asset.service.InstitutionService;
import com.wallo.auth.CurrentUserProvider;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConnectionControllerTest {

    private final ConnectionService connectionService = mock(ConnectionService.class);
    private final InstitutionService institutionService = mock(InstitutionService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ConnectionController(
                        connectionService,
                        institutionService,
                        currentUserProvider
                )
        ).build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
    }

    @Test
    void getInstitutionsKeepsExistingEndpoint() throws Exception {
        when(institutionService.getInstitutions()).thenReturn(
                new ConnectionDto.InstitutionResponse(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                )
        );

        mockMvc.perform(get("/api/institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.banks").isArray());

        verify(institutionService).getInstitutions();
    }

    @Test
    void connectAllAssetsKeepsExistingEndpoint() throws Exception {
        when(connectionService.connectAllAssets(eq(7L), any(ConnectionDto.Request.class)))
                .thenReturn(new ConnectionDto.Response(Collections.emptyList()));

        mockMvc.perform(post("/api/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.results").isArray());

        verify(connectionService).connectAllAssets(eq(7L), any(ConnectionDto.Request.class));
    }
}
