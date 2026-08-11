package com.wallo.asset.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.classification.ExpenseCategoryClassifier;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.auth.CodefCredential;
import com.wallo.external.auth.CodefCredentialProvider;
import com.wallo.external.client.BankTransactionClient;
import com.wallo.external.CodefDateTime;
import com.wallo.external.CodefResponseValidator;
import com.wallo.external.dto.CodefDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
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
public class BankTransactionCollectionService {

    private static final String SOURCE_TYPE = AssetTransactionConstants.BANK_TRANSACTION_SOURCE_TYPE;
    private static final Logger LOGGER = Logger.getLogger(BankTransactionCollectionService.class.getName());

    private final BankTransactionClient bankTransactionClient;
    private final CodefCredentialProvider codefCredentialProvider;
    private final ObjectMapper objectMapper;
    private final ExpenseCategoryClassifier categoryClassifier;
    private final TransactionSourceKeyGenerator sourceKeyGenerator;
    private final AssetSyncMapper assetSyncMapper;
    private final Clock clock;
    private final ConsumptionInsightCache consumptionInsightCache;

    public BankTransactionCollectionService(
            BankTransactionClient bankTransactionClient,
            CodefCredentialProvider codefCredentialProvider,
            ObjectMapper objectMapper,
            ExpenseCategoryClassifier categoryClassifier,
            TransactionSourceKeyGenerator sourceKeyGenerator,
            AssetSyncMapper assetSyncMapper,
            Clock clock,
            ConsumptionInsightCache consumptionInsightCache
    ) {
        this.bankTransactionClient = bankTransactionClient;
        this.codefCredentialProvider = codefCredentialProvider;
        this.objectMapper = objectMapper;
        this.categoryClassifier = categoryClassifier;
        this.sourceKeyGenerator = sourceKeyGenerator;
        this.assetSyncMapper = assetSyncMapper;
        this.clock = clock;
        this.consumptionInsightCache = consumptionInsightCache;
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
        return collectWithStats(
                userId,
                accountId,
                accountNumber,
                institution,
                startDate,
                endDate
        ).total();
    }

    public AssetSyncDto.SyncStats collectWithStats(
            long userId,
            long accountId,
            String accountNumber,
            Institution institution,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateCollectionRequest(accountNumber, institution, startDate, endDate);
        String normalizedAccountNumber = AssetIdentifierNormalizer.normalize(accountNumber, "account number");

        long startedAt = System.nanoTime();
        long apiStartedAt = System.nanoTime();
        CodefCredential credential = codefCredentialProvider.getCredential(
                userId,
                institution.getCodefOrganizationCode()
        );
        CodefDto.Response response = bankTransactionClient.getTransactions(
                new CodefDto.BankTransactionRequest(
                        institution.getCodefOrganizationCode(),
                        credential.loginType(),
                        credential.id(),
                        credential.password(),
                        accountNumber,
                        CodefDateTime.formatDate(startDate),
                        CodefDateTime.formatDate(endDate)
                )
        );
        long apiElapsedMs = elapsedMillis(apiStartedAt);
        CodefResponseValidator.requireSuccess(response, "은행 거래내역을 가져오지 못했습니다");

        long conversionStartedAt = System.nanoTime();
        List<CodefDto.BankTransaction> transactions = objectMapper.convertValue(
                response.getData(),
                new TypeReference<List<CodefDto.BankTransaction>>() { }
        );
        long conversionElapsedMs = elapsedMillis(conversionStartedAt);

        int savedCount = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        int reusedClassificationCount = 0;
        int aiRequestCount = 0;
        long classificationStartedAt = System.nanoTime();
        List<PreparedBankTransaction> preparedTransactions = safeList(transactions).stream()
                .map(source -> prepareTransaction(
                        userId,
                        accountId,
                        normalizedAccountNumber,
                        institution,
                        source
                ))
                .toList();
        preparedTransactions = TransactionBatchDeduplicator.deduplicate(
                preparedTransactions,
                transaction -> transaction.sourceIdentity().sourceDedupKey(),
                SOURCE_TYPE
        );
        int duplicateCount = safeList(transactions).size() - preparedTransactions.size();
        Map<String, ClassificationResolution> classifications = resolveClassifications(
                userId,
                institution,
                preparedTransactions
        );
        long classificationElapsedMs = elapsedMillis(classificationStartedAt);
        long processingStartedAt = System.nanoTime();
        for (PreparedBankTransaction source : preparedTransactions) {
            TransactionMapping mapping = toTransaction(
                    source,
                    classifications.get(source.sourceIdentity().sourceDedupKey())
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
                "asset-sync bank organization=%s account=%s records=%d duplicates=%d saved=%d reusedClassification=%d "
                        + "aiRequests=%d apiMs=%d conversionMs=%d classificationMs=%d processingMs=%d totalMs=%d",
                institution.getCodefOrganizationCode(),
                accountNumber,
                safeList(transactions).size(),
                duplicateCount,
                savedCount,
                reusedClassificationCount,
                aiRequestCount,
                apiElapsedMs,
                conversionElapsedMs,
                classificationElapsedMs,
                processingElapsedMs,
                elapsedMillis(startedAt)
        ));
        return new AssetSyncDto.SyncStats(insertedCount, updatedCount);
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

    private PreparedBankTransaction prepareTransaction(
            long userId,
            long accountId,
            String normalizedAccountNumber,
            Institution institution,
            CodefDto.BankTransaction source
    ) {
        if (source == null) {
            throw new IllegalArgumentException("은행 거래내역이 비어 있습니다.");
        }
        TransactionSourceIdentity sourceIdentity = sourceKeyGenerator.identityForNormalizedBankTransaction(
                institution.getCodefOrganizationCode(),
                normalizedAccountNumber,
                source.getResTrNo()
        );
        if (source.getResAccount() != null
                && !source.getResAccount().isBlank()
                && !sourceIdentity.normalizedAssetIdentifier()
                        .equals(AssetIdentifierNormalizer.normalize(source.getResAccount(), "account number"))) {
            throw new IllegalArgumentException("요청 계좌와 응답 계좌가 일치하지 않습니다.");
        }

        long accountIn = parseNonNegativeAmount(source.getResAccountIn());
        long accountOut = parseNonNegativeAmount(source.getResAccountOut());
        String description = defaultValue(source.getResAccountDesc(), "계좌 거래");
        String normalizedKind = source.getTransactionKind() == null
                ? ""
                : source.getTransactionKind().trim().toUpperCase(Locale.ROOT);
        boolean cardPayment = AssetTransactionConstants.CARD_PAYMENT_KIND.equals(normalizedKind);
        ExpenseCategoryClassifier.Context context = cardPayment
                ? new ExpenseCategoryClassifier.Context(description, null, accountOut)
                : null;
        TransactionClassification directionClassification = cardPayment
                ? validateCardPayment(accountIn, accountOut)
                : classifyDirectionTransaction(normalizedKind, accountIn, accountOut);
        return new PreparedBankTransaction(
                userId,
                accountId,
                directionClassification,
                cardPayment,
                context,
                description,
                parseDate(source.getResTrDate()),
                parseTime(source.getResTrTime()),
                sourceIdentity
        );
    }

    private TransactionMapping toTransaction(
            PreparedBankTransaction prepared,
            ClassificationResolution resolution
    ) {
        TransactionClassification classification = prepared.cardPayment()
                ? new TransactionClassification(
                        AssetTransactionConstants.EXPENSE_TYPE,
                        resolution.result().category(),
                        prepared.directionClassification().amount(),
                        resolution.result().source(),
                        resolution.result().confidence(),
                        resolution.result().classifierVersion(),
                        resolution.reused()
                )
                : prepared.directionClassification();
        TransactionSourceIdentity sourceIdentity = prepared.sourceIdentity();
        TransactionRelationValidator.validate(sourceIdentity.sourceType(), null, prepared.accountId());
        AssetSyncDto.Transaction transaction = new AssetSyncDto.Transaction(
                prepared.userId(),
                null,
                prepared.accountId(),
                classification.type(),
                classification.category(),
                classification.amount(),
                prepared.description(),
                prepared.description(),
                null,
                null,
                prepared.transactionDate(),
                prepared.transactionTime(),
                classification.categorySource(),
                classification.confidence(),
                classification.classifierVersion(),
                sourceIdentity.sourceType(),
                sourceIdentity.sourceOrganizationCode(),
                sourceIdentity.sourceTransactionId(),
                sourceIdentity.sourceDedupKey()
        );
        return new TransactionMapping(transaction, classification.reusedClassification());
    }

    private TransactionClassification classifyDirectionTransaction(
            String transactionKind,
            long accountIn,
            long accountOut
    ) {
        String normalizedKind = transactionKind == null
                ? ""
                : transactionKind.trim().toUpperCase(Locale.ROOT);
        if (normalizedKind.isBlank()) {
            return classifyByDirection(accountIn, accountOut, true);
        }

        return switch (normalizedKind) {
            case AssetTransactionConstants.INCOME_TYPE -> classifyIncome(accountIn, accountOut);
            case AssetTransactionConstants.TRANSFER_TYPE -> classifyTransfer(accountIn, accountOut);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 은행 거래 유형입니다: " + transactionKind
            );
        };
    }

    private TransactionClassification classifyByDirection(
            long accountIn,
            long accountOut,
            boolean fallback
    ) {
        if (accountIn > 0 && accountOut == 0) {
            return incomeClassification(accountIn, fallback);
        }
        if (accountOut > 0 && accountIn == 0) {
            return transferClassification(accountOut, fallback);
        }
        throw new IllegalArgumentException("입금액과 출금액 중 하나만 양수여야 합니다.");
    }

    private TransactionClassification classifyIncome(long accountIn, long accountOut) {
        if (accountIn <= 0 || accountOut != 0) {
            throw new IllegalArgumentException("입금 거래의 금액 방향이 올바르지 않습니다.");
        }
        return incomeClassification(accountIn, false);
    }

    private TransactionClassification classifyTransfer(long accountIn, long accountOut) {
        if (accountOut <= 0 || accountIn != 0) {
            throw new IllegalArgumentException("이체 거래의 금액 방향이 올바르지 않습니다.");
        }
        return transferClassification(accountOut, false);
    }

    private TransactionClassification validateCardPayment(
            long accountIn,
            long accountOut
    ) {
        if (accountOut <= 0 || accountIn != 0) {
            throw new IllegalArgumentException("카드 결제 거래의 금액 방향이 올바르지 않습니다.");
        }

        return new TransactionClassification(
                AssetTransactionConstants.EXPENSE_TYPE,
                null,
                accountOut,
                null,
                null,
                null,
                false
        );
    }

    private Map<String, ClassificationResolution> resolveClassifications(
            long userId,
            Institution institution,
            List<PreparedBankTransaction> transactions
    ) {
        Map<String, ClassificationResolution> resolutions = new HashMap<>();
        List<ExpenseCategoryClassifier.Context> pendingContexts = new ArrayList<>();
        for (PreparedBankTransaction transaction : transactions) {
            if (!transaction.cardPayment()) {
                resolutions.put(
                        transaction.sourceIdentity().sourceDedupKey(),
                        new ClassificationResolution(
                                new ExpenseCategoryClassifier.Result(
                                        transaction.directionClassification().category(),
                                        transaction.directionClassification().categorySource(),
                                        transaction.directionClassification().confidence(),
                                        transaction.directionClassification().classifierVersion()
                                ),
                                false
                        )
                );
                continue;
            }

            Optional<ExpenseCategoryClassifier.Result> deterministicClassification =
                    categoryClassifier.classifyBeforeAi(transaction.context());
            if (deterministicClassification != null && deterministicClassification.isPresent()) {
                resolutions.put(
                        transaction.sourceIdentity().sourceDedupKey(),
                        new ClassificationResolution(deterministicClassification.get(), false)
                );
                continue;
            }

            AssetSyncDto.ExistingClassification existing = assetSyncMapper.findExistingClassification(
                    userId,
                    transaction.sourceIdentity().sourceType(),
                    transaction.sourceIdentity().sourceOrganizationCode(),
                    transaction.sourceIdentity().sourceDedupKey()
            );
            if (isReusable(existing)) {
                resolutions.put(
                        transaction.sourceIdentity().sourceDedupKey(),
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
            } else if (!pendingContexts.contains(transaction.context())) {
                pendingContexts.add(transaction.context());
            }
        }

        List<ExpenseCategoryClassifier.Result> classified = categoryClassifier.classifyBatch(pendingContexts);
        for (PreparedBankTransaction transaction : transactions) {
            if (resolutions.containsKey(transaction.sourceIdentity().sourceDedupKey())) {
                continue;
            }
            int contextIndex = pendingContexts.indexOf(transaction.context());
            resolutions.put(
                    transaction.sourceIdentity().sourceDedupKey(),
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

    private TransactionClassification incomeClassification(long amount, boolean fallback) {
        return new TransactionClassification(
                AssetTransactionConstants.INCOME_TYPE,
                AssetTransactionConstants.INCOME_CATEGORY,
                amount,
                fallback
                        ? AssetTransactionConstants.BANK_DIRECTION_FALLBACK_SOURCE
                        : AssetTransactionConstants.BANK_DIRECTION_SOURCE,
                BigDecimal.ONE,
                fallback
                        ? AssetTransactionConstants.BANK_DIRECTION_FALLBACK_CLASSIFIER_VERSION
                        : AssetTransactionConstants.BANK_DIRECTION_CLASSIFIER_VERSION,
                false
        );
    }

    private TransactionClassification transferClassification(long amount, boolean fallback) {
        return new TransactionClassification(
                AssetTransactionConstants.TRANSFER_TYPE,
                AssetTransactionConstants.SEND_CATEGORY,
                amount,
                fallback
                        ? AssetTransactionConstants.BANK_DIRECTION_FALLBACK_SOURCE
                        : AssetTransactionConstants.BANK_DIRECTION_SOURCE,
                BigDecimal.ONE,
                fallback
                        ? AssetTransactionConstants.BANK_DIRECTION_FALLBACK_CLASSIFIER_VERSION
                        : AssetTransactionConstants.BANK_DIRECTION_CLASSIFIER_VERSION,
                false
        );
    }

    private void validateCollectionRequest(
            String accountNumber,
            Institution institution,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (institution == null
                || !AssetTransactionConstants.BANK_INSTITUTION_TYPE.equals(institution.getInstitutionType())) {
            throw new IllegalArgumentException("은행 기관만 거래내역을 수집할 수 있습니다.");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("계좌번호 값이 필요합니다.");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("은행 거래내역 조회 기간이 올바르지 않습니다.");
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
        return CodefDateTime.parseDate(value, "Bank transaction date");
    }

    private LocalTime parseTime(String value) {
        return CodefDateTime.parseTime(value, "Bank transaction time");
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

    private record PreparedBankTransaction(
            long userId,
            long accountId,
            TransactionClassification directionClassification,
            boolean cardPayment,
            ExpenseCategoryClassifier.Context context,
            String description,
            LocalDate transactionDate,
            LocalTime transactionTime,
            TransactionSourceIdentity sourceIdentity
    ) {
    }

    private record TransactionClassification(
            String type,
            String category,
            long amount,
            String categorySource,
            BigDecimal confidence,
            String classifierVersion,
            boolean reusedClassification
    ) {
    }
}
