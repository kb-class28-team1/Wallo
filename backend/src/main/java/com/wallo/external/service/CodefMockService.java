package com.wallo.external.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.external.CodefConstants;
import com.wallo.external.CodefDateTime;
import com.wallo.external.CodefResponseValidator;
import com.wallo.external.dto.CodefDto;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class CodefMockService {

    private static final String BANK_ACCOUNT_LIST_PATH =
            "/mock/v1/kr/bank/p/account/account-list";
    private static final String CARD_ACCOUNT_LIST_PATH =
            "/mock/v1/kr/card/p/account/card-list";
    private static final String DEFAULT_BANK_ORGANIZATION = "0004";
    private static final String DEFAULT_CARD_ORGANIZATION = "0311";
    private static final Map<String, String> FIXTURE_BY_PATH = Map.of(
            "/mock/v1/kr/stock/p/account/account-list", "stock-accounts-goal-saver.json"
    );
    private static final Map<String, String> BANK_ACCOUNT_FIXTURE_BY_ORGANIZATION = Map.of(
            "0004", "bank-accounts-goal-saver.json",
            "0088", "bank-accounts-goal-saver-0088.json",
            "0081", "bank-accounts-goal-saver-0081.json"
    );
    private static final Map<String, String> CARD_ACCOUNT_FIXTURE_BY_ORGANIZATION = Map.of(
            "0311", "card-list-goal-saver.json",
            "0301", "card-list-goal-saver-0301.json"
    );
    private static final Map<String, Set<String>> MOCK_BANK_ACCOUNTS_BY_ORGANIZATION = Map.of(
            "0004", Set.of("111111-01-222222", "LOAN-2021-0007"),
            "0088", Set.of("222222-01-333333"),
            "0081", Set.of("333333-01-444444")
    );
    private static final Map<String, Set<String>> MOCK_CARD_NUMBERS_BY_ORGANIZATION = Map.of(
            "0311", Set.of("4555-0000-0000-1222"),
            "0301", Set.of("5666-0000-0000-2333")
    );
    private static final Map<String, String> BANK_TRANSACTION_FIXTURE_BY_ORGANIZATION = Map.of(
            "0004", "bank-transaction-list-goal-saver.json",
            "0088", "bank-transaction-list-goal-saver.json",
            "0081", "bank-transaction-list-goal-saver.json"
    );
    private static final Map<String, String> CARD_APPROVAL_FIXTURE_BY_ORGANIZATION = Map.of(
            "0311", "card-approval-list-goal-saver.json",
            "0301", "card-approval-list-goal-saver.json"
    );
    private static final String INCOME_PROOF_FIXTURE = "income-proof-goal-saver.json";
    private static final String AFTER_DEMO_LOGIN_PREFIX = "after-";
    private final ObjectMapper objectMapper;

    public CodefMockService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CodefDto.Response getAssetResponse(String requestPath) {
        String defaultOrganization = CARD_ACCOUNT_LIST_PATH.equals(requestPath)
                ? DEFAULT_CARD_ORGANIZATION
                : DEFAULT_BANK_ORGANIZATION;
        return getAssetResponse(requestPath, defaultOrganization);
    }

    public CodefDto.Response getAssetResponse(String requestPath, String organization) {
        String fixtureFileName;
        if (BANK_ACCOUNT_LIST_PATH.equals(requestPath)) {
            fixtureFileName = BANK_ACCOUNT_FIXTURE_BY_ORGANIZATION.get(organization);
        } else if (CARD_ACCOUNT_LIST_PATH.equals(requestPath)) {
            fixtureFileName = CARD_ACCOUNT_FIXTURE_BY_ORGANIZATION.get(organization);
        } else {
            fixtureFileName = FIXTURE_BY_PATH.get(requestPath);
        }
        if ((BANK_ACCOUNT_LIST_PATH.equals(requestPath) || CARD_ACCOUNT_LIST_PATH.equals(requestPath))
                && fixtureFileName == null) {
            return CodefDto.Response.failure(
                    CodefConstants.NOT_FOUND_CODE,
                    "지원하지 않는 은행 기관입니다.",
                    organization
            );
        }
        return load(fixtureFileName);
    }

    /**
     * The After fixture is already seeded with its one-year-final asset state.
     * The shared goal-saver mock fixture contains a 4.8 million won loan, so
     * replaying it during the After demo would create a second active loan.
     */
    public CodefDto.Response getAssetResponse(String requestPath, String organization, String loginId) {
        if (loginId != null && loginId.startsWith(AFTER_DEMO_LOGIN_PREFIX)) {
            return CodefDto.Response.success(new CodefDto.AssetData());
        }
        return getAssetResponse(requestPath, organization);
    }

    public CodefDto.Response getCardApprovals(CodefDto.CardApprovalRequest request) {
        String requiredField = firstMissingCommonField(
                request == null ? null : request.getOrganization(),
                request == null ? null : request.getLoginType(),
                request == null ? null : request.getId(),
                request == null ? null : request.getPassword(),
                request == null ? null : request.getStartDate(),
                request == null ? null : request.getEndDate()
        );
        if (requiredField != null) {
            return invalidRequest(requiredField + " 값이 필요합니다.");
        }
        CodefDateTime.DateRange range = parseDateRange(request.getStartDate(), request.getEndDate());
        if (range == null) {
            return invalidRequest("startDate와 endDate는 유효한 YYYYMMDD 형식이어야 합니다.");
        }

        String fixtureFileName = CARD_APPROVAL_FIXTURE_BY_ORGANIZATION.get(request.getOrganization());
        if (fixtureFileName == null) {
            return CodefDto.Response.failure(
                    CodefConstants.NOT_FOUND_CODE,
                    "지원하지 않는 카드 기관입니다.",
                    request.getOrganization()
            );
        }

        CodefDto.Response fixture = load(fixtureFileName);
        if (!isSuccess(fixture)) {
            return fixture;
        }

        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                fixture.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        Set<String> availableCardNumbers = MOCK_CARD_NUMBERS_BY_ORGANIZATION.get(request.getOrganization());
        List<CodefDto.CardApproval> filteredApprovals = safeList(approvals).stream()
                .filter(approval -> availableCardNumbers.contains(approval.getResCardNo()))
                .filter(approval -> range.contains(approval.getResUsedDate()))
                .toList();

        return CodefDto.Response.success(filteredApprovals);
    }

    public CodefDto.Response getBankTransactions(CodefDto.BankTransactionRequest request) {
        String requiredField = firstMissingCommonField(
                request == null ? null : request.getOrganization(),
                request == null ? null : request.getLoginType(),
                request == null ? null : request.getId(),
                request == null ? null : request.getPassword(),
                request == null ? null : request.getStartDate(),
                request == null ? null : request.getEndDate()
        );
        if (requiredField != null) {
            return invalidRequest(requiredField + " 값이 필요합니다.");
        }
        if (isBlank(request.getAccount())) {
            return invalidRequest("account 값이 필요합니다.");
        }
        Set<String> availableAccounts = MOCK_BANK_ACCOUNTS_BY_ORGANIZATION.get(request.getOrganization());
        if (availableAccounts == null) {
            return CodefDto.Response.failure(
                    CodefConstants.NOT_FOUND_CODE,
                    "지원하지 않는 은행 기관입니다.",
                    request.getOrganization()
            );
        }
        if (!availableAccounts.contains(request.getAccount())) {
            return CodefDto.Response.failure(
                    CodefConstants.ACCOUNT_NOT_FOUND_CODE,
                    "계좌를 찾을 수 없습니다.",
                    request.getAccount()
            );
        }

        CodefDateTime.DateRange range = parseDateRange(request.getStartDate(), request.getEndDate());
        if (range == null) {
            return invalidRequest("startDate와 endDate는 유효한 YYYYMMDD 형식이어야 합니다.");
        }

        CodefDto.Response fixture = load(
                BANK_TRANSACTION_FIXTURE_BY_ORGANIZATION.get(request.getOrganization())
        );
        if (!isSuccess(fixture)) {
            return fixture;
        }

        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                fixture.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        List<CodefDto.BankTransaction> filteredTransactions = safeList(transactions).stream()
                .filter(transaction -> request.getAccount().equals(transaction.getResAccount()))
                .filter(transaction -> range.contains(transaction.getResTrDate()))
                .toList();

        return CodefDto.Response.success(filteredTransactions);
    }

    public CodefDto.Response getIncomeProof(CodefDto.IncomeProofRequest request) {
        String requiredField = firstMissingIncomeProofField(
                request == null ? null : request.getOrganization(),
                request == null ? null : request.getLoginType(),
                request == null ? null : request.getId(),
                request == null ? null : request.getPassword(),
                request == null ? null : request.getSearchStartYear(),
                request == null ? null : request.getSearchEndYear()
        );
        if (requiredField != null) {
            return invalidRequest(requiredField + " is required.");
        }

        Integer startYear = parseYear(request.getSearchStartYear());
        Integer endYear = parseYear(request.getSearchEndYear());
        if (startYear == null || endYear == null || startYear > endYear) {
            return invalidRequest("searchStartYear and searchEndYear must be YYYY.");
        }

        CodefDto.Response fixture = load(INCOME_PROOF_FIXTURE);
        if (!isSuccess(fixture)) {
            return fixture;
        }

        CodefDto.IncomeProofData incomeProofData = objectMapper.convertValue(
                fixture.getData(),
                CodefDto.IncomeProofData.class
        );
        List<CodefDto.PaymentDetails> filteredPayments = safeList(
                incomeProofData.getResPaymentDetailsStatusList()
        ).stream()
                .filter(payment -> payment != null)
                .filter(payment -> {
                    Integer paymentYear = parseYear(payment.getResAttrYear());
                    return paymentYear != null
                            && startYear <= paymentYear
                            && paymentYear <= endYear;
                })
                .toList();

        if (filteredPayments.isEmpty()) {
            return CodefDto.Response.failure(
                    CodefConstants.NOT_FOUND_CODE,
                    "Income proof mock response is unavailable for the requested year.",
                    request.getSearchStartYear() + "-" + request.getSearchEndYear()
            );
        }

        incomeProofData.setResPaymentDetailsStatusList(filteredPayments);
        return new CodefDto.Response(fixture.getResult(), incomeProofData);
    }

    private String firstMissingCommonField(
            String organization,
            String loginType,
            String id,
            String password,
            String startDate,
            String endDate
    ) {
        if (isBlank(organization)) return "organization";
        if (isBlank(loginType)) return "loginType";
        if (isBlank(id)) return "id";
        if (isBlank(password)) return "password";
        if (isBlank(startDate)) return "startDate";
        if (isBlank(endDate)) return "endDate";
        return null;
    }

    private String firstMissingIncomeProofField(
            String organization,
            String loginType,
            String id,
            String password,
            String searchStartYear,
            String searchEndYear
    ) {
        if (isBlank(organization)) return "organization";
        if (isBlank(loginType)) return "loginType";
        if (isBlank(id)) return "id";
        if (isBlank(password)) return "password";
        if (isBlank(searchStartYear)) return "searchStartYear";
        if (isBlank(searchEndYear)) return "searchEndYear";
        return null;
    }

    private CodefDto.Response load(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource("mock/codef/" + fileName);
            return objectMapper.readValue(resource.getInputStream(), CodefDto.Response.class);
        } catch (IOException exception) {
            return CodefDto.Response.failure(
                    CodefConstants.NOT_FOUND_CODE,
                    "Mock 응답 파일을 찾을 수 없습니다.",
                    exception.getMessage()
            );
        }
    }

    private CodefDateTime.DateRange parseDateRange(String startDate, String endDate) {
        return CodefDateTime.parseDateRange(startDate, endDate).orElse(null);
    }

    private Integer parseYear(String value) {
        OptionalInt year = CodefDateTime.parseYear(value);
        return year.isPresent() ? year.getAsInt() : null;
    }

    private boolean isSuccess(CodefDto.Response response) {
        return CodefResponseValidator.isSuccess(response);
    }

    private CodefDto.Response invalidRequest(String extraMessage) {
        return CodefDto.Response.failure(
                CodefConstants.INVALID_REQUEST_CODE,
                "요청값이 올바르지 않습니다.",
                extraMessage
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

}
