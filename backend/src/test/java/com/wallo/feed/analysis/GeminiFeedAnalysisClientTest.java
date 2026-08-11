package com.wallo.feed.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GeminiFeedAnalysisClientTest {

    @Test
    void extractsItemsWithoutChangingTheStableGenerationConfigShape() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String generatedJson = """
                {
                  "spendingType":"SAVED",
                  "category":"FOOD",
                  "estimatedSavingAmount":0,
                  "summary":"우유를 사지 않고 절약했어요.",
                  "confidenceScore":0.91,
                  "actualCost":0,
                  "detectedItems":[{
                    "itemName":"우유",
                    "brand":"서울우유",
                    "unit":"1L×1개",
                    "quantity":2,
                    "confidence":0.9,
                    "evidence":"서울우유 1L 두 개가 보임",
                    "comparisonType":"PRODUCT",
                    "ingredientCostPerUnit":0,
                    "restaurantPricePerUnit":0,
                    "ingredientBasis":""
                  }]
                }
                """;
        JsonNode providerResponse = objectMapper.createObjectNode()
                .set("candidates", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().set("content",
                                objectMapper.createObjectNode().set("parts",
                                        objectMapper.createArrayNode().add(
                                                objectMapper.createObjectNode()
                                                        .put("text", generatedJson))))));
        server.expect(request -> {
                    JsonNode body = objectMapper.readTree(
                            ((MockClientHttpRequest) request).getBodyAsBytes());
                    JsonNode generationConfig = body.path("generationConfig");
                    assertEquals("application/json", generationConfig.path("responseMimeType").asText());
                    assertFalse(generationConfig.has("mediaResolution"));
                    assertFalse(generationConfig.has("responseSchema"));
                    String prompt = body.path("contents").get(0).path("parts").get(0)
                            .path("text").asText();
                    assertTrue(prompt.contains("detectedItems"));
                    assertTrue(prompt.contains("comparisonType"));
                    assertTrue(prompt.contains("GATHERED"));
                    assertTrue(prompt.contains("actualCost는 0"));
                    assertTrue(prompt.contains("ingredientCostPerUnit"));
                    assertTrue(prompt.contains("실제로 보이는 재료"));
                })
                .andRespond(withSuccess(providerResponse.toString(), MediaType.APPLICATION_JSON));

        GeminiFeedAnalysisClient client = new GeminiFeedAnalysisClient(
                restTemplate, objectMapper, "test-key", "gemini-3.6-flash");

        AnalysisResponse result = client.analyze(
                new MockMultipartFile(
                        "media", "milk.jpg", "image/jpeg",
                        "image".getBytes(StandardCharsets.UTF_8)),
                "SAVED", "FOOD");

        assertEquals(1, result.detectedItems().size());
        assertEquals("서울우유", result.detectedItems().get(0).brand());
        assertEquals("1L×1개", result.detectedItems().get(0).unit());
        assertEquals(2, result.detectedItems().get(0).quantity());
        assertEquals("PRODUCT", result.detectedItems().get(0).comparisonType());
        assertEquals(0, result.detectedItems().get(0).ingredientCostPerUnit());
        server.verify();
    }
}
