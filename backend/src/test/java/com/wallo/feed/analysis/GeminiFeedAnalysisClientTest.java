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
                    assertTrue(prompt.contains("ingredientCostPerUnit"));
                    assertTrue(prompt.contains("PRODUCT_COMPARE"));
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
        assertEquals("NONE", result.detectedItems().get(0).comparisonRole());
        assertEquals(0, result.detectedItems().get(0).fallbackUnitPrice());
        server.verify();
    }

    @Test
    void parsesFinishedProductComparisonRolesAndFallbackPrices() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String generatedJson = """
                {
                  "estimatedSavingAmount":2500,
                  "summary":"저렴한 완제품을 선택했어요.",
                  "confidenceScore":0.88,
                  "actualCost":0,
                  "detectedItems":[
                    {
                      "itemName":"브랜드 카페라떼",
                      "brand":"브랜드",
                      "unit":"1개",
                      "quantity":1,
                      "confidence":0.9,
                      "evidence":"비교 대상",
                      "comparisonType":"PRODUCT_COMPARE",
                      "comparisonRole":"REFERENCE",
                      "fallbackUnitPrice":4000
                    },
                    {
                      "itemName":"PB 카페라떼",
                      "brand":"PB",
                      "unit":"1개",
                      "quantity":1,
                      "confidence":0.9,
                      "evidence":"실제 선택",
                      "comparisonType":"PRODUCT_COMPARE",
                      "comparisonRole":"ACTUAL",
                      "fallbackUnitPrice":1500
                    }
                  ]
                }
                """;
        JsonNode providerResponse = objectMapper.createObjectNode()
                .set("candidates", objectMapper.createArrayNode().add(
                        objectMapper.createObjectNode().set("content",
                                objectMapper.createObjectNode().set("parts",
                                        objectMapper.createArrayNode().add(
                                                objectMapper.createObjectNode()
                                                        .put("text", generatedJson))))));
        server.expect(request -> {})
                .andRespond(withSuccess(providerResponse.toString(), MediaType.APPLICATION_JSON));
        GeminiFeedAnalysisClient client = new GeminiFeedAnalysisClient(
                restTemplate, objectMapper, "test-key", "gemini-3.6-flash");

        AnalysisResponse result = client.analyze(
                new MockMultipartFile(
                        "media", "compare.jpg", "image/jpeg",
                        "image".getBytes(StandardCharsets.UTF_8)),
                "REDUCED", "CAFE");

        assertEquals(2, result.detectedItems().size());
        assertEquals("REFERENCE", result.detectedItems().get(0).comparisonRole());
        assertEquals(4_000, result.detectedItems().get(0).fallbackUnitPrice());
        assertEquals("ACTUAL", result.detectedItems().get(1).comparisonRole());
        assertEquals(1_500, result.detectedItems().get(1).fallbackUnitPrice());
        server.verify();
    }
}
