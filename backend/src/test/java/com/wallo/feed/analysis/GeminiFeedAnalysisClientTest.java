package com.wallo.feed.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;

class GeminiFeedAnalysisClientTest {

    @Test
    void sendsEvidenceFirstPromptWithStructuredOutputAndMediaResolution() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("candidates")
                .addObject()
                .putObject("content")
                .putArray("parts")
                .addObject()
                .put("text", "{\"spendingType\":\"REDUCED\",\"category\":\"CAFE\","
                        + "\"estimatedSavingAmount\":0,\"summary\":\"집에서 커피를 준비했어요.\","
                        + "\"confidenceScore\":0.8}");
        when(restTemplate.postForObject(
                anyString(), org.mockito.ArgumentMatchers.<HttpEntity<ObjectNode>>any(),
                eq(JsonNode.class))).thenReturn(response);

        GeminiFeedAnalysisClient client = new GeminiFeedAnalysisClient(
                restTemplate, objectMapper, "test-key", "gemini-3.6-flash", "high", "high");

        AnalysisResponse result = client.analyze(
                new MockMultipartFile("media", "proof.mp4", "video/mp4", new byte[]{1, 2}),
                "REDUCED", "CAFE");

        ArgumentCaptor<HttpEntity<ObjectNode>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(JsonNode.class));

        JsonNode request = requestCaptor.getValue().getBody();
        assertEquals("MEDIA_RESOLUTION_HIGH",
                request.path("generationConfig").path("mediaResolution").asText());
        assertEquals("high", request.path("generationConfig")
                .path("thinkingConfig").path("thinkingLevel").asText());
        assertEquals("application/json", request.path("generationConfig")
                .path("responseFormat").path("text").path("mimeType").asText());
        assertTrue(request.path("generationConfig").path("responseFormat")
                .path("text").path("schema").path("properties")
                .path("estimatedSavingAmount").has("minimum"));
        assertEquals(0, result.estimatedSavingAmount());
    }
}
