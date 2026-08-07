package com.wallo.asset.client;

import com.wallo.chat.client.AiServerException;
import java.net.URI;
import java.util.logging.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class PythonAssetReportAiClient implements AssetReportAiClient {

    private static final String DEFAULT_AI_SERVER_URL = "http://127.0.0.1:8000";
    private static final int MAX_REPORT_TITLE_LENGTH = 15;
    private static final int MAX_REPORT_CONTENT_LENGTH = 50;
    private static final String CONSUMPTION_INSIGHT_PATH =
            "/api/asset-reports/insights/generate";
    private static final Logger LOGGER = Logger.getLogger(
            PythonAssetReportAiClient.class.getName()
    );

    private final RestTemplate restTemplate;
    private final URI consumptionInsightUri;

    public PythonAssetReportAiClient() {
        this(createRestTemplate(), resolveServerUrl());
    }

    PythonAssetReportAiClient(RestTemplate restTemplate, String serverUrl) {
        this.restTemplate = restTemplate;
        this.consumptionInsightUri = URI.create(
                removeTrailingSlash(serverUrl) + CONSUMPTION_INSIGHT_PATH
        );
    }

    @Override
    public AssetReportAiDto.Response generate(AssetReportAiDto.Request request) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<AssetReportAiDto.Response> response = restTemplate.postForEntity(
                    consumptionInsightUri,
                    request,
                    AssetReportAiDto.Response.class
            );

            AssetReportAiDto.Response body = response == null ? null : response.getBody();
            body = validateResponse(body);
            LOGGER.info("ai-consumption-insight request completed durationMs="
                    + elapsedMillis(startedAt));
            return body;
        } catch (HttpStatusCodeException exception) {
            LOGGER.warning("ai-consumption-insight request failed status="
                    + exception.getRawStatusCode()
                    + " durationMs=" + elapsedMillis(startedAt));
            throw new AiServerException(
                    "AI consumption insight request failed. Status: "
                            + exception.getRawStatusCode()
                            + ", response: " + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            LOGGER.warning("ai-consumption-insight request unavailable durationMs="
                    + elapsedMillis(startedAt));
            throw new AiServerException(
                    "Unable to connect to the AI consumption insight server.",
                    exception
            );
        } catch (RestClientException exception) {
            LOGGER.warning("ai-consumption-insight response processing failed durationMs="
                    + elapsedMillis(startedAt));
            throw new AiServerException(
                    "Failed to process the AI consumption insight response.",
                    exception
            );
        }
    }

    private AssetReportAiDto.Response validateResponse(AssetReportAiDto.Response response) {
        if (response == null
                || response.reportTitle() == null
                || response.reportContent() == null
                || response.reportTitle().isBlank()
                || response.reportContent().isBlank()) {
            throw new AiServerException(
                    "AI consumption insight response is empty or invalid."
            );
        }

        String reportTitle = response.reportTitle().trim();
        String reportContent = response.reportContent().trim();
        if (isInvalidText(reportTitle, MAX_REPORT_TITLE_LENGTH)
                || isInvalidText(reportContent, MAX_REPORT_CONTENT_LENGTH)) {
            throw new AiServerException(
                    "AI consumption insight response does not satisfy the text contract."
            );
        }

        return new AssetReportAiDto.Response(reportTitle, reportContent);
    }

    private boolean isInvalidText(String value, int maxLength) {
        return value.isBlank()
                || value.codePointCount(0, value.length()) > maxLength
                || value.contains("```")
                || isJsonWrappedText(value);
    }

    private boolean isJsonWrappedText(String value) {
        return value.startsWith("{") && value.endsWith("}");
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(30_000);
        return new RestTemplate(requestFactory);
    }

    private static String resolveServerUrl() {
        return System.getenv().getOrDefault("AI_SERVER_URL", DEFAULT_AI_SERVER_URL);
    }

    private static String removeTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_AI_SERVER_URL;
        }
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
