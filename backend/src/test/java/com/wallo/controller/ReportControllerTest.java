package com.wallo.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wallo.domain.NewsReportListItem;
import com.wallo.dto.response.ReportListResponse;
import com.wallo.service.NewsReportGenerationService;
import com.wallo.service.NewsService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** GET /api/reports 목록 응답에 news_report 요약(summary)과 analyzed가 올바르게 내려가는지 검증한다. */
class ReportControllerTest {

    private NewsService newsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        newsService = mock(NewsService.class);
        NewsReportGenerationService newsReportGenerationService = mock(NewsReportGenerationService.class);

        ReportController controller = new ReportController(newsService, newsReportGenerationService);

        // 실제 운영 설정(AppConfig.objectMapper())과 동일하게 JavaTimeModule을 등록하고
        // 타임스탬프 배열 대신 ISO 문자열로 직렬화하도록 맞춘다.
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void returnsSummaryAndAnalyzedTrueWhenReportExists() throws Exception {
        ReportListResponse response = ReportListResponse.from(
                NewsReportListItem.builder()
                        .newsId(1L)
                        .title("한국은행, 기준금리 인상")
                        .category("경제")
                        .source("매일경제")
                        .url("https://example.com/1")
                        .publishedAt(LocalDateTime.of(2026, 7, 30, 12, 0))
                        .summary("한국은행이 기준금리를 인상했습니다.")
                        .analyzed(true)
                        .build());
        when(newsService.getReportList()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].summary").value("한국은행이 기준금리를 인상했습니다."))
                .andExpect(jsonPath("$.data[0].analyzed").value(true))
                .andExpect(jsonPath("$.data[0].thumbnailUrl").doesNotExist());

        verify(newsService).getReportList();
    }

    @Test
    void returnsNullSummaryAndAnalyzedFalseWhenReportDoesNotExist() throws Exception {
        ReportListResponse response = ReportListResponse.from(
                NewsReportListItem.builder()
                        .newsId(2L)
                        .title("아직 리포트가 없는 뉴스")
                        .category("경제")
                        .source("매일경제")
                        .url("https://example.com/2")
                        .publishedAt(LocalDateTime.of(2026, 7, 29, 9, 0))
                        .summary(null)
                        .analyzed(false)
                        .build());
        when(newsService.getReportList()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].summary").doesNotExist())
                .andExpect(jsonPath("$.data[0].analyzed").value(false));
    }

    @Test
    void returnsEmptyArrayWhenNoNews() throws Exception {
        when(newsService.getReportList()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
