package com.wallo.feed.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.client.AiServerException;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;

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
                    assertTrue(prompt.contains("사용자가 무엇을 했는지"));
                    assertTrue(prompt.contains("화면에서 실제로 확인한 핵심 물품·재료와 개수"));
                    assertTrue(prompt.contains("시장 가격·재료비·차액은 summary에서 추정하지 마세요"));
                    assertTrue(prompt.contains("GATHERED"));
                    assertTrue(prompt.contains("actualCost는 0"));
                    assertTrue(prompt.contains("사용자가 직접 얻은 것으로"));
                    assertTrue(prompt.contains("화면에서 확인한 실제 개체 수"));
                    assertTrue(prompt.contains("kg·g 같은 무게 단위는 사용하지 마세요"));
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
