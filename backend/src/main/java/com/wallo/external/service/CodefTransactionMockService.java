package com.wallo.external.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.external.dto.CodefDto;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CodefTransactionMockService {

    private static final String CARD_ORGANIZATION = "0311";
    private static final String BANK_ORGANIZATION = "0004";
    private static final String MOCK_BANK_ACCOUNT = "123456-01-789012";
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final CodefMockResponseLoader responseLoader;
    private final ObjectMapper objectMapper;

    public CodefTransactionMockService(
            CodefMockResponseLoader responseLoader,
            ObjectMapper objectMapper
    ) {
        this.responseLoader = responseLoader;
        this.objectMapper = objectMapper;
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
        if (!CARD_ORGANIZATION.equals(request.getOrganization())) {
            return unsupportedOrganization(request.getOrganization());
        }

        DateRange range = parseDateRange(request.getStartDate(), request.getEndDate());
        if (range == null) {
            return invalidRequest("startDate와 endDate는 유효한 YYYYMMDD 형식이어야 합니다.");
        }

        CodefDto.Response fixture = responseLoader.load("card-approval-list.json");
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

    public CodefDto.Response getBankTransactions(
            CodefDto.BankTransactionRequest request,
            String authorizationHeader
    ) {
        if (!hasBearerToken(authorizationHeader)) {
            return CodefDto.Response.failure(
                    "CF-40100",
                    "인증 정보가 올바르지 않습니다.",
                    "Authorization 헤더에 Bearer 토큰이 필요합니다."
            );
        }

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
        if (!BANK_ORGANIZATION.equals(request.getOrganization())) {
            return unsupportedOrganization(request.getOrganization());
        }
        if (!MOCK_BANK_ACCOUNT.equals(request.getAccount())) {
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

        CodefDto.Response fixture = responseLoader.load("bank-transaction-list.json");
        if (!isSuccess(fixture)) {
            return fixture;
        }

        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                fixture.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        List<CodefDto.BankTransaction> filteredTransactions = safeList(transactions).stream()
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

    private boolean hasBearerToken(String authorizationHeader) {
        return authorizationHeader != null
                && authorizationHeader.startsWith("Bearer ")
                && !authorizationHeader.substring("Bearer ".length()).isBlank();
    }

    private CodefDto.Response invalidRequest(String extraMessage) {
        return CodefDto.Response.failure("CF-40000", "요청값이 올바르지 않습니다.", extraMessage);
    }

    private CodefDto.Response unsupportedOrganization(String organization) {
        return CodefDto.Response.failure(
                "CF-40400",
                "지원하지 않는 기관입니다.",
                organization
        );
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
