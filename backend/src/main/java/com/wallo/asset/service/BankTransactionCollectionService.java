package com.wallo.asset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.client.BankTransactionClient;
import com.wallo.external.dto.CodefDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BankTransactionCollectionService {

    private static final String BANK_INSTITUTION_TYPE = "BANK";
    private static final String SOURCE_TYPE = "BANK_TRANSACTION";
    private static final String CLASSIFIER_VERSION = "bank-direction-v1";
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter RESPONSE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter();

    private final BankTransactionClient bankTransactionClient;
    private final ObjectMapper objectMapper;
    private final TransactionSourceKeyGenerator sourceKeyGenerator;
    private final AssetSyncMapper assetSyncMapper;
    private final Clock clock;

    public BankTransactionCollectionService(
            BankTransactionClient bankTransactionClient,
            ObjectMapper objectMapper,
            TransactionSourceKeyGenerator sourceKeyGenerator,
            AssetSyncMapper assetSyncMapper,
            Clock clock
    ) {
        this.bankTransactionClient = bankTransactionClient;
        this.objectMapper = objectMapper;
        this.sourceKeyGenerator = sourceKeyGenerator;
        this.assetSyncMapper = assetSyncMapper;
        this.clock = clock;
    }

    public int collectInitial(
            long userId,
            long accountId,
            String accountNumber,
            Institution institution
    ) {
        LocalDate endDate = LocalDate.now(clock);
        return collect(
                userId,
                accountId,
                accountNumber,
                institution,
                endDate.minusMonths(3),
                endDate
        );
    }

    public int collect(
            long userId,
            long accountId,
            String accountNumber,
            Institution institution,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateCollectionRequest(accountNumber, institution, startDate, endDate);

        CodefDto.Response response = bankTransactionClient.getTransactions(
                new CodefDto.BankTransactionRequest(
                        institution.getCodefOrganizationCode(),
                        ConnectionDto.MOCK_LOGIN_TYPE,
                        ConnectionDto.MOCK_ID,
                        ConnectionDto.MOCK_PASSWORD,
                        accountNumber,
                        startDate.format(REQUEST_DATE_FORMATTER),
                        endDate.format(REQUEST_DATE_FORMATTER)
                )
        );
        validateCodefResponse(response);

        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );

        int savedCount = 0;
        for (CodefDto.BankTransaction source : safeList(transactions)) {
            assetSyncMapper.upsertTransaction(
                    toTransaction(userId, accountId, accountNumber, institution, source)
            );
            savedCount++;
        }
        return savedCount;
    }

    private AssetSyncDto.Transaction toTransaction(
            long userId,
            long accountId,
            String accountNumber,
            Institution institution,
            CodefDto.BankTransaction source
    ) {
        if (source == null) {
            throw new IllegalArgumentException("은행 거래내역이 비어 있습니다.");
        }
        if (source.getResAccount() != null
                && !source.getResAccount().isBlank()
                && !accountNumber.equals(source.getResAccount())) {
            throw new IllegalArgumentException("요청 계좌와 응답 계좌가 일치하지 않습니다.");
        }

        long accountIn = parseNonNegativeAmount(source.getResAccountIn());
        long accountOut = parseNonNegativeAmount(source.getResAccountOut());
        DirectionClassification classification = classifyDirection(accountIn, accountOut);
        String transactionId = required(source.getResTrNo(), "은행 거래번호");
        String description = defaultValue(source.getResAccountDesc(), "계좌 거래");
        String sourceDedupKey = sourceKeyGenerator.forBankTransaction(
                institution.getCodefOrganizationCode(),
                accountId,
                transactionId
        );

        return new AssetSyncDto.Transaction(
                userId,
                null,
                accountId,
                classification.type(),
                classification.category(),
                classification.amount(),
                description,
                description,
                null,
                null,
                parseDate(source.getResTrDate()),
                parseTime(source.getResTrTime()),
                "BANK_DIRECTION",
                BigDecimal.ONE,
                CLASSIFIER_VERSION,
                SOURCE_TYPE,
                institution.getCodefOrganizationCode(),
                transactionId,
                sourceDedupKey
        );
    }

    private DirectionClassification classifyDirection(long accountIn, long accountOut) {
        if (accountIn > 0 && accountOut == 0) {
            return new DirectionClassification("INCOME", "INCOME", accountIn);
        }
        if (accountOut > 0 && accountIn == 0) {
            return new DirectionClassification("TRANSFER", "SEND", accountOut);
        }
        throw new IllegalArgumentException("입금액과 출금액 중 하나만 양수여야 합니다.");
    }

    private void validateCollectionRequest(
            String accountNumber,
            Institution institution,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (institution == null || !BANK_INSTITUTION_TYPE.equals(institution.getInstitutionType())) {
            throw new IllegalArgumentException("은행 기관만 거래내역을 수집할 수 있습니다.");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("계좌번호 값이 필요합니다.");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("은행 거래내역 조회 기간이 올바르지 않습니다.");
        }
    }

    private void validateCodefResponse(CodefDto.Response response) {
        if (response == null
                || response.getResult() == null
                || !ConnectionDto.CODEF_SUCCESS_CODE.equals(response.getResult().getCode())) {
            String message = response != null && response.getResult() != null
                    ? response.getResult().getMessage()
                    : "응답이 없습니다.";
            throw new IllegalStateException("은행 거래내역을 가져오지 못했습니다: " + message);
        }
    }

    private long parseNonNegativeAmount(String value) {
        try {
            long amount = Long.parseLong(value);
            if (amount < 0) {
                throw new NumberFormatException("금액은 음수일 수 없습니다.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("은행 거래금액 형식이 올바르지 않습니다.", exception);
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, REQUEST_DATE_FORMATTER);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("은행 거래일 형식이 올바르지 않습니다.", exception);
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, RESPONSE_TIME_FORMATTER);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("은행 거래시간 형식이 올바르지 않습니다.", exception);
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 값이 필요합니다.");
        }
        return value.trim();
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private record DirectionClassification(String type, String category, long amount) {
    }
}
