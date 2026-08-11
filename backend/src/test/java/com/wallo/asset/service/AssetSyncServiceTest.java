package com.wallo.asset.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetMapper;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.dto.CodefDto;
import com.wallo.external.converter.ObjectMapperCodefAssetResponseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetSyncServiceTest {

    private final AssetSyncMapper assetSyncMapper = mock(AssetSyncMapper.class);
    private final AssetMapper assetMapper = mock(AssetMapper.class);
    private final CardApprovalCollectionService cardApprovalCollectionService =
            mock(CardApprovalCollectionService.class);
    private final BankTransactionCollectionService bankTransactionCollectionService =
            mock(BankTransactionCollectionService.class);
    private final AssetSyncService assetSyncService = new AssetSyncService(
            assetSyncMapper,
            assetMapper,
            new ObjectMapperCodefAssetResponseMapper(new ObjectMapper()),
            cardApprovalCollectionService,
            bankTransactionCollectionService,
            new TransactionSourceKeyGenerator(),
            new ConsumptionInsightCache(),
            Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    );

    @Test
    void cardInstitutionUsesApprovalCollectorAfterSavingCards() {
        Institution institution = new Institution(2L, "0311", "하나카드", "CARD", "card-logo");
        Map<String, Object> data = Map.of(
                "cards", List.of(Map.of(
                        "resCardNo", "9876-0000-0000-4321",
                        "resCardName", "하나 체크카드",
                        "resCardType", "CHECK",
                        "resCardState", "1"
                )),
                "transactions", List.of(Map.of(
                        "resCardNo", "9876-0000-0000-4321",
                        "resCardApprovalNo", "legacy-approval"
                ))
        );
        when(assetSyncMapper.upsertCard(eq(11L), any(AssetSyncDto.Card.class))).thenReturn(1);

        assetSyncService.sync(7L, 11L, institution, CodefDto.Response.success(data));

        ArgumentCaptor<AssetSyncDto.Card> cardCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.Card.class);
        verify(assetSyncMapper).upsertCard(eq(11L), cardCaptor.capture());
        assertEquals("9876000000004321", cardCaptor.getValue().getNumber());
        verify(cardApprovalCollectionService).collectInitial(7L, 11L, institution);
    }

    @Test
    void syncAccountBalancesUpsertsAccountsAndRecordsLastSync() {
        Institution institution = new Institution(1L, "0004", "Wallo Bank", "BANK", "bank-logo");
        Map<String, Object> data = Map.of(
                "accounts", List.of(Map.of(
                        "resAccount", "123456-01-789012",
                        "resAccountDisplay", "123456-**-***012",
                        "resAccountName", "Emergency fund",
                        "resAccountBalance", "7250000",
                        "resAccountEvalAmount", "7250000",
                        "resAccountCurrency", "KRW",
                        "resAccountStatus", "1",
                        "resAccountSubtype", "CHECKING"
                ))
        );

        assetSyncService.syncAccountBalances(
                11L,
                institution,
                CodefDto.Response.success(data)
        );

        ArgumentCaptor<AssetSyncDto.Account> accountCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.Account.class);
        verify(assetSyncMapper).upsertAccount(eq(11L), accountCaptor.capture());
        assertEquals("12345601789012", accountCaptor.getValue().getNumber());
        assertEquals(7_250_000L, accountCaptor.getValue().getBalance());
        assertEquals("ACTIVE", accountCaptor.getValue().getStatus());
        verify(assetSyncMapper).updateConnectionLastSyncAt(11L);
        verify(bankTransactionCollectionService, never()).collectInitial(anyLong(), anyLong(), any(), any());
        verify(cardApprovalCollectionService, never()).collectInitial(anyLong(), anyLong(), any());
    }

    @Test
    void bankInstitutionCollectsTransactionsForEachSavedAccount() {
        Institution institution = new Institution(1L, "0004", "국민은행", "BANK", "bank-logo");
        Map<String, Object> data = Map.of(
                "accounts", List.of(
                        Map.of(
                                "resAccount", "123456-01-789012",
                                "resAccountDisplay", "123456-**-***012",
                                "resAccountName", "입출금통장",
                                "resAccountBalance", "5000000",
                                "resAccountStatus", "1"
                        ),
                        Map.of(
                                "resAccount", "987654-01-321098",
                                "resAccountDisplay", "987654-**-***098",
                                "resAccountName", "저축통장",
                                "resAccountBalance", "15000000",
                                "resAccountStatus", "1"
                        )
                ),
                "assetSnapshots", List.of(
                        Map.of("snapshotMonth", "2026-07", "totalAssets", "38400000"),
                        Map.of("snapshotMonth", "2026-08", "totalAssets", "40100000")
                ),
                "transactions", List.of(Map.of(
                        "resAccount", "123456-01-789012",
                        "resAccountTrNo", "legacy-bank-transaction"
                ))
        );
        when(assetSyncMapper.findAccountId(11L, "12345601789012")).thenReturn(31L);
        when(assetSyncMapper.findAccountId(11L, "98765401321098")).thenReturn(32L);

        assetSyncService.sync(7L, 11L, institution, CodefDto.Response.success(data));

        verify(bankTransactionCollectionService).collectInitial(
                7L, 31L, "123456-01-789012", institution
        );
        verify(bankTransactionCollectionService).collectInitial(
                7L, 32L, "987654-01-321098", institution
        );
        verify(assetSyncMapper, times(2)).upsertAssetSnapshot(
                eq(7L), any(AssetSyncDto.AssetSnapshot.class)
        );
    }

    @Test
    void storesLoanAsActiveLoanAndUsesLiveTotalForCurrentSnapshot() {
        Institution institution = new Institution(1L, "0004", "국민은행", "BANK", "bank-logo");
        Map<String, Object> data = Map.of(
                "loans", List.of(Map.of(
                        "resLoanName", "일반 상환 학자금대출",
                        "resLoanAccount", "STUDENT-LOAN-2021-001",
                        "resLoanDisplay", "STUDENT-LOAN-****-001",
                        "resLoanBalance", "4800000",
                        "resLoanStatus", "1",
                        "resLoanCurrency", "KRW"
                )),
                "assetSnapshots", List.of(Map.of(
                        "snapshotMonth", "2026-08",
                        "totalAssets", "40100000"
                ))
        );
        when(assetMapper.selectTotalAssets(7L)).thenReturn(53_400_000L);

        assetSyncService.sync(7L, 11L, institution, CodefDto.Response.success(data));

        ArgumentCaptor<AssetSyncDto.Account> accountCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.Account.class);
        verify(assetSyncMapper).upsertAccount(eq(11L), accountCaptor.capture());
        AssetSyncDto.Account loan = accountCaptor.getValue();
        assertEquals("LOAN", loan.getType());
        assertEquals(4_800_000L, loan.getBalance());
        assertEquals("ACTIVE", loan.getStatus());

        ArgumentCaptor<AssetSyncDto.AssetSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.AssetSnapshot.class);
        verify(assetSyncMapper).upsertAssetSnapshot(eq(7L), snapshotCaptor.capture());
        assertEquals("2026-08", snapshotCaptor.getValue().getMonth());
        assertEquals(53_400_000L, snapshotCaptor.getValue().getTotalAssets());
    }

    @Test
    void upsertsLoanTransactionWithStableSourceIdentity() {
        Institution institution = new Institution(1L, "0004", "Wallo Bank", "BANK", "bank-logo");
        Map<String, Object> data = Map.of(
                "loans", List.of(Map.of(
                        "resLoanName", "일반 상환 학자금대출",
                        "resLoanAccount", "STUDENT-LOAN-2021-001",
                        "resLoanDisplay", "STUDENT-LOAN-****-001",
                        "resLoanBalance", "4800000",
                        "resLoanStatus", "1",
                        "resLoanCurrency", "KRW"
                )),
                "transactions", List.of(Map.of(
                        "resLoanAccount", "STUDENT-LOAN-2021-001",
                        "resLoanPaymentNo", "LOAN-202607-0001",
                        "resLoanPaymentDate", "2026-07-25",
                        "resLoanPaymentTime", "09:00:00",
                        "resLoanPaymentAmount", "150000",
                        "resLoanPaymentCategory", "LOAN_REPAYMENT"
                ))
        );
        when(assetSyncMapper.findAccountId(11L, "STUDENTLOAN2021001")).thenReturn(379L);

        assetSyncService.sync(7L, 11L, institution, CodefDto.Response.success(data));

        ArgumentCaptor<AssetSyncDto.Transaction> transactionCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.Transaction.class);
        verify(assetSyncMapper).upsertTransaction(transactionCaptor.capture());
        AssetSyncDto.Transaction transaction = transactionCaptor.getValue();
        assertEquals(379L, transaction.getAccountId());
        assertEquals("LOAN_TRANSACTION", transaction.getSourceType());
        assertEquals("0004", transaction.getSourceOrganizationCode());
        assertEquals("LOAN-202607-0001", transaction.getSourceTransactionId());
        assertEquals("LOAN-202607-0001", transaction.getApprovalNo());
        assertEquals("LOAN_REPAYMENT", transaction.getCategory());
        assertEquals("CODEF", transaction.getCategorySource());
        assertEquals(64, transaction.getSourceDedupKey().length());
    }

    @Test
    void upsertsStockTransactionWithStableSourceIdentity() {
        Institution institution = new Institution(3L, "0081", "Wallo Securities", "STOCK", "stock-logo");
        Map<String, Object> data = Map.of(
                "accounts", List.of(Map.of(
                        "resAccount", "12345678-01",
                        "resAccountDisplay", "123456**-**",
                        "resAccountName", "Investment account",
                        "resAccountBalance", "350000",
                        "resAccountEvalAmount", "14500000",
                        "resAccountCurrency", "KRW",
                        "resAccountStatus", "1",
                        "resAccountSubtype", "STOCK"
                )),
                "transactions", List.of(Map.of(
                        "resAccount", "12345678-01",
                        "resAccountTrNo", "STOCK-202607-0001",
                        "resAccountTrDate", "2026-07-24",
                        "resAccountTrTime", "10:05:00",
                        "resAccountTrType", "INCOME",
                        "resAccountTrAmount", "180000",
                        "resAccountTrDesc", "배당금",
                        "resAccountTrCategory", "INVESTMENT"
                ))
        );
        when(assetSyncMapper.findAccountId(11L, "1234567801")).thenReturn(41L);

        assetSyncService.sync(7L, 11L, institution, CodefDto.Response.success(data));

        ArgumentCaptor<AssetSyncDto.Transaction> transactionCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.Transaction.class);
        verify(assetSyncMapper).upsertTransaction(transactionCaptor.capture());
        AssetSyncDto.Transaction transaction = transactionCaptor.getValue();
        assertEquals(41L, transaction.getAccountId());
        assertEquals("STOCK_TRANSACTION", transaction.getSourceType());
        assertEquals("0081", transaction.getSourceOrganizationCode());
        assertEquals("STOCK-202607-0001", transaction.getSourceTransactionId());
        assertEquals("INCOME", transaction.getType());
        assertEquals("INVESTMENT", transaction.getCategory());
        assertEquals(64, transaction.getSourceDedupKey().length());
    }

    @Test
    void refreshesCurrentMonthSnapshotFromLiveTotal() {
        when(assetMapper.selectTotalAssets(7L)).thenReturn(12_300_000L);

        assetSyncService.refreshCurrentMonthSnapshot(7L);

        ArgumentCaptor<AssetSyncDto.AssetSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.AssetSnapshot.class);
        verify(assetSyncMapper).upsertAssetSnapshot(eq(7L), snapshotCaptor.capture());
        assertEquals("2026-08", snapshotCaptor.getValue().getMonth());
        assertEquals(12_300_000L, snapshotCaptor.getValue().getTotalAssets());
    }
}
