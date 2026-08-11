package com.wallo.feed.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.client.AiServerException;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class GeminiFeedAnalysisClientTest {

    private final RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiFeedAnalysisClient client;

    @BeforeEach
    void setUp() {
        client = new GeminiFeedAnalysisClient(
                restTemplate, objectMapper, "test-key", "gemini-test");
    }

    @Test
    void retriesOnceAfterTemporaryConnectionFailure() {
        JsonNode response = response("{\"spendingType\":\"REDUCED\","
                + "\"category\":\"CAFE\",\"estimatedSavingAmount\":1500,"
                + "\"summary\":\"메뉴판에서 가격 차이가 확인됩니다.\","
                + "\"confidenceScore\":0.8}");
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("temporary connection failure"))
                .thenReturn(response);

        AnalysisResponse result = client.analyze(media(), "REDUCED", "CAFE");

        assertEquals(1_500, result.estimatedSavingAmount());
        verify(restTemplate, times(2))
                .postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void stopsAfterOneRetryWhenGeminiKeepsReturningServerError() {
        HttpServerErrorException unavailable = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "unavailable", null, new byte[0],
                StandardCharsets.UTF_8);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(unavailable);

        assertThrows(AiServerException.class,
                () -> client.analyze(media(), "REDUCED", "CAFE"));

        verify(restTemplate, times(2))
                .postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void doesNotRetryNonTransientClientError() {
        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "bad request", null, new byte[0],
                StandardCharsets.UTF_8);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(badRequest);

        assertThrows(AiServerException.class,
                () -> client.analyze(media(), "REDUCED", "CAFE"));

        verify(restTemplate, times(1))
                .postForObject(anyString(), any(HttpEntity.class), eq(JsonNode.class));
    }

    private MultipartFile media() {
        return new MockMultipartFile("media", "feed.jpg", "image/jpeg", new byte[]{1});
    }

    private JsonNode response(String generatedJson) {
        JsonNode response = objectMapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) response)
                .putArray("candidates")
                .addObject()
                .putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", generatedJson);
        return response;
    }
}
