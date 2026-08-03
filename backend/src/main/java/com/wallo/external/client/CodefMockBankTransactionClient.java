package com.wallo.external.client;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.dto.CodefDto;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CodefMockBankTransactionClient implements BankTransactionClient {

    private static final String BANK_TRANSACTION_PATH =
            "/mock/v1/kr/bank/p/account/transaction-list";

    private final RestTemplate restTemplate;
    private final CodefMockApiUrlProvider urlProvider;
    private final CodefAuthorizedRequestFactory requestFactory;

    public CodefMockBankTransactionClient(
            RestTemplate restTemplate,
            CodefMockApiUrlProvider urlProvider,
            CodefAuthorizedRequestFactory requestFactory
    ) {
        this.restTemplate = restTemplate;
        this.urlProvider = urlProvider;
        this.requestFactory = requestFactory;
    }

    @Override
    public CodefDto.Response getTransactions(CodefDto.BankTransactionRequest request) {
        try {
            return restTemplate.exchange(
                    urlProvider.resolve(BANK_TRANSACTION_PATH),
                    HttpMethod.POST,
                    requestFactory.create(request),
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
