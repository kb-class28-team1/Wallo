package com.wallo.external.client;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.dto.CodefDto;
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
    private final CodefMockApiUrlProvider urlProvider;
    private final CodefAuthorizedRequestFactory requestFactory;

    public CodefMockClient(
            RestTemplate restTemplate,
            CodefMockApiUrlProvider urlProvider,
            CodefAuthorizedRequestFactory requestFactory
    ) {
        this.restTemplate = restTemplate;
        this.urlProvider = urlProvider;
        this.requestFactory = requestFactory;
    }

    @Override
    public CodefDto.Response connectInstitution(CodefDto.Request request) {
        try {
            return restTemplate.exchange(
                    urlProvider.resolve(resolvePath(request.getInstitutionType())),
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
