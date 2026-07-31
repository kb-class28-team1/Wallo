package com.wallo.asset.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wallo.asset.mapper.ReportMapper;
import com.wallo.asset.service.ReportService;
import com.wallo.auth.SessionCurrentUserProvider;
import com.wallo.common.exception.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReportControllerTest {

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
