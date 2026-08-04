package com.wallo.external.client;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.dto.CodefDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CodefMockCardApprovalClient implements CardApprovalClient {

    private static final String CARD_APPROVAL_PATH = "/mock/v1/kr/card/p/approval-list";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final CodefAuthorizedRequestFactory requestFactory;

    public CodefMockCardApprovalClient(
            RestTemplate restTemplate,
            @Value("${codef.mock-api.base-url}") String baseUrl,
            CodefAuthorizedRequestFactory requestFactory
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.requestFactory = requestFactory;
    }

    @Override
    public CodefDto.Response getApprovals(CodefDto.CardApprovalRequest request) {
        try {
            return restTemplate.exchange(
                    baseUrl + CARD_APPROVAL_PATH,
                    HttpMethod.POST,
                    requestFactory.create(request),
                    CodefDto.Response.class
            ).getBody();
        } catch (RestClientException exception) {
            return CodefDto.Response.failure(
                    "CF-99999",
                    "카드 승인내역 Mock API 호출 실패",
                    exception.getMessage()
            );
        }
    }
}
