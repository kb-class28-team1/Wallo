package com.wallo.asset.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.asset.dto.ReportDto;
import com.wallo.asset.mapper.ReportMapper;
import com.wallo.asset.service.ReportService;
import com.wallo.auth.CurrentUserProvider;
import com.wallo.auth.SessionCurrentUserProvider;
import com.wallo.common.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReportControllerTest {

    @Test
    void returnsTaxSettlementWithCombinedCardSpending() throws Exception {
        ReportService reportService = mock(ReportService.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        when(currentUserProvider.getCurrentUserId()).thenReturn(7L);
        when(reportService.getTaxSettlement(7L, 2026))
                .thenReturn(new ReportDto.TaxSettlement(
                        50_000_000L,
                        12_500_000L,
                        11_500_000L,
                        3_000_000L,
                        8_500_000L
                ));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportController(reportService, currentUserProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String responseBody = mockMvc.perform(get("/api/reports/tax-settlement")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(responseBody.contains("\"success\":true"));
        assertTrue(responseBody.contains("\"creditCardThreshold\":12500000"));
        assertTrue(responseBody.contains("\"cardSpentYtd\":11500000"));
        assertTrue(responseBody.contains("\"creditCardSpentYtd\":3000000"));
        assertTrue(responseBody.contains("\"checkCardSpentYtd\":8500000"));
        verify(reportService).getTaxSettlement(eq(7L), eq(2026));
    }

    @Test
    void returnsUnauthorizedWithoutLoginSession() throws Exception {
        ReportService reportService = new ReportService(mock(ReportMapper.class));
        ReportController reportController = new ReportController(
                reportService,
                new SessionCurrentUserProvider()
        );
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(reportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        String responseBody = mockMvc.perform(get("/api/reports/insights"))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(responseBody.contains("\"success\":false"));
        assertTrue(responseBody.contains("\"code\":\"AUTH_001\""));
        assertTrue(responseBody.contains("\"message\":\"로그인이 필요합니다.\""));
    }
}
