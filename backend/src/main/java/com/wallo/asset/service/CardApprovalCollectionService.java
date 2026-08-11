package com.wallo.asset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.classification.ExpenseCategoryClassifier;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.dto.ConnectionDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.auth.CodefCredential;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.client.CardApprovalClient;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
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
    private static final Logger LOGGER = Logger.getLogger(CardApprovalCollectionService.class.getName());

    private final CardApprovalClient cardApprovalClient;
    private final CodefCredentialProvider codefCredentialProvider;
    private final ObjectMapper objectMapper;
    private final ExpenseCategoryClassifier categoryClassifier;
    private final TransactionSourceKeyGenerator sourceKeyGenerator;
    private final AssetSyncMapper assetSyncMapper;
    private final Clock clock;
    private final ConsumptionInsightCache consumptionInsightCache;

    public CardApprovalCollectionService(
            CardApprovalClient cardApprovalClient,
            CodefCredentialProvider codefCredentialProvider,
            ObjectMapper objectMapper,
            ExpenseCategoryClassifier categoryClassifier,
            TransactionSourceKeyGenerator sourceKeyGenerator,
            AssetSyncMapper assetSyncMapper,
            Clock clock,
            ConsumptionInsightCache consumptionInsightCache
    ) {
        this.cardApprovalClient = cardApprovalClient;
        this.codefCredentialProvider = codefCredentialProvider;
        this.objectMapper = objectMapper;
        this.categoryClassifier = categoryClassifier;
        this.sourceKeyGenerator = sourceKeyGenerator;
        this.assetSyncMapper = assetSyncMapper;
        this.clock = clock;
        this.consumptionInsightCache = consumptionInsightCache;
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

        long startedAt = System.nanoTime();
        long apiStartedAt = System.nanoTime();
        CodefCredential credential = codefCredentialProvider.getCredential(
                userId,
                institution.getCodefOrganizationCode()
        );
        CodefDto.Response response = cardApprovalClient.getApprovals(
                new CodefDto.CardApprovalRequest(
                        institution.getCodefOrganizationCode(),
                        credential.loginType(),
                        credential.id(),
                        credential.password(),
                        startDate.format(REQUEST_DATE_FORMATTER),
                        endDate.format(REQUEST_DATE_FORMATTER)
                )
        );
        long apiElapsedMs = elapsedMillis(apiStartedAt);
        validateCodefResponse(response);

        long conversionStartedAt = System.nanoTime();
        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        long conversionElapsedMs = elapsedMillis(conversionStartedAt);

        int savedCount = 0;
        int reusedClassificationCount = 0;
        int aiRequestCount = 0;
        long classificationStartedAt = System.nanoTime();
        List<PreparedApproval> preparedApprovals = safeList(approvals).stream()
                .map(approval -> prepareApproval(userId, connectionId, institution, approval))
                .toList();
        Map<String, ClassificationResolution> classifications = resolveClassifications(
                userId,
                institution,
                preparedApprovals
        );
        long classificationElapsedMs = elapsedMillis(classificationStartedAt);
        long processingStartedAt = System.nanoTime();
        for (PreparedApproval approval : preparedApprovals) {
            TransactionMapping mapping = toTransaction(
                    approval,
                    classifications.get(approval.sourceDedupKey())
            );
            assetSyncMapper.upsertTransaction(mapping.transaction());
            if (mapping.reusedClassification()) {
                reusedClassificationCount++;
            } else if ("AI".equals(mapping.transaction().getCategorySource())) {
                aiRequestCount++;
            }
            savedCount++;
        }
        if (savedCount > 0) {
            invalidateConsumptionInsightCache(userId, startDate, endDate);
        }
        long processingElapsedMs = elapsedMillis(processingStartedAt);
        LOGGER.info(String.format(
                Locale.ROOT,
                "asset-sync card organization=%s records=%d saved=%d reusedClassification=%d aiRequests=%d "
                        + "apiMs=%d conversionMs=%d classificationMs=%d processingMs=%d totalMs=%d",
                institution.getCodefOrganizationCode(),
                safeList(approvals).size(),
                savedCount,
                reusedClassificationCount,
                aiRequestCount,
                apiElapsedMs,
                conversionElapsedMs,
                classificationElapsedMs,
                processingElapsedMs,
                elapsedMillis(startedAt)
        ));
        return savedCount;
    }

    private void invalidateConsumptionInsightCache(
            long userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        LocalDate reportDataStart = currentMonth.minusMonths(1).atDay(1);
        LocalDate reportDataEnd = currentMonth.atEndOfMonth();
        boolean affectsCurrentReport = !endDate.isBefore(reportDataStart)
                && !startDate.isAfter(reportDataEnd);
        if (affectsCurrentReport) {
            consumptionInsightCache.invalidateAfterCommit(userId, currentMonth);
        }
    }

    private PreparedApproval prepareApproval(
            long userId,
            long connectionId,
            Institution institution,
            CodefDto.CardApproval approval
    ) {
        if (approval == null) {
            throw new IllegalArgumentException("카드 승인내역이 비어 있습니다.");
        }

        String cardNumber = AssetIdentifierNormalizer.normalize(approval.getResCardNo(), "card number");
        Long cardId = resolveCardId(connectionId, cardNumber);
        String approvalNo = required(approval.getResApprovalNo(), "카드 승인번호");
        String merchantName = defaultValue(approval.getResMemberName(), "카드 결제");
        long amount = parsePositiveAmount(approval.getResUsedAmount());
        LocalDate transactionDate = parseDate(approval.getResUsedDate());
        LocalTime transactionTime = parseTime(approval.getResUsedTime());
        ExpenseCategoryClassifier.Context context = new ExpenseCategoryClassifier.Context(
                merchantName,
                approval.getResMemberSector(),
                amount
        );
        String sourceDedupKey = sourceKeyGenerator.forCardApproval(
                institution.getCodefOrganizationCode(),
                cardNumber,
                approvalNo
        );
        return new PreparedApproval(
                userId,
                cardId,
                approvalNo,
                merchantName,
                approval.getResMemberName(),
                approval.getResMemberSector(),
                amount,
                transactionDate,
                transactionTime,
                context,
                sourceDedupKey,
                institution.getCodefOrganizationCode()
        );
    }

    private TransactionMapping toTransaction(
            PreparedApproval approval,
            ClassificationResolution resolution
    ) {
        ExpenseCategoryClassifier.Result classification = resolution.result();
        AssetSyncDto.Transaction transaction = new AssetSyncDto.Transaction(
                approval.userId(),
                approval.cardId(),
                null,
                "EXPENSE",
                classification.category(),
                approval.amount(),
                approval.merchantName(),
                approval.originalMerchantName(),
                approval.merchantSector(),
                approval.approvalNo(),
                approval.transactionDate(),
                approval.transactionTime(),
                classification.source(),
                classification.confidence(),
                classification.classifierVersion(),
                SOURCE_TYPE,
                approval.sourceOrganizationCode(),
                approval.approvalNo(),
                approval.sourceDedupKey()
        );
        return new TransactionMapping(transaction, resolution.reused());
    }

    private Map<String, ClassificationResolution> resolveClassifications(
            long userId,
            Institution institution,
            List<PreparedApproval> approvals
    ) {
        Map<String, ClassificationResolution> resolutions = new HashMap<>();
        List<ExpenseCategoryClassifier.Context> pendingContexts = new ArrayList<>();
        for (PreparedApproval approval : approvals) {
            Optional<ExpenseCategoryClassifier.Result> deterministicClassification =
                    categoryClassifier.classifyBeforeAi(approval.context());
            if (deterministicClassification != null && deterministicClassification.isPresent()) {
                resolutions.put(
                        approval.sourceDedupKey(),
                        new ClassificationResolution(deterministicClassification.get(), false)
                );
                continue;
            }

            AssetSyncDto.ExistingClassification existing = assetSyncMapper.findExistingClassification(
                    userId,
                    SOURCE_TYPE,
                    institution.getCodefOrganizationCode(),
                    approval.sourceDedupKey()
            );
            if (isReusable(existing)) {
                resolutions.put(
                        approval.sourceDedupKey(),
                        new ClassificationResolution(
                                new ExpenseCategoryClassifier.Result(
                                        existing.getCategory(),
                                        existing.getCategorySource(),
                                        existing.getCategoryConfidence(),
                                        existing.getClassifierVersion()
                                ),
                                true
                        )
                );
            } else if (!pendingContexts.contains(approval.context())) {
                pendingContexts.add(approval.context());
            }
        }

        List<ExpenseCategoryClassifier.Result> classified = categoryClassifier.classifyBatch(pendingContexts);
        for (PreparedApproval approval : approvals) {
            if (resolutions.containsKey(approval.sourceDedupKey())) {
                continue;
            }
            int contextIndex = pendingContexts.indexOf(approval.context());
            resolutions.put(
                    approval.sourceDedupKey(),
                    new ClassificationResolution(classified.get(contextIndex), false)
            );
        }
        return resolutions;
    }

    private boolean isReusable(AssetSyncDto.ExistingClassification existing) {
        return existing != null
                && existing.getCategory() != null
                && !existing.getCategory().isBlank()
                && existing.getCategorySource() != null
                && !existing.getCategorySource().isBlank()
                && !"FALLBACK".equals(existing.getCategorySource());
    }

    private Long resolveCardId(long connectionId, String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return null;
        }
        Long cardId = assetSyncMapper.findCardId(
                connectionId,
                AssetIdentifierNormalizer.normalize(cardNumber, "card number")
        );
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

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private record ClassificationResolution(
            ExpenseCategoryClassifier.Result result,
            boolean reused
    ) {
    }

    private record TransactionMapping(
            AssetSyncDto.Transaction transaction,
            boolean reusedClassification
    ) {
    }

    private record PreparedApproval(
            long userId,
            Long cardId,
            String approvalNo,
            String merchantName,
            String originalMerchantName,
            String merchantSector,
            long amount,
            LocalDate transactionDate,
            LocalTime transactionTime,
            ExpenseCategoryClassifier.Context context,
            String sourceDedupKey,
            String sourceOrganizationCode
    ) {
    }
}
