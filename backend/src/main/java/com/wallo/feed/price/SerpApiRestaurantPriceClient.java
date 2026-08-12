package com.wallo.feed.price;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Google 일반 검색의 메뉴·가격 문구를 음식점 판매가 후보로 변환한다. */
public class SerpApiRestaurantPriceClient implements RestaurantPriceClient {
    private static final Logger log = LoggerFactory.getLogger(SerpApiRestaurantPriceClient.class);
    private static final int MIN_PRICE = 500;
    private static final int MAX_PRICE = 1_000_000;
    private static final Pattern WON_PRICE = Pattern.compile(
            "(\\d{1,3}(?:,\\d{3})+|\\d{3,6})\\s*원");

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final int maxResults;

    public SerpApiRestaurantPriceClient(
            RestTemplate restTemplate, String baseUrl, String apiKey, int maxResults) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.maxResults = Math.max(1, Math.min(20, maxResults));
    }

    @Override
    public List<RestaurantPriceCandidate> search(String dishName, String unit) {
        if (dishName == null || dishName.isBlank()) {
            return List.of();
        }
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("engine", "google")
                    .queryParam("q", buildQuery(dishName, unit))
                    .queryParam("location", "Seoul, South Korea")
                    .queryParam("google_domain", "google.co.kr")
                    .queryParam("gl", "kr")
                    .queryParam("hl", "ko")
                    .queryParam("api_key", apiKey)
                    .build()
                    .encode()
                    .toUri();
            return parseCandidates(restTemplate.getForObject(uri, JsonNode.class), dishName);
        } catch (RestClientException exception) {
            log.warn("음식점 가격 검색에 실패했습니다: {}", exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<RestaurantPriceCandidate> parseCandidates(JsonNode response, String dishName) {
        if (response == null || !response.path("organic_results").isArray()) {
            return List.of();
        }
        String normalizedDishName = normalize(dishName);
        List<RestaurantPriceCandidate> candidates = new ArrayList<>();
        for (JsonNode result : response.path("organic_results")) {
            if (candidates.size() >= maxResults) {
                break;
            }
            String title = text(result, "title");
            String snippet = text(result, "snippet");
            String searchableText = title + " " + snippet + " " + extensionText(result);
            String sourceUrl = text(result, "link");
            if (!normalize(searchableText).contains(normalizedDishName)
                    || !sourceUrl.startsWith("http")) {
                continue;
            }
            Matcher matcher = WON_PRICE.matcher(searchableText);
            while (matcher.find() && candidates.size() < maxResults) {
                int price = parsePrice(matcher.group(1));
                if (price < MIN_PRICE || price > MAX_PRICE) {
                    continue;
                }
                String source = text(result, "source");
                if (source.isBlank()) {
                    source = text(result, "displayed_link");
                }
                candidates.add(new RestaurantPriceCandidate(
                        title, price, source.isBlank() ? "Google 검색" : source, sourceUrl));
            }
        }
        return List.copyOf(candidates);
    }

    private String buildQuery(String dishName, String unit) {
        String normalizedUnit = unit == null ? "" : unit.trim();
        return (dishName.trim() + " " + normalizedUnit + " 음식점 메뉴 가격").trim();
    }

    private String extensionText(JsonNode result) {
        StringBuilder text = new StringBuilder();
        for (String position : List.of("top", "bottom")) {
            JsonNode extensions = result.path("rich_snippet").path(position).path("extensions");
            if (extensions.isArray()) {
                extensions.forEach(value -> text.append(' ').append(value.asText("")));
            }
        }
        return text.toString();
    }

    private int parsePrice(String value) {
        try {
            return Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-z가-힣]", "");
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }
}
