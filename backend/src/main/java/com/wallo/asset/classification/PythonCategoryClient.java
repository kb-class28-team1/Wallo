package com.wallo.asset.classification;

import com.wallo.chat.client.AiServerException;
import java.math.BigDecimal;
import java.net.URI;
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

    private final RestTemplate restTemplate;
    private final URI categoryUri;

    public PythonCategoryClient() {
        this(createRestTemplate(), resolveServerUrl());
    }

    PythonCategoryClient(RestTemplate restTemplate, String serverUrl) {
        this.restTemplate = restTemplate;
        this.categoryUri = URI.create(removeTrailingSlash(serverUrl) + CATEGORY_PATH);
    }

    @Override
    public CategoryClassificationDto.Response classify(CategoryClassificationDto.Request request) {
        try {
            ResponseEntity<CategoryClassificationDto.Response> response = restTemplate.postForEntity(
                    categoryUri,
                    request,
                    CategoryClassificationDto.Response.class
            );

            CategoryClassificationDto.Response body = response == null ? null : response.getBody();
            validateResponse(body);
            return body;
        } catch (HttpStatusCodeException exception) {
            throw new AiServerException(
                    "AI category request failed. Status: "
                            + exception.getRawStatusCode()
                            + ", response: "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new AiServerException("Unable to connect to the AI category server.", exception);
        } catch (RestClientException exception) {
            throw new AiServerException("Failed to process the AI category response.", exception);
        }
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
