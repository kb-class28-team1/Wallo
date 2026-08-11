package com.wallo.feed.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wallo.chat.client.AiServerException;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/** Google Search grounding을 이용해 시세 캐시가 없는 물품의 최저가 후보를 찾는다. */
public class GeminiPriceSearchClient implements PriceSearchClient {
    private static final String API_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final int MAX_ATTEMPTS = 2;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiPriceSearchClient(
            RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public Optional<PriceSearchResult> findLowestPrice(
            String itemName, String brand, String unit, String category) {
        try {
            ObjectNode request = buildRequest(itemName, brand, unit, category);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode response = postWithRetry(
                    API_ENDPOINT.formatted(model, apiKey), new HttpEntity<>(request, headers));
            JsonNode result = objectMapper.readTree(extractGeneratedText(response));
            int price = Math.max(0, Math.min(10_000_000, result.path("lowestPrice").asInt(0)));
            double confidence = Math.max(0, Math.min(1, result.path("confidence").asDouble(0)));
            if (price <= 0 || confidence < 0.5) {
                return Optional.empty();
            }
            String sourceUrl = firstGroundingUrl(response);
            String declaredUrl = result.path("sourceUrl").asText("").trim();
            if (!declaredUrl.isBlank()) {
                sourceUrl = declaredUrl;
            }
            if (sourceUrl.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PriceSearchResult(
                    nonBlankOr(itemName, result.path("itemName").asText("")),
                    blankToNull(brand),
                    nonBlankOr(unit, "개"),
                    price,
                    nonBlankOr(result.path("source").asText(""), "Google Search"),
                    sourceUrl,
                    confidence));
        } catch (RestClientResponseException exception) {
            throw new AiServerException(
                    "시세 검색 API 요청이 실패했습니다. HTTP " + exception.getRawStatusCode(), exception);
        } catch (RestClientException | IOException exception) {
            throw new AiServerException("시세 검색에 실패했습니다.", exception);
        }
    }

    private ObjectNode buildRequest(String itemName, String brand, String unit, String category) {
        ObjectNode request = objectMapper.createObjectNode();
        request.putArray("contents").addObject().putArray("parts").addObject().put("text", """
                당신은 물품 시세 조사 담당자입니다.
                아래 물품과 단위를 기준으로 현재 공개 판매 페이지에서 확인 가능한 최저 판매가 1개를 찾으세요.
                정확히 같은 물품·규격을 확인할 수 없으면 lowestPrice를 0으로 반환하세요.
                중고가, 배송비 제외 여부, 묶음 수량을 혼동하지 말고, 가격을 상식으로 추정하지 마세요.
                sourceUrl은 실제 검색 결과의 URL이어야 합니다. JSON만 반환하세요.

                물품명: %s
                브랜드/규격: %s
                단위: %s
                카테고리: %s
                """.formatted(itemName, blankToNull(brand) == null ? "미확인" : brand, unit, category));
        request.putArray("tools").addObject().putObject("google_search");
        ObjectNode generationConfig = request.putObject("generationConfig");
        ObjectNode responseFormat = generationConfig.putObject("responseFormat");
        ObjectNode text = responseFormat.putObject("text");
        text.put("mimeType", "application/json");
        text.set("schema", priceSchema());
        return request;
    }

    private ObjectNode priceSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("itemName").put("type", "string");
        properties.putObject("lowestPrice").put("type", "integer").put("minimum", 0);
        properties.putObject("source").put("type", "string");
        properties.putObject("sourceUrl").put("type", "string");
        properties.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
        schema.set("required", values("itemName", "lowestPrice", "source", "sourceUrl", "confidence"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private ArrayNode values(String... items) {
        ArrayNode values = objectMapper.createArrayNode();
        for (String item : items) values.add(item);
        return values;
    }

    private JsonNode postWithRetry(String endpoint, HttpEntity<ObjectNode> request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restTemplate.postForObject(endpoint, request, JsonNode.class);
            } catch (RestClientResponseException exception) {
                if (attempt == MAX_ATTEMPTS || (exception.getRawStatusCode() != 429
                        && exception.getRawStatusCode() < 500)) throw exception;
                pause(attempt);
            } catch (RestClientException exception) {
                if (attempt == MAX_ATTEMPTS) throw exception;
                pause(attempt);
            }
        }
        throw new IllegalStateException("시세 검색이 완료되지 않았습니다.");
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServerException("시세 검색 재시도가 중단되었습니다.", exception);
        }
    }

    private String extractGeneratedText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (part.has("text")) text.append(part.path("text").asText());
            }
        }
        if (text.length() == 0) throw new AiServerException("시세 검색 결과가 비어 있습니다.");
        String value = text.toString().trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        return start >= 0 && end > start ? value.substring(start, end + 1) : value;
    }

    private String firstGroundingUrl(JsonNode response) {
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode chunk : candidate.path("groundingMetadata").path("groundingChunks")) {
                String uri = chunk.path("web").path("uri").asText("").trim();
                if (!uri.isBlank()) return uri;
            }
        }
        return "";
    }

    private String nonBlankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
