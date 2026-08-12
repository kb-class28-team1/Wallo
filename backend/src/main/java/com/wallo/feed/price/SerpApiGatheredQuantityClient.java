package com.wallo.feed.price;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** 검색 결과 문장에서 판매 단위의 평균 개체 수를 추출한다. */
public class SerpApiGatheredQuantityClient implements GatheredQuantityClient {
    private static final Logger log = LoggerFactory.getLogger(
            SerpApiGatheredQuantityClient.class);
    private static final Pattern COUNT_RANGE = Pattern.compile(
            "(?i)(\\d+)\\s*(?:~|-|–|부터)\\s*(\\d+)\\s*(?:개|마리|미|송이|그루|알|입)");
    private static final Pattern COUNT_AFTER_PACKAGE = Pattern.compile(
            "(?i)(?:1\\s*(?:kg|킬로|박스|팩|봉|상자))[^0-9]{0,24}"
                    + "(\\d+)\\s*(?:개|마리|미|송이|그루|알|입)");
    private static final Pattern COUNT_PER_WEIGHT = Pattern.compile(
            "(?i)(?:kg|킬로)\\s*(?:당|에|기준)[^0-9]{0,24}"
                    + "(\\d+)\\s*(?:개|마리|미|송이|그루|알|입)");

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;
    private final int maxResults;

    public SerpApiGatheredQuantityClient(
            RestTemplate restTemplate, String baseUrl, String apiKey, int maxResults) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.maxResults = Math.max(1, Math.min(20, maxResults));
    }

    @Override
    public OptionalInt findAveragePackageQuantity(String itemName, String packageUnit) {
        String normalizedItemName = itemName == null ? "" : itemName.trim();
        if (normalizedItemName.isBlank()) {
            return OptionalInt.empty();
        }
        String query = (normalizedItemName + " "
                + (packageUnit == null ? "판매 단위" : packageUnit.trim())
                + " 평균 개수 몇 개 1kg당").trim();
        if (query.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("engine", "google")
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
            return parseAverageQuantity(response);
        } catch (RestClientException exception) {
            log.warn("채집물 평균 개수 검색에 실패했습니다: {}",
                    exception.getClass().getSimpleName());
            return OptionalInt.empty();
        }
    }

    private OptionalInt parseAverageQuantity(JsonNode response) {
        if (response == null) {
            return OptionalInt.empty();
        }
        List<Integer> quantities = new ArrayList<>();
        collectText(response.path("answer_box"), quantities);
        collectText(response.path("knowledge_graph"), quantities);
        JsonNode organicResults = response.path("organic_results");
        if (organicResults.isArray()) {
            int inspected = 0;
            for (JsonNode result : organicResults) {
                if (inspected++ >= maxResults) {
                    break;
                }
                collectText(result, quantities);
            }
        }
        if (quantities.isEmpty()) {
            return OptionalInt.empty();
        }
        Collections.sort(quantities);
        return OptionalInt.of(quantities.get((quantities.size() - 1) / 2));
    }

    private void collectText(JsonNode node, List<Integer> quantities) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addMatches(node.asText(), quantities);
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), quantities));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(value -> collectText(value, quantities));
        }
    }

    private void addMatches(String text, List<Integer> quantities) {
        Matcher rangeMatcher = COUNT_RANGE.matcher(text);
        while (rangeMatcher.find()) {
            int lower = Integer.parseInt(rangeMatcher.group(1));
            int upper = Integer.parseInt(rangeMatcher.group(2));
            quantities.add(Math.max(1, (int) Math.round((lower + upper) / 2.0)));
        }
        Matcher packageMatcher = COUNT_AFTER_PACKAGE.matcher(text);
        while (packageMatcher.find()) {
            quantities.add(Integer.parseInt(packageMatcher.group(1)));
        }
        Matcher weightMatcher = COUNT_PER_WEIGHT.matcher(text);
        while (weightMatcher.find()) {
            quantities.add(Integer.parseInt(weightMatcher.group(1)));
        }
    }
}
