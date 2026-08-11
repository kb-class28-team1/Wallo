package com.wallo.asset.service;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetMapper;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.dto.CodefDto;
import com.wallo.external.converter.CodefAssetResponseMapper;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
public class AssetSyncService {

    private static final String CARD_APPROVAL_SOURCE_TYPE = "CARD_APPROVAL";
    private static final String LOAN_TRANSACTION_SOURCE_TYPE = "LOAN_TRANSACTION";
    private static final String STOCK_TRANSACTION_SOURCE_TYPE = "STOCK_TRANSACTION";
    private static final String CODEF_CATEGORY_SOURCE = "CODEF";
    private static final String CODEF_CLASSIFIER_VERSION = "codef-v1";
    private static final Logger LOGGER = Logger.getLogger(AssetSyncService.class.getName());

    private final AssetSyncMapper assetSyncMapper;
    private final AssetMapper assetMapper;
    private final CodefAssetResponseMapper codefAssetResponseMapper;
    private final CardApprovalCollectionService cardApprovalCollectionService;
    private final BankTransactionCollectionService bankTransactionCollectionService;
    private final TransactionSourceKeyGenerator sourceKeyGenerator;
    private final ConsumptionInsightCache consumptionInsightCache;
    private final Clock clock;

    public AssetSyncService(
            AssetSyncMapper assetSyncMapper,
            AssetMapper assetMapper,
            CodefAssetResponseMapper codefAssetResponseMapper,
            CardApprovalCollectionService cardApprovalCollectionService,
            BankTransactionCollectionService bankTransactionCollectionService,
            TransactionSourceKeyGenerator sourceKeyGenerator,
            ConsumptionInsightCache consumptionInsightCache,
            Clock clock
    ) {
        this.assetSyncMapper = assetSyncMapper;
        this.assetMapper = assetMapper;
        this.codefAssetResponseMapper = codefAssetResponseMapper;
        this.cardApprovalCollectionService = cardApprovalCollectionService;
        this.bankTransactionCollectionService = bankTransactionCollectionService;
        this.sourceKeyGenerator = sourceKeyGenerator;
        this.consumptionInsightCache = consumptionInsightCache;
        this.clock = clock;
    }

    public void sync(long userId, long connectionId, Institution institution, CodefDto.Response response) {
        long startedAt = System.nanoTime();
        CodefDto.AssetData data = codefAssetResponseMapper.toAssetData(response);
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));

        for (CodefDto.AssetSnapshot snapshot : values(data.getAssetSnapshots())) {
            String snapshotMonth = snapshotMonth(snapshot.getSnapshotMonth());
            if (currentMonth.toString().equals(snapshotMonth)) {
                continue;
            }
            assetSyncMapper.upsertAssetSnapshot(userId, new AssetSyncDto.AssetSnapshot(
                    snapshotMonth, amount(snapshot.getTotalAssets())
            ));
        }

        upsertAccounts(connectionId, institution, data);
        for (CodefDto.Card card : values(data.getCards())) {
            assetSyncMapper.upsertCard(connectionId, new AssetSyncDto.Card(
                    AssetIdentifierNormalizer.normalize(card.getResCardNo(), "card number"),
                    card.getResCardName(), defaultValue(card.getResCardType(), "CREDIT"),
                    status(card.getResCardState()), card.getResValidPeriod()));
        }
        long assetStageElapsedMs = elapsedMillis(startedAt);
        long transactionStageStartedAt = System.nanoTime();
        int transactionDuplicateCount = 0;
        if ("CARD".equals(institution.getInstitutionType())) {
            cardApprovalCollectionService.collectInitial(userId, connectionId, institution);
        } else if ("BANK".equals(institution.getInstitutionType())) {
            collectBankTransactions(userId, connectionId, institution, data.getAccounts());
            List<CodefDto.Transaction> loanTransactions = values(data.getTransactions()).stream()
                    .filter(source -> !blank(source.getResLoanAccount()))
                    .toList();
            List<CodefDto.Transaction> uniqueLoanTransactions = deduplicateAssetTransactions(
                    loanTransactions,
                    institution
            );
            transactionDuplicateCount = loanTransactions.size() - uniqueLoanTransactions.size();
            for (CodefDto.Transaction source : uniqueLoanTransactions) {
                if (!blank(source.getResLoanAccount())) {
                    syncTransaction(userId, connectionId, institution, source);
                }
            }
        } else {
            List<CodefDto.Transaction> assetTransactions = values(data.getTransactions());
            List<CodefDto.Transaction> uniqueAssetTransactions = deduplicateAssetTransactions(
                    assetTransactions,
                    institution
            );
            transactionDuplicateCount = assetTransactions.size() - uniqueAssetTransactions.size();
            for (CodefDto.Transaction source : uniqueAssetTransactions) {
                syncTransaction(userId, connectionId, institution, source);
            }
        }
        upsertCurrentMonthSnapshot(userId, currentMonth);
        consumptionInsightCache.invalidateAfterCommit(userId, currentMonth);
        assetSyncMapper.updateConnectionLastSyncAt(connectionId);
        LOGGER.info(String.format(
                Locale.ROOT,
                "asset-sync-service organization=%s type=%s snapshots=%d accounts=%d loans=%d cards=%d "
                        + "transactionDuplicates=%d "
                        + "assetStageMs=%d transactionStageMs=%d totalMs=%d",
                institution.getCodefOrganizationCode(),
                institution.getInstitutionType(),
                values(data.getAssetSnapshots()).size(),
                values(data.getAccounts()).size(),
                values(data.getLoans()).size(),
                values(data.getCards()).size(),
                transactionDuplicateCount,
                assetStageElapsedMs,
                elapsedMillis(transactionStageStartedAt),
                elapsedMillis(startedAt)
        ));
    }

    /** 선택된 목표 계좌의 잔액만 최신 Codef 응답으로 갱신한다. */
    public void syncAccountBalances(
            long connectionId,
            Institution institution,
            CodefDto.Response response
    ) {
        if (response == null || response.getData() == null) {
            return;
        }

        CodefDto.AssetData data = codefAssetResponseMapper.toAssetData(response);
        upsertAccounts(connectionId, institution, data);
        assetSyncMapper.updateConnectionLastSyncAt(connectionId);
    }

    public void refreshCurrentMonthSnapshot(long userId) {
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        upsertCurrentMonthSnapshot(userId, currentMonth);
    }

    private void upsertCurrentMonthSnapshot(long userId, YearMonth currentMonth) {
        Long currentTotalAssets = assetMapper.selectTotalAssets(userId);
        assetSyncMapper.upsertAssetSnapshot(
                userId,
                new AssetSyncDto.AssetSnapshot(
                        currentMonth.toString(),
                        currentTotalAssets == null ? 0L : currentTotalAssets
                )
        );
    }

    private void upsertAccounts(
            long connectionId,
            Institution institution,
            CodefDto.AssetData data
    ) {
        for (CodefDto.Account account : values(data.getAccounts())) {
            String accountNumber = AssetIdentifierNormalizer.normalize(account.getResAccount(), "account number");
            assetSyncMapper.upsertAccount(connectionId, new AssetSyncDto.Account(
                    accountNumber, account.getResAccountDisplay(), account.getResAccountName(),
                    institution.getInstitutionType(), account.getResAccountSubtype(), amount(account.getResAccountBalance()),
                    amount(defaultValue(account.getResAccountEvalAmount(), account.getResAccountBalance())),
                    defaultValue(account.getResAccountCurrency(), "KRW"), status(account.getResAccountStatus())));
        }
        for (CodefDto.Loan loan : values(data.getLoans())) {
            String accountNumber = AssetIdentifierNormalizer.normalize(loan.getResLoanAccount(), "loan account number");
            assetSyncMapper.upsertAccount(connectionId, new AssetSyncDto.Account(
                    accountNumber, loan.getResLoanDisplay(), loan.getResLoanName(), "LOAN", "LOAN",
                    amount(loan.getResLoanBalance()), amount(loan.getResLoanBalance()),
                    defaultValue(loan.getResLoanCurrency(), "KRW"), status(loan.getResLoanStatus())));
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void collectBankTransactions(
            long userId,
            long connectionId,
            Institution institution,
            List<CodefDto.Account> accounts
    ) {
        for (CodefDto.Account account : values(accounts)) {
            String accountNumber = AssetIdentifierNormalizer.normalize(account.getResAccount(), "account number");
            Long accountId = required(
                    assetSyncMapper.findAccountId(connectionId, accountNumber)
            );
            bankTransactionCollectionService.collectInitial(
                    userId,
                    accountId,
                    account.getResAccount(),
                    institution
            );
        }
    }

    private void syncTransaction(
            long userId,
            long connectionId,
            Institution institution,
            CodefDto.Transaction source
    ) {
        boolean cardTransaction = !blank(source.getResCardNo());
        boolean loanTransaction = !blank(source.getResLoanAccount());
        String cardNumber = cardTransaction
                ? AssetIdentifierNormalizer.normalize(source.getResCardNo(), "card number")
                : null;
        String accountNumber = cardTransaction
                ? null
                : loanTransaction
                        ? AssetIdentifierNormalizer.normalize(source.getResLoanAccount(), "loan account number")
                        : AssetIdentifierNormalizer.normalize(source.getResAccount(), "account number");
        Long cardId = cardTransaction
                ? required(assetSyncMapper.findCardId(connectionId, cardNumber))
                : null;
        Long accountId = cardTransaction
                ? null
                : required(assetSyncMapper.findAccountId(connectionId, accountNumber));
        String sourceType = sourceType(cardTransaction, loanTransaction);
        TransactionRelationValidator.validate(sourceType, cardId, accountId);
        String sourceTransactionId = sourceTransactionId(cardTransaction, loanTransaction, source);
        String sourceDedupKey = sourceKeyGenerator.forAssetTransaction(
                sourceType,
                institution.getCodefOrganizationCode(),
                cardTransaction ? cardNumber : accountNumber,
                sourceTransactionId
        );
        String merchantName = cardTransaction
                ? defaultValue(source.getResUsedMerchantName(), "카드 결제")
                : loanTransaction ? "학자금대출 상환" : defaultValue(source.getResAccountTrDesc(), "계좌 거래");
        String type = cardTransaction || loanTransaction
                ? "EXPENSE"
                : defaultValue(source.getResAccountTrType(), "EXPENSE");
        String category = cardTransaction
                ? defaultValue(source.getResUsedCategory(), "OTHER")
                : loanTransaction
                        ? defaultValue(source.getResLoanPaymentCategory(), "LOAN_REPAYMENT")
                        : defaultValue(source.getResAccountTrCategory(), "OTHER");
        long amount = amount(cardTransaction ? source.getResUsedAmount()
                : loanTransaction ? source.getResLoanPaymentAmount() : source.getResAccountTrAmount());
        LocalDate date = LocalDate.parse(cardTransaction ? source.getResUsedDate()
                : loanTransaction ? source.getResLoanPaymentDate() : source.getResAccountTrDate());
        LocalTime time = LocalTime.parse(cardTransaction ? source.getResUsedTime()
                : loanTransaction ? source.getResLoanPaymentTime() : source.getResAccountTrTime());

        AssetSyncDto.Transaction transaction = new AssetSyncDto.Transaction(
                userId,
                cardId,
                accountId,
                type,
                category,
                amount,
                merchantName,
                merchantName,
                null,
                sourceTransactionId,
                date,
                time,
                CODEF_CATEGORY_SOURCE,
                java.math.BigDecimal.ONE,
                CODEF_CLASSIFIER_VERSION,
                sourceType,
                institution.getCodefOrganizationCode(),
                sourceTransactionId,
                sourceDedupKey
        );
        assetSyncMapper.upsertTransaction(transaction);
    }

    private List<CodefDto.Transaction> deduplicateAssetTransactions(
            List<CodefDto.Transaction> transactions,
            Institution institution
    ) {
        return TransactionBatchDeduplicator.deduplicate(
                transactions,
                source -> assetTransactionSourceDedupKey(institution, source),
                this::sameAssetTransactionPayload,
                "ASSET_TRANSACTION"
        );
    }

    private String assetTransactionSourceDedupKey(
            Institution institution,
            CodefDto.Transaction source
    ) {
        if (source == null) {
            throw new IllegalArgumentException("CODEF asset transaction is required.");
        }
        boolean cardTransaction = !blank(source.getResCardNo());
        boolean loanTransaction = !blank(source.getResLoanAccount());
        String assetNumber = cardTransaction
                ? source.getResCardNo()
                : loanTransaction ? source.getResLoanAccount() : source.getResAccount();
        String sourceType = sourceType(cardTransaction, loanTransaction);
        String sourceTransactionId = sourceTransactionId(cardTransaction, loanTransaction, source);
        return sourceKeyGenerator.forAssetTransaction(
                sourceType,
                institution.getCodefOrganizationCode(),
                assetNumber,
                sourceTransactionId
        );
    }

    private boolean sameAssetTransactionPayload(
            CodefDto.Transaction left,
            CodefDto.Transaction right
    ) {
        return Objects.equals(left.getResAccount(), right.getResAccount())
                && Objects.equals(left.getResAccountTrNo(), right.getResAccountTrNo())
                && Objects.equals(left.getResAccountTrDate(), right.getResAccountTrDate())
                && Objects.equals(left.getResAccountTrTime(), right.getResAccountTrTime())
                && Objects.equals(left.getResAccountTrType(), right.getResAccountTrType())
                && Objects.equals(left.getResAccountTrAmount(), right.getResAccountTrAmount())
                && Objects.equals(left.getResAccountTrDesc(), right.getResAccountTrDesc())
                && Objects.equals(left.getResAccountTrCategory(), right.getResAccountTrCategory())
                && Objects.equals(left.getResCardNo(), right.getResCardNo())
                && Objects.equals(left.getResCardApprovalNo(), right.getResCardApprovalNo())
                && Objects.equals(left.getResUsedDate(), right.getResUsedDate())
                && Objects.equals(left.getResUsedTime(), right.getResUsedTime())
                && Objects.equals(left.getResUsedAmount(), right.getResUsedAmount())
                && Objects.equals(left.getResUsedMerchantName(), right.getResUsedMerchantName())
                && Objects.equals(left.getResUsedCategory(), right.getResUsedCategory())
                && Objects.equals(left.getResLoanAccount(), right.getResLoanAccount())
                && Objects.equals(left.getResLoanPaymentNo(), right.getResLoanPaymentNo())
                && Objects.equals(left.getResLoanPaymentDate(), right.getResLoanPaymentDate())
                && Objects.equals(left.getResLoanPaymentTime(), right.getResLoanPaymentTime())
                && Objects.equals(left.getResLoanPaymentAmount(), right.getResLoanPaymentAmount())
                && Objects.equals(left.getResLoanPaymentCategory(), right.getResLoanPaymentCategory());
    }

    private String sourceType(boolean cardTransaction, boolean loanTransaction) {
        if (cardTransaction) {
            return CARD_APPROVAL_SOURCE_TYPE;
        }
        if (loanTransaction) {
            return LOAN_TRANSACTION_SOURCE_TYPE;
        }
        return STOCK_TRANSACTION_SOURCE_TYPE;
    }

    private String sourceTransactionId(
            boolean cardTransaction,
            boolean loanTransaction,
            CodefDto.Transaction source
    ) {
        String transactionId = cardTransaction
                ? source.getResCardApprovalNo()
                : loanTransaction ? source.getResLoanPaymentNo() : source.getResAccountTrNo();
        if (blank(transactionId)) {
            throw new IllegalArgumentException("CODEF source transaction id is required.");
        }
        return transactionId.trim();
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private Long required(Long id) {
        if (id == null) throw new IllegalStateException("연동 자산을 찾을 수 없습니다.");
        return id;
    }

    private long amount(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("연동 금액 형식이 올바르지 않습니다.", exception); }
    }

    private String snapshotMonth(String value) {
        try {
            return YearMonth.parse(value).toString();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid asset snapshot month: " + value, exception);
        }
    }

    private String status(String value) {
        return "1".equals(value) || "ACTIVE".equals(value) ? "ACTIVE" : "INACTIVE";
    }

    private String defaultValue(String value, String defaultValue) {
        return blank(value) ? defaultValue : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
