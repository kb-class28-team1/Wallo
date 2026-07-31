package com.wallo.asset.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.auth.UnauthenticatedException;
import com.wallo.asset.dto.AnnualSalaryDto;
import com.wallo.asset.service.AnnualSalaryService;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnnualSalaryControllerTest {

    private AnnualSalaryService annualSalaryService;
    private CurrentUserProvider currentUserProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        annualSalaryService = mock(AnnualSalaryService.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AnnualSalaryController(
                        annualSalaryService,
                        currentUserProvider
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updatesCurrentUsersAnnualSalary() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
        when(annualSalaryService.updateAnnualSalary(eq(7L), any()))
                .thenReturn(new AnnualSalaryDto.Response(50_000_000L));

        String responseBody = mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualSalary\":50000000}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"annualSalary\":50000000"));

        ArgumentCaptor<AnnualSalaryDto.UpdateRequest> captor =
                ArgumentCaptor.forClass(AnnualSalaryDto.UpdateRequest.class);
        verify(annualSalaryService).updateAnnualSalary(eq(7L), captor.capture());
        assertEquals(50_000_000L, captor.getValue().getAnnualSalary());
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenThrow(new UnauthenticatedException());

        mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualSalary\":50000000}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAnnualSalaryReturnsBadRequest() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
        when(annualSalaryService.updateAnnualSalary(eq(7L), any()))
                .thenThrow(new CustomException(ErrorCode.INVALID_ANNUAL_SALARY));

        String responseBody = mockMvc.perform(patch("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualSalary\":0}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"code\":\"PROFILE_001\""));
    }
}
