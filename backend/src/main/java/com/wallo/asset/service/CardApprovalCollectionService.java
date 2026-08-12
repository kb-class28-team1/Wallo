package com.wallo.asset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.classification.ExpenseCategoryClassifier;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.auth.CodefCredential;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.CodefRetryExecutor;
import com.wallo.external.client.CardApprovalClient;
import com.wallo.external.CodefDateTime;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CardApprovalCollectionService {

    private static final String SOURCE_TYPE = AssetTransactionConstants.CARD_APPROVAL_SOURCE_TYPE;
    private static final Logger LOGGER = Logger.getLogger(CardApprovalCollectionService.class.getName());

    private final CardApprovalClient cardApprovalClient;
    private final CodefCredentialProvider codefCredentialProvider;
    private final CodefRetryExecutor codefRetryExecutor;
    private final ObjectMapper objectMapper;
    private final ExpenseCategoryClassifier categoryClassifier;
    private final TransactionSourceKeyGenerator sourceKeyGenerator;
    private final AssetSyncMapper assetSyncMapper;
    private final Clock clock;
    private final ConsumptionInsightCache consumptionInsightCache;

    public CardApprovalCollectionService(
            CardApprovalClient cardApprovalClient,
            CodefCredentialProvider codefCredentialProvider,
            CodefRetryExecutor codefRetryExecutor,
            ObjectMapper objectMapper,
            ExpenseCategoryClassifier categoryClassifier,
            TransactionSourceKeyGenerator sourceKeyGenerator,
            AssetSyncMapper assetSyncMapper,
            Clock clock,
            ConsumptionInsightCache consumptionInsightCache
    ) {
        this.cardApprovalClient = cardApprovalClient;
        this.codefCredentialProvider = codefCredentialProvider;
        this.codefRetryExecutor = codefRetryExecutor;
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
        return collectWithStats(userId, connectionId, institution, startDate, endDate).total();
    }

    public AssetSyncDto.SyncStats collectWithStats(
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
        CodefDto.CardApprovalRequest request = new CodefDto.CardApprovalRequest(
                institution.getCodefOrganizationCode(),
                credential.loginType(),
                credential.id(),
                credential.password(),
                CodefDateTime.formatDate(startDate),
                CodefDateTime.formatDate(endDate)
        );
        CodefDto.Response response = codefRetryExecutor.execute(
                "card approval collection organization=" + institution.getCodefOrganizationCode(),
                () -> cardApprovalClient.getApprovals(request)
        );
        long apiElapsedMs = elapsedMillis(apiStartedAt);

        long conversionStartedAt = System.nanoTime();
        List<CodefDto.CardApproval> approvals = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.CardApproval>>() { }
        );
        List<CodefDto.CardApproval> activeCardApprovals = filterActiveCardApprovals(
                connectionId,
                approvals
        );
        long conversionElapsedMs = elapsedMillis(conversionStartedAt);

        int savedCount = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        int reusedClassificationCount = 0;
        int aiRequestCount = 0;
        long classificationStartedAt = System.nanoTime();
        List<PreparedApproval> preparedApprovals = safeList(activeCardApprovals).stream()
                .map(approval -> prepareApproval(userId, connectionId, institution, approval))
                .toList();
        preparedApprovals = TransactionBatchDeduplicator.deduplicate(
                preparedApprovals,
                approval -> approval.sourceIdentity().sourceDedupKey(),
                SOURCE_TYPE
        );
        int duplicateCount = safeList(activeCardApprovals).size() - preparedApprovals.size();
        int inactiveCardApprovalCount = safeList(approvals).size() - safeList(activeCardApprovals).size();
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
                    classifications.get(approval.sourceIdentity().sourceDedupKey())
            );
            boolean existingTransaction = assetSyncMapper.findExistingTransactionId(
                    mapping.transaction().getUserId(),
                    mapping.transaction().getSourceType(),
                    mapping.transaction().getSourceOrganizationCode(),
                    mapping.transaction().getSourceDedupKey()
            ) != null;
            assetSyncMapper.upsertTransaction(mapping.transaction());
            if (existingTransaction) {
                updatedCount++;
            } else {
                insertedCount++;
            }
            if (mapping.reusedClassification()) {
                reusedClassificationCount++;
            } else if (AssetTransactionConstants.AI_CATEGORY_SOURCE
                    .equals(mapping.transaction().getCategorySource())) {
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
                "asset-sync card organization=%s records=%d duplicates=%d saved=%d reusedClassification=%d aiRequests=%d "
                        + "inactiveCardApprovals=%d apiMs=%d conversionMs=%d classificationMs=%d processingMs=%d totalMs=%d",
                institution.getCodefOrganizationCode(),
                safeList(approvals).size(),
                duplicateCount,
                savedCount,
                reusedClassificationCount,
                aiRequestCount,
                inactiveCardApprovalCount,
                apiElapsedMs,
                conversionElapsedMs,
                classificationElapsedMs,
                processingElapsedMs,
                elapsedMillis(startedAt)
        ));
        return new AssetSyncDto.SyncStats(insertedCount, updatedCount);
    }

    private List<CodefDto.CardApproval> filterActiveCardApprovals(
            long connectionId,
            List<CodefDto.CardApproval> approvals
    ) {
        List<String> activeCardNumbers = assetSyncMapper.findActiveCardNumbers(connectionId);
        if (activeCardNumbers == null) {
            return safeList(approvals);
        }

        Set<String> activeCardNumberSet = new HashSet<>();
        for (String cardNumber : activeCardNumbers) {
            if (cardNumber != null && !cardNumber.isBlank()) {
                activeCardNumberSet.add(
                        AssetIdentifierNormalizer.normalize(cardNumber, "card number")
                );
            }
        }

        return safeList(approvals).stream()
                .filter(approval -> approval == null
                        || approval.getResCardNo() == null
                        || approval.getResCardNo().isBlank()
                        || activeCardNumberSet.contains(
                                AssetIdentifierNormalizer.normalize(
                                        approval.getResCardNo(),
                                        "card number"
                                )
                        ))
                .toList();
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

        String approvalNo = required(approval.getResApprovalNo(), "카드 승인번호");
        TransactionSourceIdentity sourceIdentity = sourceKeyGenerator.identityForCardApproval(
                institution.getCodefOrganizationCode(),
                approval.getResCardNo(),
                approvalNo
        );
        String cardNumber = sourceIdentity.normalizedAssetIdentifier();
        Long cardId = resolveCardId(connectionId, cardNumber);
        String merchantName = defaultValue(approval.getResMemberName(), "카드 결제");
        long amount = parsePositiveAmount(approval.getResUsedAmount());
        LocalDate transactionDate = parseDate(approval.getResUsedDate());
        LocalTime transactionTime = parseTime(approval.getResUsedTime());
        ExpenseCategoryClassifier.Context context = new ExpenseCategoryClassifier.Context(
                merchantName,
                approval.getResMemberSector(),
                amount
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
                sourceIdentity
        );
    }

    private TransactionMapping toTransaction(
            PreparedApproval approval,
            ClassificationResolution resolution
    ) {
        ExpenseCategoryClassifier.Result classification = resolution.result();
        TransactionSourceIdentity sourceIdentity = approval.sourceIdentity();
        TransactionRelationValidator.validate(sourceIdentity.sourceType(), approval.cardId(), null);
        AssetSyncDto.Transaction transaction = new AssetSyncDto.Transaction(
                approval.userId(),
                approval.cardId(),
                null,
                AssetTransactionConstants.EXPENSE_TYPE,
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
                sourceIdentity.sourceType(),
                sourceIdentity.sourceOrganizationCode(),
                sourceIdentity.sourceTransactionId(),
                sourceIdentity.sourceDedupKey()
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
                        approval.sourceIdentity().sourceDedupKey(),
                        new ClassificationResolution(deterministicClassification.get(), false)
                );
                continue;
            }

            AssetSyncDto.ExistingClassification existing = assetSyncMapper.findExistingClassification(
                    userId,
                    approval.sourceIdentity().sourceType(),
                    approval.sourceIdentity().sourceOrganizationCode(),
                    approval.sourceIdentity().sourceDedupKey()
            );
            if (isReusable(existing)) {
                resolutions.put(
                        approval.sourceIdentity().sourceDedupKey(),
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
            if (resolutions.containsKey(approval.sourceIdentity().sourceDedupKey())) {
                continue;
            }
            int contextIndex = pendingContexts.indexOf(approval.context());
            resolutions.put(
                    approval.sourceIdentity().sourceDedupKey(),
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
                && !AssetTransactionConstants.FALLBACK_CATEGORY_SOURCE.equals(existing.getCategorySource());
    }

    private Long resolveCardId(long connectionId, String cardNumber) {
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
        if (institution == null
                || !AssetTransactionConstants.CARD_INSTITUTION_TYPE.equals(institution.getInstitutionType())) {
            throw new IllegalArgumentException("카드 기관만 승인내역을 수집할 수 있습니다.");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("카드 승인내역 조회 기간이 올바르지 않습니다.");
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
        return CodefDateTime.parseDate(value, "Card approval date");
    }

    private LocalTime parseTime(String value) {
        return CodefDateTime.parseTime(value, "Card approval time");
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
            TransactionSourceIdentity sourceIdentity
    ) {
    }
}
