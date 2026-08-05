package com.wallo.asset.classification;

import com.wallo.chat.client.AiServerException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class PythonCategoryClient implements CategoryClassificationClient {

    private static final String DEFAULT_AI_SERVER_URL = "http://127.0.0.1:8000";
    private static final String CATEGORY_PATH = "/api/category/classify";
    private static final String CATEGORY_BATCH_PATH = "/api/category/classify/batch";
    private static final Logger LOGGER = Logger.getLogger(PythonCategoryClient.class.getName());

    private final RestTemplate restTemplate;
    private final URI categoryUri;
    private final URI categoryBatchUri;

    public PythonCategoryClient() {
        this(createRestTemplate(), resolveServerUrl());
    }

    PythonCategoryClient(RestTemplate restTemplate, String serverUrl) {
        this.restTemplate = restTemplate;
        String normalizedServerUrl = removeTrailingSlash(serverUrl);
        this.categoryUri = URI.create(normalizedServerUrl + CATEGORY_PATH);
        this.categoryBatchUri = URI.create(normalizedServerUrl + CATEGORY_BATCH_PATH);
    }

    @Override
    public CategoryClassificationDto.Response classify(CategoryClassificationDto.Request request) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<CategoryClassificationDto.Response> response = restTemplate.postForEntity(
                    categoryUri,
                    request,
                    CategoryClassificationDto.Response.class
            );

            CategoryClassificationDto.Response body = response == null ? null : response.getBody();
            validateResponse(body);
            LOGGER.info("ai-category request completed durationMs=" + elapsedMillis(startedAt));
            return body;
        } catch (HttpStatusCodeException exception) {
            LOGGER.warning("ai-category request failed status=" + exception.getRawStatusCode()
                    + " durationMs=" + elapsedMillis(startedAt));
            throw new AiServerException(
                    "AI category request failed. Status: "
                            + exception.getRawStatusCode()
                            + ", response: "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            LOGGER.warning("ai-category request unavailable durationMs=" + elapsedMillis(startedAt));
            throw new AiServerException("Unable to connect to the AI category server.", exception);
        } catch (RestClientException exception) {
            LOGGER.warning("ai-category response processing failed durationMs=" + elapsedMillis(startedAt));
            throw new AiServerException("Failed to process the AI category response.", exception);
        }
    }

    @Override
    public List<CategoryClassificationDto.Response> classifyBatch(
            List<CategoryClassificationDto.Request> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        long startedAt = System.nanoTime();
        try {
            ResponseEntity<CategoryClassificationDto.BatchResponse> response = restTemplate.postForEntity(
                    categoryBatchUri,
                    new CategoryClassificationDto.BatchRequest(requests),
                    CategoryClassificationDto.BatchResponse.class
            );
            CategoryClassificationDto.BatchResponse body = response == null ? null : response.getBody();
            if (body == null || body.results() == null || body.results().size() != requests.size()) {
                throw new AiServerException("AI category batch response is empty or invalid.");
            }
            for (CategoryClassificationDto.Response result : body.results()) {
                validateResponse(result);
            }
            LOGGER.info("ai-category batch request completed count=" + requests.size()
                    + " durationMs=" + elapsedMillis(startedAt));
            return body.results();
        } catch (HttpStatusCodeException exception) {
            LOGGER.warning("ai-category batch request failed status=" + exception.getRawStatusCode()
                    + " durationMs=" + elapsedMillis(startedAt));
            throw new AiServerException(
                    "AI category batch request failed. Status: "
                            + exception.getRawStatusCode()
                            + ", response: "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            LOGGER.warning("ai-category batch request unavailable durationMs="
                    + elapsedMillis(startedAt));
            throw new AiServerException("Unable to connect to the AI category server.", exception);
        } catch (RestClientException exception) {
            LOGGER.warning("ai-category batch response processing failed durationMs="
                    + elapsedMillis(startedAt));
            throw new AiServerException("Failed to process the AI category batch response.", exception);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void validateResponse(CategoryClassificationDto.Response response) {
        if (response == null
                || response.category() == null
                || response.category().isBlank()
                || response.confidence() == null
                || response.confidence().compareTo(BigDecimal.ZERO) < 0
                || response.confidence().compareTo(BigDecimal.ONE) > 0) {
            throw new AiServerException("AI category response is empty or invalid.");
        }
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
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
