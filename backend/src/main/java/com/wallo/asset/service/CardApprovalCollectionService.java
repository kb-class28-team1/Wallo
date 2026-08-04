package com.wallo.asset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.classification.ExpenseCategoryClassifier;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.client.CardApprovalClient;
import com.wallo.external.dto.CodefDto;
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
public class CardApprovalCollectionService {

    private static final String CARD_INSTITUTION_TYPE = "CARD";
    private static final String SOURCE_TYPE = "CARD_APPROVAL";
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter RESPONSE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .toFormatter();

    private final CardApprovalClient cardApprovalClient;
    private final ObjectMapper objectMapper;
    private final ExpenseCategoryClassifier categoryClassifier;
    private final TransactionSourceKeyGenerator sourceKeyGenerator;
    private final AssetSyncMapper assetSyncMapper;
    private final Clock clock;

    public CardApprovalCollectionService(
            CardApprovalClient cardApprovalClient,
            ObjectMapper objectMapper,
            ExpenseCategoryClassifier categoryClassifier,
            TransactionSourceKeyGenerator sourceKeyGenerator,
            AssetSyncMapper assetSyncMapper,
            Clock clock
    ) {
        this.cardApprovalClient = cardApprovalClient;
        this.objectMapper = objectMapper;
        this.categoryClassifier = categoryClassifier;
        this.sourceKeyGenerator = sourceKeyGenerator;
        this.assetSyncMapper = assetSyncMapper;
        this.clock = clock;
    }

    public int collectInitial(long userId, long connectionId, Institution institution) {
        LocalDate endDate = LocalDate.now(clock);
        return collect(userId, connectionId, institution, endDate.minusMonths(3), endDate);
    }

    public int collect(
            long userId,
            long connectionId,
            Institution institution,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateCollectionRequest(institution, startDate, endDate);

        CodefDto.Response response = cardApprovalClient.getApprovals(
                new CodefDto.CardApprovalRequest(
                        institution.getCodefOrganizationCode(),
                        ConnectionDto.MOCK_LOGIN_TYPE,
                        ConnectionDto.MOCK_ID,
                        ConnectionDto.MOCK_PASSWORD,
                        startDate.format(REQUEST_DATE_FORMATTER),
                        endDate.format(REQUEST_DATE_FORMATTER)
                )
        );
        validateCodefResponse(response);

        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );

        int savedCount = 0;
        for (CodefDto.CardApproval approval : safeList(approvals)) {
            assetSyncMapper.upsertTransaction(
                    toTransaction(userId, connectionId, institution, approval)
            );
            savedCount++;
        }
        return savedCount;
    }

    private AssetSyncDto.Transaction toTransaction(
            long userId,
            long connectionId,
            Institution institution,
            CodefDto.CardApproval approval
    ) {
        if (approval == null) {
            throw new IllegalArgumentException("카드 승인내역이 비어 있습니다.");
        }

        Long cardId = resolveCardId(connectionId, approval.getResCardNo());
        String approvalNo = required(approval.getResApprovalNo(), "카드 승인번호");
        String merchantName = defaultValue(approval.getResMemberName(), "카드 결제");
        long amount = parsePositiveAmount(approval.getResUsedAmount());
        LocalDate transactionDate = parseDate(approval.getResUsedDate());
        LocalTime transactionTime = parseTime(approval.getResUsedTime());
        ExpenseCategoryClassifier.Result classification = categoryClassifier.classify(
                new ExpenseCategoryClassifier.Context(
                        merchantName,
                        approval.getResMemberSector(),
                        amount
                )
        );
        String sourceDedupKey = sourceKeyGenerator.forCardApproval(
                institution.getCodefOrganizationCode(),
                cardId,
                approvalNo
        );

        return new AssetSyncDto.Transaction(
                userId,
                cardId,
                null,
                "EXPENSE",
                classification.category(),
                amount,
                merchantName,
                approval.getResMemberName(),
                approval.getResMemberSector(),
                approvalNo,
                transactionDate,
                transactionTime,
                classification.source(),
                classification.confidence(),
                classification.classifierVersion(),
                SOURCE_TYPE,
                institution.getCodefOrganizationCode(),
                approvalNo,
                sourceDedupKey
        );
    }

    private Long resolveCardId(long connectionId, String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return null;
        }
        Long cardId = assetSyncMapper.findCardId(connectionId, cardNumber);
        if (cardId == null) {
            throw new IllegalStateException("승인내역에 해당하는 연동 카드를 찾을 수 없습니다.");
        }
        return cardId;
    }

    private void validateCollectionRequest(
            Institution institution,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (institution == null || !CARD_INSTITUTION_TYPE.equals(institution.getInstitutionType())) {
            throw new IllegalArgumentException("카드 기관만 승인내역을 수집할 수 있습니다.");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("카드 승인내역 조회 기간이 올바르지 않습니다.");
        }
    }

    private void validateCodefResponse(CodefDto.Response response) {
        if (response == null
                || response.getResult() == null
                || !ConnectionDto.CODEF_SUCCESS_CODE.equals(response.getResult().getCode())) {
            String message = response != null && response.getResult() != null
                    ? response.getResult().getMessage()
                    : "응답이 없습니다.";
            throw new IllegalStateException("카드 승인내역을 가져오지 못했습니다: " + message);
        }
    }

    private long parsePositiveAmount(String value) {
        try {
            long amount = Long.parseLong(value);
            if (amount <= 0) {
                throw new NumberFormatException("금액은 양수여야 합니다.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("카드 승인금액 형식이 올바르지 않습니다.", exception);
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, REQUEST_DATE_FORMATTER);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("카드 승인일 형식이 올바르지 않습니다.", exception);
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, RESPONSE_TIME_FORMATTER);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("카드 승인시간 형식이 올바르지 않습니다.", exception);
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
}
