package com.wallo.external.client;

import com.wallo.external.dto.CodefDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CodefMockBankTransactionClient implements BankTransactionClient {

    private static final String BANK_TRANSACTION_PATH =
            "/mock/v1/kr/bank/p/account/transaction-list";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String accessToken;

    public CodefMockBankTransactionClient(
            RestTemplate restTemplate,
            @Value("${codef.mock-api.base-url}") String baseUrl,
            @Value("${codef.mock-api.access-token}") String accessToken
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.accessToken = accessToken;
    }

    @Override
    public CodefDto.Response getTransactions(CodefDto.BankTransactionRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        try {
            return restTemplate.exchange(
                    baseUrl + BANK_TRANSACTION_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    CodefDto.Response.class
            ).getBody();
        } catch (RestClientException exception) {
            return CodefDto.Response.failure(
                    "CF-99999",
                    "은행 거래내역 Mock API 호출 실패",
                    exception.getMessage()
            );
        }
    }
}
