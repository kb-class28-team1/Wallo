package com.wallo.asset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetMapper;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
public class AssetSyncService {

    private static final Logger LOGGER = Logger.getLogger(AssetSyncService.class.getName());

    private final AssetSyncMapper assetSyncMapper;
    private final AssetMapper assetMapper;
    private final ObjectMapper objectMapper;
    private final CardApprovalCollectionService cardApprovalCollectionService;
    private final BankTransactionCollectionService bankTransactionCollectionService;
    private final ConsumptionInsightCache consumptionInsightCache;
    private final Clock clock;

    public AssetSyncService(
            AssetSyncMapper assetSyncMapper,
            AssetMapper assetMapper,
            ObjectMapper objectMapper,
            CardApprovalCollectionService cardApprovalCollectionService,
            BankTransactionCollectionService bankTransactionCollectionService,
            ConsumptionInsightCache consumptionInsightCache,
            Clock clock
    ) {
        this.assetSyncMapper = assetSyncMapper;
        this.assetMapper = assetMapper;
        this.objectMapper = objectMapper;
        this.cardApprovalCollectionService = cardApprovalCollectionService;
        this.bankTransactionCollectionService = bankTransactionCollectionService;
        this.consumptionInsightCache = consumptionInsightCache;
        this.clock = clock;
    }

    public void sync(long userId, long connectionId, Institution institution, CodefDto.Response response) {
        long startedAt = System.nanoTime();
        CodefDto.AssetData data = objectMapper.convertValue(response.getData(), CodefDto.AssetData.class);
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

        for (CodefDto.Account account : values(data.getAccounts())) {
            assetSyncMapper.upsertAccount(connectionId, new AssetSyncDto.Account(
                    account.getResAccount(), account.getResAccountDisplay(), account.getResAccountName(),
                    institution.getInstitutionType(), account.getResAccountSubtype(), amount(account.getResAccountBalance()),
                    amount(defaultValue(account.getResAccountEvalAmount(), account.getResAccountBalance())),
                    defaultValue(account.getResAccountCurrency(), "KRW"), status(account.getResAccountStatus())));
        }
        for (CodefDto.Loan loan : values(data.getLoans())) {
            assetSyncMapper.upsertAccount(connectionId, new AssetSyncDto.Account(
                    loan.getResLoanAccount(), loan.getResLoanDisplay(), loan.getResLoanName(), "LOAN", "LOAN",
                    amount(loan.getResLoanBalance()), amount(loan.getResLoanBalance()),
                    defaultValue(loan.getResLoanCurrency(), "KRW"), status(loan.getResLoanStatus())));
        }
        for (CodefDto.Card card : values(data.getCards())) {
            assetSyncMapper.upsertCard(connectionId, new AssetSyncDto.Card(
                    card.getResCardNo(), card.getResCardName(), defaultValue(card.getResCardType(), "CREDIT"),
                    status(card.getResCardState()), card.getResValidPeriod()));
        }
        long assetStageElapsedMs = elapsedMillis(startedAt);
        long transactionStageStartedAt = System.nanoTime();
        if ("CARD".equals(institution.getInstitutionType())) {
            cardApprovalCollectionService.collectInitial(userId, connectionId, institution);
        } else if ("BANK".equals(institution.getInstitutionType())) {
            collectBankTransactions(userId, connectionId, institution, data.getAccounts());
            for (CodefDto.Transaction source : values(data.getTransactions())) {
                if (!blank(source.getResLoanAccount())) {
                    syncTransaction(userId, connectionId, source);
                }
            }
        } else {
            for (CodefDto.Transaction source : values(data.getTransactions())) {
                syncTransaction(userId, connectionId, source);
            }
        }
        Long currentTotalAssets = assetMapper.selectTotalAssets(userId);
        assetSyncMapper.upsertAssetSnapshot(
                userId,
                new AssetSyncDto.AssetSnapshot(
                        currentMonth.toString(),
                        currentTotalAssets == null ? 0L : currentTotalAssets
                )
        );
        consumptionInsightCache.invalidateAfterCommit(userId, currentMonth);
        LOGGER.info(String.format(
                Locale.ROOT,
                "asset-sync-service organization=%s type=%s snapshots=%d accounts=%d loans=%d cards=%d "
                        + "assetStageMs=%d transactionStageMs=%d totalMs=%d",
                institution.getCodefOrganizationCode(),
                institution.getInstitutionType(),
                values(data.getAssetSnapshots()).size(),
                values(data.getAccounts()).size(),
                values(data.getLoans()).size(),
                values(data.getCards()).size(),
                assetStageElapsedMs,
                elapsedMillis(transactionStageStartedAt),
                elapsedMillis(startedAt)
        ));
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
            Long accountId = required(
                    assetSyncMapper.findAccountId(connectionId, account.getResAccount())
            );
            bankTransactionCollectionService.collectInitial(
                    userId,
                    accountId,
                    account.getResAccount(),
                    institution
            );
        }
    }

    private void syncTransaction(long userId, long connectionId, CodefDto.Transaction source) {
        boolean cardTransaction = !blank(source.getResCardNo());
        boolean loanTransaction = !blank(source.getResLoanAccount());
        Long cardId = cardTransaction ? required(assetSyncMapper.findCardId(connectionId, source.getResCardNo())) : null;
        String accountNumber = loanTransaction ? source.getResLoanAccount() : source.getResAccount();
        Long accountId = cardTransaction ? null : required(assetSyncMapper.findAccountId(connectionId, accountNumber));

        AssetSyncDto.Transaction transaction = new AssetSyncDto.Transaction(
                userId, cardId, accountId,
                cardTransaction || loanTransaction ? "EXPENSE" : defaultValue(source.getResAccountTrType(), "EXPENSE"),
                cardTransaction ? defaultValue(source.getResUsedCategory(), "OTHER")
                        : loanTransaction ? defaultValue(source.getResLoanPaymentCategory(), "LOAN_REPAYMENT")
                                : defaultValue(source.getResAccountTrCategory(), "OTHER"),
                amount(cardTransaction ? source.getResUsedAmount()
                        : loanTransaction ? source.getResLoanPaymentAmount() : source.getResAccountTrAmount()),
                cardTransaction ? defaultValue(source.getResUsedMerchantName(), "카드 결제")
                        : loanTransaction ? "학자금대출 상환" : defaultValue(source.getResAccountTrDesc(), "계좌 거래"),
                cardTransaction ? source.getResCardApprovalNo()
                        : loanTransaction ? source.getResLoanPaymentNo() : source.getResAccountTrNo(),
                LocalDate.parse(cardTransaction ? source.getResUsedDate()
                        : loanTransaction ? source.getResLoanPaymentDate() : source.getResAccountTrDate()),
                LocalTime.parse(cardTransaction ? source.getResUsedTime()
                        : loanTransaction ? source.getResLoanPaymentTime() : source.getResAccountTrTime()));
        if (assetSyncMapper.updateTransactionByApproval(transaction) == 0) {
            assetSyncMapper.insertTransaction(transaction);
        }
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
