package com.wallo.feed.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wallo.chat.client.AiServerException;
import com.wallo.feed.dto.FeedDtos.AnalysisFeedbackSummary;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Gemini의 영상 이해 결과를 근거 중심의 구조화된 분석으로 변환한다. */
public class GeminiFeedAnalysisClient implements FeedAnalysisClient, ContextAwareFeedAnalysisClient {
    private static final String API_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final int MAX_ATTEMPTS = 2;
    private static final String DEFAULT_SUMMARY = "영상에서 절약 행동의 근거를 충분히 확인하지 못했습니다.";
    private static final String[] FEED_CATEGORIES = {
        "FOOD", "CAFE", "TRANSPORT", "SHOPPING", "DELIVERY",
        "HOUSING", "LIVING", "CULTURE", "HEALTH", "ETC"
    };

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String thinkingLevel;
    private final String mediaResolution;

    public GeminiFeedAnalysisClient(
            RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String model) {
        this(restTemplate, objectMapper, apiKey, model, "high", "high");
    }

    public GeminiFeedAnalysisClient(
            RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String model,
            String thinkingLevel, String mediaResolution) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.thinkingLevel = normalizeOption(thinkingLevel, "high", "minimal", "low", "medium", "high");
        this.mediaResolution = normalizeOption(mediaResolution, "high", "low", "medium", "high");
    }

    @Override
    public AnalysisResponse analyze(MultipartFile media, String spendingType, String category) {
        return analyze(media, spendingType, category, null);
    }

    @Override
    public AnalysisResponse analyze(
            MultipartFile media, String spendingType, String category,
            AnalysisFeedbackSummary feedbackSummary) {
        try {
            ObjectNode request = buildRequest(media, spendingType, category, feedbackSummary);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode response = postWithRetry(
                    API_ENDPOINT.formatted(model, apiKey), new HttpEntity<>(request, headers));
            return toAnalysisResponse(response, spendingType, category);
        } catch (RestClientResponseException exception) {
            throw new AiServerException(
                    "Gemini 영상 분석 API 요청이 실패했습니다. HTTP " + exception.getRawStatusCode(), exception);
        } catch (RestClientException | IOException exception) {
            throw new AiServerException("Gemini 영상 분석에 실패했습니다.", exception);
        }
    }

    private ObjectNode buildRequest(
            MultipartFile media, String spendingType, String category,
            AnalysisFeedbackSummary feedbackSummary) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        ArrayNode contents = request.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        ObjectNode mediaPart = parts.addObject();
        ObjectNode inlineData = mediaPart.putObject("inline_data");
        inlineData.put("mime_type", resolveMimeType(media));
        inlineData.put("data", Base64.getEncoder().encodeToString(media.getBytes()));
        parts.addObject().put("text", buildPrompt(spendingType, category, feedbackSummary));

        ObjectNode generationConfig = request.putObject("generationConfig");
        generationConfig.putObject("thinkingConfig").put("thinkingLevel", thinkingLevel);
        // v1beta에서는 미디어별 설정 대신 요청 전체 설정으로 전달해야 한다.
        generationConfig.put("mediaResolution",
                "MEDIA_RESOLUTION_" + mediaResolution.toUpperCase(Locale.ROOT));
        ObjectNode textFormat = generationConfig.putObject("responseFormat").putObject("text");
        textFormat.put("mimeType", "application/json");
        textFormat.set("schema", analysisSchema());
        return request;
    }

    private String buildPrompt(
            String spendingType, String category, AnalysisFeedbackSummary feedbackSummary) {
        return """
                당신은 절약 챌린지의 영상 분석 담당자입니다.
                목표는 영상에 실제로 나타난 상황, 물품, 비용 근거를 추출하는 것입니다.

                입력값 spendingType은 "%s", category는 "%s"이며 이 값은 변경하지 않습니다.
                분석 절차:
                1. 영상 전체의 행동과 화면 글자, 영수증, 상품명, 수량, 음성을 확인합니다.
                2. 물품은 영상에서 명확히 식별되는 것만 detectedItems에 넣습니다.
                3. 실제 결제금액이나 비용이 화면·음성으로 확인될 때만 actualCost에 기록하고, 없으면 0입니다.
                4. 시세와 최종 차액은 서버의 시세표가 계산하므로 상식이나 막연한 시장가를 만들지 않습니다.
                5. 불확실하면 물품을 억지로 추가하지 말고 confidenceScore를 낮춥니다.
                6. summary는 영상에서 확인한 근거만 사용해 짧은 한국어로 작성합니다.

                현재 카테고리 분석 기준: %s
                최근 사용자 피드백: %s
                JSON만 반환하세요. Markdown이나 설명을 덧붙이지 마세요.
                """.formatted(spendingType, category, categoryGuidance(category), feedbackText(feedbackSummary));
    }

    private ObjectNode analysisSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("spendingType").put("type", "string")
                .set("enum", enumValues("SPENT", "REDUCED", "SAVED"));
        properties.putObject("category").put("type", "string")
                .set("enum", enumValues(FEED_CATEGORIES));
        properties.putObject("estimatedSavingAmount").put("type", "integer")
                .put("minimum", 0).put("maximum", 1_000_000);
        properties.putObject("summary").put("type", "string");
        properties.putObject("confidenceScore").put("type", "number")
                .put("minimum", 0).put("maximum", 1);
        properties.putObject("actualCost").put("type", "integer")
                .put("minimum", 0).put("maximum", 10_000_000);

        ObjectNode detectedItems = properties.putObject("detectedItems");
        detectedItems.put("type", "array");
        ObjectNode item = detectedItems.putObject("items");
        item.put("type", "object");
        ObjectNode itemProperties = item.putObject("properties");
        itemProperties.putObject("itemName").put("type", "string");
        itemProperties.putObject("brand").put("type", "string");
        itemProperties.putObject("unit").put("type", "string");
        itemProperties.putObject("quantity").put("type", "integer").put("minimum", 1).put("maximum", 999);
        itemProperties.putObject("confidence").put("type", "number").put("minimum", 0).put("maximum", 1);
        itemProperties.putObject("evidence").put("type", "string");
        item.set("required", enumValues("itemName", "brand", "unit", "quantity", "confidence", "evidence"));
        item.put("additionalProperties", false);

        schema.set("required", enumValues(
                "spendingType", "category", "estimatedSavingAmount", "summary",
                "confidenceScore", "actualCost", "detectedItems"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private ArrayNode enumValues(String... values) {
        ArrayNode result = objectMapper.createArrayNode();
        for (String value : values) result.add(value);
        return result;
    }

    private String categoryGuidance(String category) {
        return switch (category) {
            case "FOOD" -> "식재료, 조리, 식사 준비 행동과 식품 수량을 확인합니다.";
            case "CAFE" -> "카페 주문, 음료·디저트 상품명과 결제 장면을 확인합니다.";
            case "TRANSPORT" -> "이동수단, 승차·주유·교통 결제 장면을 확인합니다.";
            case "SHOPPING" -> "구매 물품, 상품명, 수량, 결제 장면을 확인합니다.";
            case "DELIVERY" -> "배달 주문·포장·배달비가 화면이나 음성에 있는지 확인합니다.";
            case "HOUSING" -> "주거 관련 행동과 실제 비용 근거를 확인합니다.";
            case "LIVING" -> "생활용품과 생활비 관련 장면을 확인합니다.";
            case "CULTURE" -> "문화·여가 이용과 티켓·이용료 근거를 확인합니다.";
            case "HEALTH" -> "의료·운동·건강 관련 이용과 비용 근거를 확인합니다.";
            default -> "다른 카테고리로 분류하기 어려운 절약 행동의 근거를 확인합니다.";
        };
    }

    private String feedbackText(AnalysisFeedbackSummary feedbackSummary) {
        if (feedbackSummary == null
                || feedbackSummary.highCount() + feedbackSummary.accurateCount()
                + feedbackSummary.lowCount() == 0) {
            return "저장된 피드백이 없습니다.";
        }
        return "높음 " + feedbackSummary.highCount() + "회, 정확 "
                + feedbackSummary.accurateCount() + "회, 낮음 "
                + feedbackSummary.lowCount() + "회입니다. 낮음 평가가 있었으면 영상 근거가 있는 물품만 보수적으로 추출합니다.";
    }

    private JsonNode postWithRetry(String endpoint, HttpEntity<ObjectNode> request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restTemplate.postForObject(endpoint, request, JsonNode.class);
            } catch (RestClientResponseException exception) {
                int status = exception.getRawStatusCode();
                if (attempt == MAX_ATTEMPTS || (status != 429 && status < 500)) throw exception;
                pauseBeforeRetry(attempt);
            } catch (RestClientException exception) {
                if (attempt == MAX_ATTEMPTS) throw exception;
                pauseBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("Gemini 영상 분석이 완료되지 않았습니다.");
    }

    private void pauseBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServerException("Gemini 분석 재시도가 중단되었습니다.", exception);
        }
    }

    private AnalysisResponse toAnalysisResponse(
            JsonNode response, String spendingType, String category) throws IOException {
        String generatedText = extractGeneratedText(response);
        JsonNode result;
        try {
            result = objectMapper.readTree(cleanJson(generatedText));
        } catch (IOException exception) {
            throw new AiServerException("Gemini 분석 결과를 읽지 못했습니다.", exception);
        }

        int amount = Math.max(0, Math.min(1_000_000,
                result.path("estimatedSavingAmount").asInt(0)));
        if ("SPENT".equals(spendingType)) amount = 0;
        double confidence = Math.max(0, Math.min(1, result.path("confidenceScore").asDouble(0)));
        String summary = result.path("summary").asText(DEFAULT_SUMMARY).trim();
        if (summary.isBlank()) summary = DEFAULT_SUMMARY;
        int actualCost = Math.max(0, Math.min(10_000_000, result.path("actualCost").asInt(0)));

        List<DetectedItem> items = new ArrayList<>();
        for (JsonNode item : result.path("detectedItems")) {
            String itemName = item.path("itemName").asText("").trim();
            if (itemName.isBlank()) continue;
            items.add(new DetectedItem(
                    itemName,
                    item.path("brand").asText("").trim(),
                    nonBlankOr(item.path("unit").asText(""), "개"),
                    Math.max(1, Math.min(999, item.path("quantity").asInt(1))),
                    0, 0,
                    Math.max(0, Math.min(1, item.path("confidence").asDouble(0))),
                    item.path("evidence").asText("").trim()));
        }
        return new AnalysisResponse(
                spendingType, category, amount, summary, confidence,
                items, 0, actualCost, 0, List.of());
    }

    private String extractGeneratedText(JsonNode response) {
        if (response == null || !response.has("candidates")) {
            throw new AiServerException("Gemini 응답에 분석 결과가 없습니다.");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (part.has("text")) text.append(part.path("text").asText());
            }
        }
        if (text.length() == 0) throw new AiServerException("Gemini 응답에 분석 텍스트가 없습니다.");
        return text.toString();
    }

    private String cleanJson(String value) {
        String cleaned = value.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        return start >= 0 && end > start ? cleaned.substring(start, end + 1) : cleaned;
    }

    private String resolveMimeType(MultipartFile media) {
        if (media.getContentType() != null && !media.getContentType().isBlank()) {
            return media.getContentType().toLowerCase(Locale.ROOT);
        }
        String filename = media.getOriginalFilename() == null
                ? "" : media.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".mp4")) return "video/mp4";
        if (filename.endsWith(".mov")) return "video/quicktime";
        if (filename.endsWith(".webm")) return "video/webm";
        if (filename.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    private String normalizeOption(String value, String fallback, String... allowed) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (String option : allowed) if (option.equals(normalized)) return normalized;
        return fallback;
    }

    private String nonBlankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
