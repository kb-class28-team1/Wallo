package com.wallo.chat.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import com.wallo.chat.dto.SummarizeConversationRequest;
import com.wallo.chat.dto.SummarizeConversationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.logging.Logger;

@Component
public class PythonAiClient {
    private static final Logger LOGGER = Logger.getLogger(PythonAiClient.class.getName());
    private static final String DEFAULT_AI_SERVER_URL = "http://127.0.0.1:8000";

    private final RestTemplate restTemplate;
    private final URI chatUri;
    private final URI summarizeUri;

    @Autowired
    public PythonAiClient(ObjectMapper objectMapper) {
        this(createRestTemplate(objectMapper), System.getenv()
                .getOrDefault("AI_SERVER_URL", DEFAULT_AI_SERVER_URL));
    }

    PythonAiClient(RestTemplate restTemplate, String serverUrl) {
        this.restTemplate = restTemplate;
        this.chatUri = URI.create(removeTrailingSlash(serverUrl) + "/api/chat");
        this.summarizeUri = URI.create(removeTrailingSlash(serverUrl) + "/api/chat/summarize");
    }

    static RestTemplate createRestTemplate(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(90_000);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> converter.setObjectMapper(objectMapper));
        return restTemplate;
    }

    public SummarizeConversationResponse summarize(SummarizeConversationRequest request) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<SummarizeConversationResponse> response = restTemplate.postForEntity(
                    summarizeUri, request, SummarizeConversationResponse.class);
            if (response.getBody() == null
                    || response.getBody().summary() == null
                    || response.getBody().summary().isBlank()) {
                throw new AiServerException("AI 서버의 대화 요약 응답이 비어 있습니다.");
            }
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            if (exception.getRawStatusCode() == 429) {
                throw rateLimitException(exception);
            }
            throw new AiServerException(
                    "AI 서버가 대화 요약에 실패했습니다. 상태 코드: "
                            + exception.getRawStatusCode(), exception);
        } catch (ResourceAccessException exception) {
            throw new AiServerException("AI 서버에 연결할 수 없습니다.", exception);
        } catch (RestClientException exception) {
            throw new AiServerException("AI 서버의 대화 요약 응답 처리에 실패했습니다.", exception);
        } finally {
            LOGGER.info(String.format(
                    "[WALLO_TIMING] ai.summarize elapsedMs=%d",
                    elapsedMillis(startedAt)
            ));
        }
    }

    public ChatResponse chat(ChatRequest chatRequest) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
                    chatUri,
                    chatRequest,
                    ChatResponse.class
            );

            if (response.getBody() == null) {
                throw new AiServerException("AI 서버 응답이 비어 있습니다.");
            }
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            if (exception.getRawStatusCode() == 429) {
                throw rateLimitException(exception);
            }
            throw new AiServerException(
                    "AI 서버가 요청 처리에 실패했습니다. 상태 코드: "
                            + exception.getRawStatusCode()
                            + ", 응답: "
                            + exception.getResponseBodyAsString(),
                    exception
            );
        } catch (ResourceAccessException exception) {
            throw new AiServerException("AI 서버에 연결할 수 없습니다.", exception);
        } catch (RestClientException exception) {
            throw new AiServerException("AI 서버 응답 처리에 실패했습니다.", exception);
        } finally {
            LOGGER.info(String.format(
                    "[WALLO_TIMING] ai.chat elapsedMs=%d",
                    elapsedMillis(startedAt)
            ));
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String removeTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }

    private AiRateLimitException rateLimitException(
            HttpStatusCodeException exception
    ) {
        return new AiRateLimitException(
                "현재 AI 사용량 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
                exception
        );
    }
}
