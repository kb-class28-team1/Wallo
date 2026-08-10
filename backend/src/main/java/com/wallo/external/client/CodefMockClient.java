package com.wallo.external.client;

import com.wallo.external.auth.CodefAuthorizedRequestFactory;
import com.wallo.external.auth.CodefPasswordEncryptor;
import com.wallo.external.auth.IdentityCodefPasswordEncryptor;
import com.wallo.external.dto.CodefDto;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Common CODEF adapter used by the local Mock API and CODEF Sandbox/production.
 */
public class CodefMockClient
        implements CodefClient, BankTransactionClient, CardApprovalClient, IncomeProofClient {

    private static final String DEFAULT_MOCK_PATH_PREFIX = "/mock/v1";
    private static final String BANK = "BANK";
    private static final String CARD = "CARD";
    private static final String STOCK = "STOCK";
    private static final String BANK_TRANSACTION_PATH = "/kr/bank/p/account/transaction-list";
    private static final String CARD_APPROVAL_PATH = "/kr/card/p/approval-list";
    private static final String INCOME_PROOF_PATH = "/kr/public/mw/issuance/proof-income";

    private final RestTemplate restTemplate;
    private final CodefMockApiUrlProvider urlProvider;
    private final CodefAuthorizedRequestFactory requestFactory;
    private final CodefPasswordEncryptor passwordEncryptor;
    private final String apiPathPrefix;

    /**
     * Keeps the original constructor behavior for the local Mock API.
     */
    public CodefMockClient(
            RestTemplate restTemplate,
            CodefMockApiUrlProvider urlProvider,
            CodefAuthorizedRequestFactory requestFactory
    ) {
        this(
                restTemplate,
                urlProvider,
                requestFactory,
                new IdentityCodefPasswordEncryptor(),
                DEFAULT_MOCK_PATH_PREFIX
        );
    }

    public CodefMockClient(
            RestTemplate restTemplate,
            CodefMockApiUrlProvider urlProvider,
            CodefAuthorizedRequestFactory requestFactory,
            CodefPasswordEncryptor passwordEncryptor,
            String apiPathPrefix
    ) {
        this.restTemplate = restTemplate;
        this.urlProvider = urlProvider;
        this.requestFactory = requestFactory;
        this.passwordEncryptor = passwordEncryptor;
        this.apiPathPrefix = normalizePathPrefix(apiPathPrefix);
    }

    @Override
    public CodefDto.Response connectInstitution(CodefDto.Request request) {
        return post(
                resolveAssetPath(request.getInstitutionType()),
                encrypt(request),
                "CODEF institution connection API call failed."
        );
    }

    @Override
    public CodefDto.Response getTransactions(CodefDto.BankTransactionRequest request) {
        return post(
                BANK_TRANSACTION_PATH,
                encrypt(request),
                "CODEF bank transaction API call failed."
        );
    }

    @Override
    public CodefDto.Response getApprovals(CodefDto.CardApprovalRequest request) {
        return post(
                CARD_APPROVAL_PATH,
                encrypt(request),
                "CODEF card approval API call failed."
        );
    }

    @Override
    public CodefDto.Response getIncomeProof(CodefDto.IncomeProofRequest request) {
        return post(
                INCOME_PROOF_PATH,
                encrypt(request),
                "CODEF income proof API call failed."
        );
    }

    private CodefDto.Request encrypt(CodefDto.Request request) {
        if (request == null) {
            return null;
        }
        return new CodefDto.Request(
                request.getOrganization(),
                request.getInstitutionType(),
                request.getLoginType(),
                request.getId(),
                passwordEncryptor.encrypt(request.getPassword())
        );
    }

    private CodefDto.BankTransactionRequest encrypt(CodefDto.BankTransactionRequest request) {
        if (request == null) {
            return null;
        }
        return new CodefDto.BankTransactionRequest(
                request.getOrganization(),
                request.getLoginType(),
                request.getId(),
                passwordEncryptor.encrypt(request.getPassword()),
                request.getAccount(),
                request.getStartDate(),
                request.getEndDate()
        );
    }

    private CodefDto.CardApprovalRequest encrypt(CodefDto.CardApprovalRequest request) {
        if (request == null) {
            return null;
        }
        return new CodefDto.CardApprovalRequest(
                request.getOrganization(),
                request.getLoginType(),
                request.getId(),
                passwordEncryptor.encrypt(request.getPassword()),
                request.getStartDate(),
                request.getEndDate()
        );
    }

    private CodefDto.IncomeProofRequest encrypt(CodefDto.IncomeProofRequest request) {
        if (request == null) {
            return null;
        }
        return new CodefDto.IncomeProofRequest(
                request.getOrganization(),
                request.getLoginType(),
                request.getId(),
                passwordEncryptor.encrypt(request.getPassword()),
                request.getSearchStartYear(),
                request.getSearchEndYear()
        );
    }

    private <T> CodefDto.Response post(String path, T request, String failureMessage) {
        try {
            return restTemplate.exchange(
                    urlProvider.resolve(resolveApiPath(path)),
                    HttpMethod.POST,
                    requestFactory.create(request),
                    CodefDto.Response.class
            ).getBody();
        } catch (RestClientException exception) {
            return CodefDto.Response.failure("CF-99999", failureMessage, exception.getMessage());
        }
    }

    private String resolveAssetPath(String institutionType) {
        if (BANK.equals(institutionType)) {
            return "/kr/bank/p/account/account-list";
        }

        if (CARD.equals(institutionType)) {
            return "/kr/card/p/account/card-list";
        }

        if (STOCK.equals(institutionType)) {
            return "/kr/stock/p/account/account-list";
        }

        return "/kr/unsupported";
    }

    private String resolveApiPath(String path) {
        return apiPathPrefix + (path.startsWith("/") ? path : "/" + path);
    }

    private String normalizePathPrefix(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isBlank()) {
            throw new IllegalArgumentException("CODEF API path prefix is required.");
        }
        String normalized = pathPrefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }
}
