package com.wallo.feed.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wallo.chat.client.AiServerException;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gemini generateContent REST API를 이용한 피드 미디어 분석 클라이언트.
 *
 * <p>작은 이미지/영상의 첫 연결을 위한 구현이며, 파일 자체를 Base64 inline data로 전송한다.
 * API 키는 생성자에서만 받고 로그나 응답에 노출하지 않는다.</p>
 */
public class GeminiFeedAnalysisClient implements FeedAnalysisClient {
    private static final String API_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String DEFAULT_SUMMARY = "Gemini가 업로드한 미디어를 분석했습니다.";
    private static final int MAX_RETRIES = 1;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiFeedAnalysisClient(
            RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public AnalysisResponse analyze(
            MultipartFile media, String spendingType, String category) {
        try {
            ObjectNode request = buildRequest(media, spendingType, category);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String endpoint = API_ENDPOINT.formatted(model, apiKey);
            JsonNode response = requestWithRetry(endpoint, new HttpEntity<>(request, headers));
            return toAnalysisResponse(response, spendingType, category);
        } catch (RestClientResponseException exception) {
            throw new AiServerException(
                    "Gemini 분석 API 요청이 실패했습니다. HTTP " + exception.getRawStatusCode(),
                    exception);
        } catch (RestClientException | IOException exception) {
            throw new AiServerException("Gemini 미디어 분석에 실패했습니다.", exception);
        }
    }

    private JsonNode requestWithRetry(String endpoint, HttpEntity<ObjectNode> request) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restTemplate.postForObject(endpoint, request, JsonNode.class);
            } catch (RestClientResponseException exception) {
                if (attempt == MAX_RETRIES || !isRetryableStatus(exception.getRawStatusCode())) {
                    throw exception;
                }
            } catch (ResourceAccessException exception) {
                if (attempt == MAX_RETRIES) {
                    throw exception;
                }
            } catch (RestClientException exception) {
                throw exception;
            }
        }
        throw new IllegalStateException("Gemini 분석 요청 재시도에 실패했습니다.");
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 429
                || statusCode >= 500 && statusCode <= 599;
    }

    private ObjectNode buildRequest(
            MultipartFile media, String spendingType, String category) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        ArrayNode contents = request.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        parts.addObject().put("text", buildPrompt(spendingType, category));
        ObjectNode mediaPart = parts.addObject();
        ObjectNode inlineData = mediaPart.putObject("inline_data");
        inlineData.put("mime_type", resolveMimeType(media));
        inlineData.put("data", Base64.getEncoder().encodeToString(media.getBytes()));

        ObjectNode generationConfig = request.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        return request;
    }

    private String buildPrompt(String spendingType, String category) {
        return """
                <role>절약 인증 미디어 분석기</role>
                <input>spendingType=%s, category=%s, media=첨부된 사진 또는 영상</input>
                <task>
                1. 미디어에서 실제로 보이는 절약 행동을 확인한다.
                2. 보이는 가격·수량·횟수·비교 근거만 사용해 예상 절약액을 원 단위 정수로 계산한다.
                3. 근거가 없으면 0원으로 두고 confidenceScore를 낮춘다.
                4. spendingType이 SPENT이면 금액은 0이다.
                </task>
                <rules>
                추측, 평균 가격, 일반 상식으로 값을 만들지 않는다.
                spendingType과 category는 입력값을 그대로 반환한다.
                응답은 아래 JSON 하나만 반환한다. Markdown과 추가 문장은 금지한다.
                </rules>
                <output>
                {"spendingType":"%s","category":"%s","estimatedSavingAmount":0,
                 "summary":"짧은 한국어 근거 설명","confidenceScore":0.0}
                </output>
                """.formatted(spendingType, category, spendingType, category);
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

        int amount = Math.max(0, result.path("estimatedSavingAmount").asInt(0));
        if ("SPENT".equals(spendingType)) {
            amount = 0;
        }
        double confidence = result.path("confidenceScore").asDouble(0.0);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        String summary = result.path("summary").asText(DEFAULT_SUMMARY).trim();
        if (summary.isBlank()) {
            summary = DEFAULT_SUMMARY;
        }

        // 카테고리/소비 종류는 사용자가 선택하고 서버가 검증한 값을 신뢰한다.
        return new AnalysisResponse(spendingType, category, amount, summary, confidence);
    }

    private String extractGeneratedText(JsonNode response) {
        if (response == null || !response.has("candidates")) {
            throw new AiServerException("Gemini 응답에 분석 결과가 없습니다.");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                JsonNode textNode = part.get("text");
                if (textNode != null && !textNode.asText().isBlank()) {
                    text.append(textNode.asText());
                }
            }
        }
        if (text.length() == 0) {
            throw new AiServerException("Gemini 응답에 분석 텍스트가 없습니다.");
        }
        return text.toString();
    }

    private String cleanJson(String text) {
        String cleaned = text.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
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
}
