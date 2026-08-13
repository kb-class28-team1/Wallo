package com.wallo.mission.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wallo.chat.client.AiServerException;
import com.wallo.mission.dto.MissionVerificationDto;
import java.io.IOException;
import java.util.Base64;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class GeminiMissionVerificationClient implements MissionVerificationClient {
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiMissionVerificationClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                                           String apiKey, String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public MissionVerificationDto.AiResult verify(
            byte[] media, String contentType, MissionVerificationDto.MissionSpec mission) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            JsonNode response = restTemplate.postForObject(ENDPOINT.formatted(model, apiKey),
                    new HttpEntity<>(request(media, contentType, mission), headers), JsonNode.class);
            return parse(response);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AiServerException aiException) throw aiException;
            throw new AiServerException("Gemini 미션 인증 분석에 실패했습니다.", exception);
        }
    }

    private ObjectNode request(byte[] media, String contentType,
                               MissionVerificationDto.MissionSpec mission) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode parts = root.putArray("contents").addObject().putArray("parts");
        parts.addObject().put("text", prompt(mission));
        ObjectNode inline = parts.addObject().putObject("inline_data");
        inline.put("mime_type", contentType);
        inline.put("data", Base64.getEncoder().encodeToString(media));
        root.putObject("generationConfig").put("responseMimeType", "application/json");
        return root;
    }

    private String prompt(MissionVerificationDto.MissionSpec mission) {
        return """
                당신은 절약 미션 인증 사진/영상을 판정합니다. 미디어에서 직접 확인되는 근거만 사용하세요.
                미션 제목: %s
                미션 설명: %s
                촬영 안내: %s
                검증 규칙: %s
                달성이 명확하면 PASS, 명확히 불충족이면 FAIL, 판단 근거가 부족하면 REVIEW입니다.
                confidenceScore는 0~1, reason은 한국어 한두 문장으로 작성하세요.
                JSON만 반환: {"decision":"PASS|FAIL|REVIEW","confidenceScore":0.0,"reason":"..."}
                """.formatted(mission.title(), mission.description(),
                mission.evidenceGuide(), mission.verificationRuleJson());
    }

    private MissionVerificationDto.AiResult parse(JsonNode response) {
        try {
            String text = response.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText();
            JsonNode result = objectMapper.readTree(text);
            String decision = result.path("decision").asText();
            double confidence = result.path("confidenceScore").asDouble(-1);
            String reason = result.path("reason").asText();
            if (!java.util.Set.of("PASS", "FAIL", "REVIEW").contains(decision)
                    || confidence < 0 || confidence > 1 || reason.isBlank()) {
                throw new IllegalArgumentException("invalid mission verification response");
            }
            return new MissionVerificationDto.AiResult(decision, confidence, reason, model);
        } catch (Exception exception) {
            throw new AiServerException("Gemini 미션 판정 응답이 올바르지 않습니다.", exception);
        }
    }
}
