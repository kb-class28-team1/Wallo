package com.wallo.feed.price;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** SerpApi Google Shopping 응답을 가격 후보 목록으로 변환한다. */
public class SerpApiShoppingPriceClient implements ShoppingPriceClient {
    private static final Logger log = LoggerFactory.getLogger(SerpApiShoppingPriceClient.class);
    private static final int MAX_PRICE = 10_000_000;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final int maxResults;

    public SerpApiShoppingPriceClient(
            RestTemplate restTemplate, String baseUrl, String apiKey, int maxResults) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.maxResults = Math.max(1, Math.min(40, maxResults));
    }

    @Override
    public List<ShoppingPriceCandidate> search(String itemName, String brand, String unit) {
        String query = buildQuery(itemName, brand, unit);
        if (query.isBlank()) {
            return List.of();
        }

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("engine", "google_shopping")
                    .queryParam("q", query)
                    .queryParam("location", "Seoul, South Korea")
                    .queryParam("google_domain", "google.co.kr")
                    .queryParam("gl", "kr")
                    .queryParam("hl", "ko")
                    .queryParam("api_key", apiKey)
                    .build()
                    .encode()
                    .toUri();
            JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
            return parseCandidates(response);
        } catch (RestClientException exception) {
            // 시세 검색 실패가 피드 업로드나 영상 분석 실패로 이어지지 않도록 빈 결과로 격리한다.
            log.warn("쇼핑 가격 검색에 실패했습니다: {}", exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<ShoppingPriceCandidate> parseCandidates(JsonNode response) {
        if (response == null || !response.path("shopping_results").isArray()) {
            return List.of();
        }

        List<ShoppingPriceCandidate> candidates = new ArrayList<>();
        for (JsonNode result : response.path("shopping_results")) {
            if (candidates.size() >= maxResults) {
                break;
            }
            if (result.hasNonNull("second_hand_condition") || result.hasNonNull("installment")) {
                continue;
            }
            String title = text(result, "title");
            String sourceUrl = text(result, "product_link");
            double extractedPrice = result.path("extracted_price").asDouble(0);
            if (title.isBlank() || sourceUrl.isBlank()
                    || !Double.isFinite(extractedPrice)
                    || extractedPrice <= 0 || extractedPrice > MAX_PRICE) {
                continue;
            }
            int price = (int) Math.round(extractedPrice);
            String source = text(result, "source");
            candidates.add(new ShoppingPriceCandidate(
                    title,
                    price,
                    source.isBlank() ? "Google Shopping" : source,
                    sourceUrl,
                    text(result, "delivery")));
        }
        return List.copyOf(candidates);
    }

    private String buildQuery(String itemName, String brand, String unit) {
        StringBuilder query = new StringBuilder();
        append(query, brand);
        append(query, itemName);
        append(query, unit);
        return query.toString().trim();
    }

    private void append(StringBuilder target, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim();
        if (target.toString().contains(normalized)) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(normalized);
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }
}
