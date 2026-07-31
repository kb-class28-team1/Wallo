package com.wallo.chat.client;

import com.wallo.chat.dto.ChatRequest;
import com.wallo.chat.dto.ChatResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Component
public class PythonAiClient {
    private static final String DEFAULT_AI_SERVER_URL = "http://127.0.0.1:8000";

    private final RestTemplate restTemplate;
    private final URI chatUri;

    public PythonAiClient() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(90_000);
        this.restTemplate = new RestTemplate(requestFactory);

        String serverUrl = System.getenv()
                .getOrDefault("AI_SERVER_URL", DEFAULT_AI_SERVER_URL);
        this.chatUri = URI.create(removeTrailingSlash(serverUrl) + "/api/chat");
    }

    public ChatResponse chat(ChatRequest chatRequest) {
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
        }
    }

    private String removeTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
