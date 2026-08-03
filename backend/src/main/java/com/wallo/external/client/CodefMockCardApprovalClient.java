package com.wallo.external.client;

import com.wallo.external.dto.CodefDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CodefMockCardApprovalClient implements CardApprovalClient {

    private static final String CARD_APPROVAL_PATH = "/mock/v1/kr/card/p/approval-list";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CodefMockCardApprovalClient(
            RestTemplate restTemplate,
            @Value("${codef.mock-api.base-url}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public CodefDto.Response getApprovals(CodefDto.CardApprovalRequest request) {
        try {
            return restTemplate.postForObject(
                    baseUrl + CARD_APPROVAL_PATH,
                    request,
                    CodefDto.Response.class
            );
        } catch (RestClientException exception) {
            return CodefDto.Response.failure(
                    "CF-99999",
                    "카드 승인내역 Mock API 호출 실패",
                    exception.getMessage()
            );
        }
    }
}
