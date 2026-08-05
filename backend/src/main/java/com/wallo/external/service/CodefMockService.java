package com.wallo.external.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.external.dto.CodefDto;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class CodefMockService {

    private static final String BANK_ACCOUNT_LIST_PATH =
            "/mock/v1/kr/bank/p/account/account-list";
    private static final String DEFAULT_BANK_ORGANIZATION = "0004";
    private static final Map<String, String> FIXTURE_BY_PATH = Map.of(
            "/mock/v1/kr/card/p/account/card-list", "card-list.json",
            "/mock/v1/kr/stock/p/account/account-list", "stock-accounts.json"
    );
    private static final Map<String, String> BANK_ACCOUNT_FIXTURE_BY_ORGANIZATION = Map.of(
            "0004", "bank-accounts-0004.json",
            "0088", "bank-accounts-0088.json",
            "0081", "bank-accounts-0081.json"
    );
    private static final Map<String, Set<String>> MOCK_BANK_ACCOUNTS_BY_ORGANIZATION = Map.of(
            "0004", Set.of("123456-01-789012", "987654-01-321098"),
            "0088", Set.of("223344-01-556677"),
            "0081", Set.of("334455-01-667788")
    );
    private static final Map<String, String> BANK_TRANSACTION_FIXTURE_BY_ORGANIZATION = Map.of(
            "0004", "bank-transaction-list-0004.json",
            "0088", "bank-transaction-list-0088.json",
            "0081", "bank-transaction-list-0081.json"
    );
    private static final Map<String, String> CARD_APPROVAL_FIXTURE_BY_ORGANIZATION = Map.of(
            "0311", "card-approval-list-0311.json",
            "0301", "card-approval-list-0301.json"
    );
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final ObjectMapper objectMapper;

    public CodefMockService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CodefDto.Response getAssetResponse(String requestPath) {
        return getAssetResponse(requestPath, DEFAULT_BANK_ORGANIZATION);
    }

    public CodefDto.Response getAssetResponse(String requestPath, String organization) {
        String fixtureFileName = BANK_ACCOUNT_LIST_PATH.equals(requestPath)
                ? BANK_ACCOUNT_FIXTURE_BY_ORGANIZATION.get(organization)
                : FIXTURE_BY_PATH.get(requestPath);
        if (BANK_ACCOUNT_LIST_PATH.equals(requestPath) && fixtureFileName == null) {
            return CodefDto.Response.failure(
                    "CF-40400",
                    "지원하지 않는 은행 기관입니다.",
                    organization
            );
        }
        return load(fixtureFileName);
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
        DateRange range = parseDateRange(request.getStartDate(), request.getEndDate());
        if (range == null) {
            return invalidRequest("startDate와 endDate는 유효한 YYYYMMDD 형식이어야 합니다.");
        }

        String fixtureFileName = CARD_APPROVAL_FIXTURE_BY_ORGANIZATION.get(request.getOrganization());
        if (fixtureFileName == null) {
            return CodefDto.Response.failure(
                    "CF-40400",
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
        List<CodefDto.CardApproval> filteredApprovals = safeList(approvals).stream()
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
                    "CF-40400",
                    "지원하지 않는 은행 기관입니다.",
                    request.getOrganization()
            );
        }
        if (!availableAccounts.contains(request.getAccount())) {
            return CodefDto.Response.failure(
                    "CF-40401",
                    "계좌를 찾을 수 없습니다.",
                    request.getAccount()
            );
        }

        DateRange range = parseDateRange(request.getStartDate(), request.getEndDate());
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

    private CodefDto.Response load(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource("mock/codef/" + fileName);
            return objectMapper.readValue(resource.getInputStream(), CodefDto.Response.class);
        } catch (IOException exception) {
            return CodefDto.Response.failure(
                    "CF-40400",
                    "Mock 응답 파일을 찾을 수 없습니다.",
                    exception.getMessage()
            );
        }
    }

    private DateRange parseDateRange(String startDate, String endDate) {
        if (!isEightDigitDate(startDate) || !isEightDigitDate(endDate)) {
            return null;
        }

        try {
            LocalDate parsedStartDate = LocalDate.parse(startDate, REQUEST_DATE_FORMATTER);
            LocalDate parsedEndDate = LocalDate.parse(endDate, REQUEST_DATE_FORMATTER);
            if (parsedStartDate.isAfter(parsedEndDate)) {
                return null;
            }
            return new DateRange(parsedStartDate, parsedEndDate);
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private boolean isEightDigitDate(String value) {
        return value != null && value.matches("\\d{8}");
    }

    private boolean isSuccess(CodefDto.Response response) {
        return response != null
                && response.getResult() != null
                && "CF-00000".equals(response.getResult().getCode());
    }

    private CodefDto.Response invalidRequest(String extraMessage) {
        return CodefDto.Response.failure("CF-40000", "요청값이 올바르지 않습니다.", extraMessage);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
        private boolean contains(String basicIsoDate) {
            try {
                LocalDate date = LocalDate.parse(basicIsoDate, REQUEST_DATE_FORMATTER);
                return !date.isBefore(startDate) && !date.isAfter(endDate);
            } catch (DateTimeException exception) {
                return false;
            }
        }
    }
}
