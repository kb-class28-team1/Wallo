package com.wallo.asset.classification;

import com.wallo.chat.client.AiServerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.function.LongConsumer;
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
    private static final int MAX_ATTEMPTS = 2;
    private static final long INITIAL_BACKOFF_MILLIS = 250L;
    private static final Logger LOGGER = Logger.getLogger(PythonCategoryClient.class.getName());

    private final RestTemplate restTemplate;
    private final URI categoryUri;
    private final URI categoryBatchUri;
    private final LongConsumer retrySleeper;
    private final ObjectMapper objectMapper;

    public PythonCategoryClient() {
        this(
                createRestTemplate(),
                resolveServerUrl(),
                PythonCategoryClient::sleepBeforeRetry,
                new ObjectMapper()
        );
    }

    PythonCategoryClient(RestTemplate restTemplate, String serverUrl) {
        this(restTemplate, serverUrl, PythonCategoryClient::sleepBeforeRetry, new ObjectMapper());
    }

    PythonCategoryClient(RestTemplate restTemplate, String serverUrl, LongConsumer retrySleeper) {
        this(restTemplate, serverUrl, retrySleeper, new ObjectMapper());
    }

    private PythonCategoryClient(
            RestTemplate restTemplate,
            String serverUrl,
            LongConsumer retrySleeper,
            ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        String normalizedServerUrl = removeTrailingSlash(serverUrl);
        this.categoryUri = URI.create(normalizedServerUrl + CATEGORY_PATH);
        this.categoryBatchUri = URI.create(normalizedServerUrl + CATEGORY_BATCH_PATH);
        this.retrySleeper = retrySleeper;
        this.objectMapper = objectMapper;
    }

    @Override
    public CategoryClassificationDto.Response classify(CategoryClassificationDto.Request request) {
        return executeWithRetry("category", () -> classifyOnce(request));
    }

    private CategoryClassificationDto.Response classifyOnce(CategoryClassificationDto.Request request) {
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
                    toFailureReason(exception),
                    isRetryable(exception),
                    exception.getRawStatusCode(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            LOGGER.warning("ai-category request unavailable durationMs=" + elapsedMillis(startedAt));
            throw toResourceAccessException(exception);
        } catch (RestClientException exception) {
            LOGGER.warning("ai-category response processing failed durationMs=" + elapsedMillis(startedAt));
            throw new AiServerException(
                    "Failed to process the AI category response.",
                    AiServerException.FailureReason.AI_INVALID_RESPONSE,
                    false,
                    null,
                    exception
            );
        }
    }

    @Override
    public List<CategoryClassificationDto.Response> classifyBatch(
            List<CategoryClassificationDto.Request> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }

        return executeWithRetry("category-batch", () -> classifyBatchOnce(requests));
    }

    private List<CategoryClassificationDto.Response> classifyBatchOnce(
            List<CategoryClassificationDto.Request> requests
    ) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<CategoryClassificationDto.BatchResponse> response = restTemplate.postForEntity(
                    categoryBatchUri,
                    new CategoryClassificationDto.BatchRequest(requests),
                    CategoryClassificationDto.BatchResponse.class
            );
            CategoryClassificationDto.BatchResponse body = response == null ? null : response.getBody();
            if (body == null || body.results() == null || body.results().size() != requests.size()) {
                throw new AiServerException(
                        "AI category batch response is empty or invalid.",
                        AiServerException.FailureReason.AI_INVALID_RESPONSE,
                        false,
                        null
                );
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
                    toFailureReason(exception),
                    isRetryable(exception),
                    exception.getRawStatusCode(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            LOGGER.warning("ai-category batch request unavailable durationMs="
                    + elapsedMillis(startedAt));
            throw toResourceAccessException(exception);
        } catch (RestClientException exception) {
            LOGGER.warning("ai-category batch response processing failed durationMs="
                    + elapsedMillis(startedAt));
            throw new AiServerException(
                    "Failed to process the AI category batch response.",
                    AiServerException.FailureReason.AI_INVALID_RESPONSE,
                    false,
                    null,
                    exception
            );
        }
    }

    private <T> T executeWithRetry(String operation, java.util.function.Supplier<T> operationCall) {
        AiServerException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return operationCall.get();
            } catch (AiServerException exception) {
                lastFailure = exception;
                if (!exception.isRetryable() || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }

                long backoffMillis = INITIAL_BACKOFF_MILLIS * (1L << (attempt - 1));
                LOGGER.warning(
                        "ai-category retry operation=" + operation
                                + " attempt=" + attempt + "/" + MAX_ATTEMPTS
                                + " reason=" + exception.getFailureReason()
                                + " backoffMs=" + backoffMillis
                );
                retrySleeper.accept(backoffMillis);
            }
        }
        throw lastFailure;
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
            throw new AiServerException(
                    "AI category response is empty or invalid.",
                    AiServerException.FailureReason.AI_INVALID_RESPONSE,
                    false,
                    null
            );
        }
    }

    private AiServerException toResourceAccessException(ResourceAccessException exception) {
        boolean timeout = hasCause(exception, SocketTimeoutException.class)
                || hasCauseNamed(exception, "ConnectTimeoutException")
                || hasCauseNamed(exception, "TimeoutException");
        AiServerException.FailureReason reason = timeout
                ? AiServerException.FailureReason.AI_TIMEOUT
                : AiServerException.FailureReason.AI_UNAVAILABLE;
        return new AiServerException(
                timeout
                        ? "The AI category server timed out."
                        : "Unable to connect to the AI category server.",
                reason,
                true,
                null,
                exception
        );
    }

    private AiServerException.FailureReason toFailureReason(HttpStatusCodeException exception) {
        ErrorContract errorContract = readErrorContract(exception.getResponseBodyAsString());
        if (errorContract != null && errorContract.failureReason() != null) {
            return errorContract.failureReason();
        }
        return reasonForStatus(exception.getRawStatusCode());
    }

    private boolean isRetryable(HttpStatusCodeException exception) {
        int statusCode = exception.getRawStatusCode();
        boolean transientStatus = statusCode == 502 || statusCode == 503 || statusCode == 504;
        if (!transientStatus) {
            return false;
        }

        ErrorContract errorContract = readErrorContract(exception.getResponseBodyAsString());
        if (errorContract != null && errorContract.retryable() != null) {
            return errorContract.retryable();
        }
        return true;
    }

    private ErrorContract readErrorContract(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode detail = root == null ? null : root.get("detail");
            JsonNode payload = detail != null && detail.isObject() ? detail : root;
            if (payload == null || !payload.isObject()) {
                return null;
            }

            String errorCode = payload.path("errorCode").asText(null);
            AiServerException.FailureReason failureReason = toFailureReason(errorCode);
            Boolean retryable = payload.has("retryable")
                    ? payload.get("retryable").asBoolean()
                    : null;
            if (failureReason == null && retryable == null) {
                return null;
            }
            return new ErrorContract(failureReason, retryable);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AiServerException.FailureReason toFailureReason(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return null;
        }
        try {
            return AiServerException.FailureReason.valueOf(errorCode);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private AiServerException.FailureReason reasonForStatus(int statusCode) {
        return switch (statusCode) {
            case 502 -> AiServerException.FailureReason.AI_UPSTREAM_ERROR;
            case 503 -> AiServerException.FailureReason.AI_UNAVAILABLE;
            case 504 -> AiServerException.FailureReason.AI_TIMEOUT;
            default -> statusCode >= 400 && statusCode < 500
                    ? AiServerException.FailureReason.AI_INVALID_REQUEST
                    : AiServerException.FailureReason.UNKNOWN;
        };
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCauseNamed(Throwable throwable, String simpleName) {
        Throwable current = throwable;
        while (current != null) {
            if (simpleName.equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void sleepBeforeRetry(long backoffMillis) {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiServerException(
                    "AI category retry was interrupted.",
                    AiServerException.FailureReason.AI_UNAVAILABLE,
                    false,
                    null,
                    exception
            );
        }
    }

    private record ErrorContract(
            AiServerException.FailureReason failureReason,
            Boolean retryable
    ) {
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
