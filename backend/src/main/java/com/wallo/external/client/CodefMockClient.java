package com.wallo.external.client;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.dto.CodefDto;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class CodefMockClient implements CodefClient, BankTransactionClient, CardApprovalClient {

    private static final String BANK = "BANK";
    private static final String CARD = "CARD";
    private static final String STOCK = "STOCK";
    private static final String BANK_TRANSACTION_PATH =
            "/mock/v1/kr/bank/p/account/transaction-list";
    private static final String CARD_APPROVAL_PATH = "/mock/v1/kr/card/p/approval-list";

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
        return post(
                resolvePath(request.getInstitutionType()),
                request,
                "Mock API 호출 실패"
        );
    }

    @Override
    public CodefDto.Response getTransactions(CodefDto.BankTransactionRequest request) {
        return post(
                BANK_TRANSACTION_PATH,
                request,
                "은행 거래내역 Mock API 호출 실패"
        );
    }

    @Override
    public CodefDto.Response getApprovals(CodefDto.CardApprovalRequest request) {
        return post(
                CARD_APPROVAL_PATH,
                request,
                "카드 승인내역 Mock API 호출 실패"
        );
    }

    private <T> CodefDto.Response post(String path, T request, String failureMessage) {
        try {
            return restTemplate.exchange(
                    urlProvider.resolve(path),
                    HttpMethod.POST,
                    requestFactory.create(request),
                    CodefDto.Response.class
            ).getBody();
        } catch (RestClientException exception) {
            return CodefDto.Response.failure("CF-99999", failureMessage, exception.getMessage());
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
