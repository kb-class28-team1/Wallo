package com.wallo.report.client;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import com.wallo.report.dto.ai.NewsReportAiRequest;
import com.wallo.report.dto.ai.NewsReportAiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * Python AI 서버의 금융 리포트 생성 엔드포인트(POST /api/reports/generate)를 호출한다.
 * PythonAiClient(채팅)와 요청/응답 구조와 책임이 달라 별도 클라이언트로 분리했다.
 * AI 서버 주소는 PythonAiClient와 동일하게 AI_SERVER_URL 환경변수를 사용한다
 * (프로젝트에 이 값을 application.properties로 관리하는 기존 규칙이 없어, 기존 환경변수 방식을 그대로 따랐다).
 */
@Component
public class PythonNewsReportAiClient implements NewsReportAiClient {

    private static final Logger log = LoggerFactory.getLogger(PythonNewsReportAiClient.class);

    private static final String DEFAULT_AI_SERVER_URL = "http://127.0.0.1:8000";

    private final RestTemplate restTemplate;
    private final URI generateReportUri;

    public PythonNewsReportAiClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(90_000);
        this.restTemplate = new RestTemplate(requestFactory);
        // 이 RestTemplate은 AppConfig/WebMvcConfig의 Jackson 설정과 무관하게 자체 ObjectMapper를 쓰기 때문에,
        // 기본값(WRITE_DATES_AS_TIMESTAMPS=true)을 그대로 두면 publishedAt이 "2026-07-31T09:00:00"이 아니라
        // [2026,7,31,9,0] 배열로 나가 Python(str 타입)이 422로 거부한다. 여기서도 동일하게 꺼준다.
        disableDateTimestamps(restTemplate);

        String serverUrl = System.getenv().getOrDefault("AI_SERVER_URL", DEFAULT_AI_SERVER_URL);
        this.generateReportUri = URI.create(removeTrailingSlash(serverUrl) + "/api/reports/generate");
    }

    private void disableDateTimestamps(RestTemplate restTemplate) {
        for (HttpMessageConverter<?> converter : restTemplate.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) converter).getObjectMapper()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
        }
    }

    @Override
    public NewsReportAiResponse generateReport(NewsReportAiRequest request) {
        try {
            ResponseEntity<NewsReportAiResponse> response =
                    restTemplate.postForEntity(generateReportUri, request, NewsReportAiResponse.class);

            if (response.getBody() == null) {
                log.warn("AI 서버 응답 본문이 비어 있습니다 - newsId: {}", request.newsId());
                throw new CustomException(ErrorCode.AI_REPORT_INVALID_RESPONSE);
            }
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            log.error("AI 서버가 오류 상태를 반환했습니다 - newsId: {}, status: {}",
                    request.newsId(), exception.getRawStatusCode(), exception);
            throw new CustomException(ErrorCode.AI_REPORT_GENERATION_FAILED, exception);
        } catch (ResourceAccessException exception) {
            log.error("AI 서버에 연결할 수 없습니다 - newsId: {}", request.newsId(), exception);
            throw new CustomException(ErrorCode.AI_REPORT_GENERATION_FAILED, exception);
        } catch (RestClientException exception) {
            log.error("AI 서버 응답 처리에 실패했습니다 - newsId: {}", request.newsId(), exception);
            throw new CustomException(ErrorCode.AI_REPORT_GENERATION_FAILED, exception);
        }
    }

    private String removeTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
