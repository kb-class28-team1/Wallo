package com.wallo.feed.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wallo.chat.client.AiServerException;
import com.wallo.feed.dto.FeedDtos.AnalysisResponse;
import com.wallo.feed.dto.FeedDtos.DetectedItem;
import com.wallo.feed.dto.FeedDtos.SavingAmountFeedbackSummary;
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

/**
 * Gemini generateContent REST API를 이용한 피드 미디어 분석 클라이언트.
 *
 * <p>작은 이미지/영상의 첫 연결을 위한 구현이며, 파일 자체를 Base64 inline data로 전송한다.
 * API 키는 생성자에서만 받고 로그나 응답에 노출하지 않는다.</p>
 */
public class GeminiFeedAnalysisClient implements FeedAnalysisClient, SavingFeedbackAwareFeedAnalysisClient {
    private static final String API_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String DEFAULT_SUMMARY = "Gemini가 업로드한 미디어를 분석했습니다.";

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
        return analyze(media, spendingType, category, null);
    }

    @Override
    public AnalysisResponse analyze(
            MultipartFile media, String spendingType, String category,
            SavingAmountFeedbackSummary feedbackSummary) {
        try {
            ObjectNode request = buildRequest(media, spendingType, category, feedbackSummary);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String endpoint = API_ENDPOINT.formatted(model, apiKey);
            JsonNode response = restTemplate.postForObject(
                    endpoint, new HttpEntity<>(request, headers), JsonNode.class);
            return toAnalysisResponse(response, spendingType, category);
        } catch (RestClientResponseException exception) {
            throw new AiServerException(
                    "Gemini 분석 API 요청이 실패했습니다. HTTP " + exception.getRawStatusCode(),
                    exception);
        } catch (RestClientException | IOException exception) {
            throw new AiServerException("Gemini 미디어 분석에 실패했습니다.", exception);
        }
    }

    private ObjectNode buildRequest(
            MultipartFile media, String spendingType, String category,
            SavingAmountFeedbackSummary feedbackSummary) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        ArrayNode contents = request.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

        parts.addObject().put("text", buildPrompt(spendingType, category, feedbackSummary));
        ObjectNode mediaPart = parts.addObject();
        ObjectNode inlineData = mediaPart.putObject("inline_data");
        inlineData.put("mime_type", resolveMimeType(media));
        inlineData.put("data", Base64.getEncoder().encodeToString(media.getBytes()));

        ObjectNode generationConfig = request.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        return request;
    }

    private String buildPrompt(
            String spendingType, String category,
            SavingAmountFeedbackSummary feedbackSummary) {
        return """
                당신은 절약 챌린지의 인증 사진 또는 영상을 분석하는 AI입니다.
                업로드한 미디어에서 사용자가 실천한 절약 행동의 근거를 간단히 확인하고,
                아래 형식의 JSON만 반환하세요. Markdown 코드 블록이나 추가 설명은 금지합니다.

                spendingType은 반드시 "%s", category는 반드시 "%s"를 그대로 사용하세요.
                estimatedSavingAmount는 관찰 가능한 가격, 수량, 횟수 또는 비교 근거를 바탕으로
                과도하게 추정하지 않은 예상 절약 금액을 원 단위 정수로 작성하세요.
                계산 결과가 1,000원 단위로 딱 떨어지지 않더라도 임의로 반올림하지 말고
                2,350원처럼 계산된 금액을 그대로 반영하세요.
                근거가 부족하거나 절약 행동을 확인할 수 없으면 보수적으로 0원에 가깝게 작성하세요.
                spendingType이 SPENT이면 0으로 작성하세요.
                confidenceScore는 근거가 명확할수록 1에 가까운 0부터 1 사이 숫자로 작성하세요.
                actualCost는 영상·사진의 영수증, 가격표 또는 음성에서 실제 지출 금액이 명확할 때만
                원 단위 정수로 작성하고 확인할 수 없으면 0으로 작성하세요.
                detectedItems에는 현재 카테고리의 절약 금액 계산에 직접 필요한 물품만 최대 3개 작성하세요.
                물품명, 브랜드, 한 묶음의 규격과 묶음 수량을 화면에서 확인할 수 있는 범위에서 작성하세요.
                unit은 가격 비교 기준이 되는 한 묶음 규격입니다. 예: 1L×1개, 500ml×60병, 120g×5봉.
                직접 요리하거나 음료·디저트를 만든 장면이 명확하면 comparisonType을 HOMEMADE로,
                완제품을 구매하거나 사용한 장면이면 PRODUCT로 작성하세요.
                채집·재배·수확·낚시 결과를 촬영한 사진이나 영상이면 대상 물품은 사용자가 직접 얻은 것으로
                간주하고 comparisonType을 GATHERED로 작성하며 actualCost는 0으로 작성하세요.
                이때 바구니, 삽, 낚싯대 같은 주변 도구는 detectedItems에서 제외하고 얻은 물품만 작성하세요.
                GATHERED 물품은 무게를 임의로 추정하지 말고 화면에서 확인되는 개체 수를 보수적으로 세세요.
                고구마·감자·조개·성게 같은 물품은 unit을 1개, 물고기·동물은 1마리,
                꽃이나 과일 한 덩이는 1송이처럼 개체에 맞는 단위를 사용하세요.
                quantity에는 화면에서 확인한 실제 개체 수를 정수로 작성하고 kg·g 같은 무게 단위는 사용하지 마세요.
                HOMEMADE일 때 ingredientCostPerUnit은 일반적인 소량 구매 재료비를 사용한 1단위 예상 원가,
                restaurantPricePerUnit은 음식점·카페의 보수적인 1단위 판매가를 원 단위 정수로 작성하세요.
                ingredientBasis에는 사진·영상에 실제로 보이는 재료, 양, 토핑, 소스와 조리 형태를 우선해
                핵심 재료와 원가 산정 근거를 짧게 작성하세요. 정형화된 레시피를 그대로 가정하지 마세요.
                PRODUCT 또는 GATHERED일 때 재료 관련 세 값은 각각 0, 0, 빈 문자열로 작성하세요.
                PRODUCT와 GATHERED의 시장 가격은 직접 추정하지 마세요.
                상품 시세 검색과 최종 계산은 서버가 수행합니다.

                {
                  "spendingType": "%s",
                  "category": "%s",
                  "estimatedSavingAmount": 0,
                  "summary": "절약 행동에 대한 짧은 한국어 설명",
                  "confidenceScore": 0.0,
                  "actualCost": 0,
                  "detectedItems": [
                    {
                      "itemName": "상품 종류 또는 상품명",
                      "brand": "확인되지 않으면 빈 문자열",
                      "unit": "한 묶음 규격",
                      "quantity": 1,
                      "confidence": 0.0,
                      "evidence": "화면에서 확인한 짧은 근거",
                      "comparisonType": "PRODUCT, HOMEMADE 또는 GATHERED",
                      "ingredientCostPerUnit": 0,
                      "restaurantPricePerUnit": 0,
                      "ingredientBasis": "HOMEMADE일 때 보이는 핵심 재료와 산정 근거"
                    }
                  ]
                }
                분석 범위는 위에서 지정한 category 한 가지만으로 제한합니다.
                카테고리 코드: FOOD(식비), CAFE(카페), TRANSPORT(교통/차량), SHOPPING(쇼핑),
                DELIVERY(배달), HOUSING(주거/통신), LIVING(생활), CULTURE(문화), HEALTH(건강), ETC(기타).
                지정된 category의 절약 행동과 근거만 분석하고 다른 카테고리의 물품·행동은 무시하세요.
                과거 사용자 검증 요약: %s
                """.formatted(
                spendingType, category, spendingType, category,
                feedbackPrompt(feedbackSummary));
    }

    private String feedbackPrompt(SavingAmountFeedbackSummary feedbackSummary) {
        if (feedbackSummary == null || feedbackSummary.getTotalCount() < 3) {
            return "검증 데이터가 부족하므로 금액을 임의로 보정하지 마세요.";
        }
        return "최근 검증 " + feedbackSummary.getTotalCount() + "건 중 같음 "
                + feedbackSummary.getSameCount() + "건, 다름 "
                + feedbackSummary.getDifferentCount() + "건입니다. "
                + "실제 금액-현재 AI 금액의 평균 차이는 "
                + feedbackSummary.getAverageDifference() + "원입니다. "
                + "이 값을 참고하되 영상 근거가 우선입니다.";
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
        long actualCost = Math.max(0, result.path("actualCost").asLong(0));
        List<DetectedItem> detectedItems = parseDetectedItems(result.path("detectedItems"));
        String summary = result.path("summary").asText(DEFAULT_SUMMARY).trim();
        if (summary.isBlank()) {
            summary = DEFAULT_SUMMARY;
        }

        // 카테고리/소비 종류는 사용자가 선택하고 서버가 검증한 값을 신뢰한다.
        return new AnalysisResponse(
                spendingType, category, amount, summary, confidence,
                detectedItems, 0, actualCost, 0, List.of());
    }

    private List<DetectedItem> parseDetectedItems(JsonNode itemsNode) {
        if (!itemsNode.isArray()) {
            return List.of();
        }
        List<DetectedItem> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            if (items.size() >= 3) {
                break;
            }
            String itemName = item.path("itemName").asText("").trim();
            if (itemName.isBlank()) {
                continue;
            }
            String brand = item.path("brand").asText("").trim();
            String unit = item.path("unit").asText("개").trim();
            if (unit.isBlank()) {
                unit = "개";
            }
            int quantity = Math.max(1, Math.min(99, item.path("quantity").asInt(1)));
            double confidence = item.path("confidence").asDouble(0);
            confidence = Math.max(0, Math.min(1, confidence));
            items.add(new DetectedItem(
                    itemName,
                    brand,
                    unit,
                    quantity,
                    0,
                    0,
                    confidence,
                    item.path("evidence").asText("").trim(),
                    item.path("comparisonType").asText("PRODUCT").trim(),
                    Math.max(0, item.path("ingredientCostPerUnit").asInt(0)),
                    Math.max(0, item.path("restaurantPricePerUnit").asInt(0)),
                    item.path("ingredientBasis").asText("").trim()));
        }
        return List.copyOf(items);
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
