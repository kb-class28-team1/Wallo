package com.wallo.external.client;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.dto.CodefDto;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CodefMockCardApprovalClient implements CardApprovalClient {

    private static final String CARD_APPROVAL_PATH = "/mock/v1/kr/card/p/approval-list";

    private final RestTemplate restTemplate;
    private final CodefMockApiUrlProvider urlProvider;
    private final CodefAuthorizedRequestFactory requestFactory;

    public CodefMockCardApprovalClient(
            RestTemplate restTemplate,
            CodefMockApiUrlProvider urlProvider,
            CodefAuthorizedRequestFactory requestFactory
    ) {
        this.restTemplate = restTemplate;
        this.urlProvider = urlProvider;
        this.requestFactory = requestFactory;
    }

    @Override
    public CodefDto.Response getApprovals(CodefDto.CardApprovalRequest request) {
        try {
            return restTemplate.exchange(
                    urlProvider.resolve(CARD_APPROVAL_PATH),
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
