package com.wallo.external.client;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.dto.CodefDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CodefMockClient implements CodefClient {

    private static final String BANK = "BANK";
    private static final String CARD = "CARD";
    private static final String STOCK = "STOCK";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final CodefAuthorizedRequestFactory requestFactory;

    public CodefMockClient(
            RestTemplate restTemplate,
            @Value("${codef.mock-api.base-url}") String baseUrl,
            CodefAuthorizedRequestFactory requestFactory
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.requestFactory = requestFactory;
    }

    @Override
    public CodefDto.Response connectInstitution(CodefDto.Request request) {
        try {
            return restTemplate.exchange(
                    baseUrl + resolvePath(request.getInstitutionType()),
                    HttpMethod.POST,
                    requestFactory.create(request),
                    CodefDto.Response.class
            ).getBody();
        } catch (RestClientException exception) {
            return CodefDto.Response.failure("CF-99999", "Mock API 호출 실패", exception.getMessage());
        }
    }

    private String resolvePath(String institutionType) {
        if (BANK.equals(institutionType)) {
            return "/mock/v1/kr/bank/p/account/account-list";
        }

        if (CARD.equals(institutionType)) {
            return "/mock/v1/kr/card/p/account/card-list";
        }

        if (STOCK.equals(institutionType)) {
            return "/mock/v1/kr/stock/p/account/account-list";
        }

        return "/mock/v1/kr/unsupported";
    }
}
