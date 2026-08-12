package com.wallo.mission.client;

import com.wallo.chat.client.AiServerException;
import com.wallo.chat.client.AiRateLimitException;
import com.wallo.mission.dto.MissionGenerationDto;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class PythonMissionAiClient implements MissionAiClient {
    private final RestTemplate restTemplate;
    private final URI generationUri;

    public PythonMissionAiClient(
            RestTemplate restTemplate,
            @Value("${ai.server.url:http://127.0.0.1:8000}") String serverUrl) {
        this.restTemplate = restTemplate;
        String normalized = serverUrl.endsWith("/")
                ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.generationUri = URI.create(normalized + "/api/missions/generate");
    }

    @Override
    public MissionGenerationDto.Response generate(MissionGenerationDto.Request request) {
        try {
            ResponseEntity<MissionGenerationDto.Response> response = restTemplate.postForEntity(
                    generationUri, request, MissionGenerationDto.Response.class);
            if (response == null || response.getBody() == null) {
                throw new AiServerException("AI mission response is empty.");
            }
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            if (exception.getRawStatusCode() == 429) {
                throw new AiRateLimitException(
                        "AI 미션 생성 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.",
                        exception);
            }
            throw new AiServerException(
                    "AI mission server returned status " + exception.getRawStatusCode() + ".",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new AiServerException("Unable to connect to the AI mission server.", exception);
        } catch (RestClientException exception) {
            throw new AiServerException("Failed to process the AI mission response.", exception);
        }
    }
}
