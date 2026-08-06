package com.wallo.asset.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AssetSyncServiceTest {

    private final AssetSyncMapper assetSyncMapper = mock(AssetSyncMapper.class);
    private final CardApprovalCollectionService cardApprovalCollectionService =
            mock(CardApprovalCollectionService.class);
    private final BankTransactionCollectionService bankTransactionCollectionService =
            mock(BankTransactionCollectionService.class);
    private final AssetSyncService assetSyncService = new AssetSyncService(
            assetSyncMapper,
            new ObjectMapper(),
            cardApprovalCollectionService,
            bankTransactionCollectionService,
            new ConsumptionInsightCache(),
            Clock.system(ZoneId.of("Asia/Seoul"))
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

        verify(assetSyncMapper).upsertCard(eq(11L), any(AssetSyncDto.Card.class));
        verify(cardApprovalCollectionService).collectInitial(7L, 11L, institution);
        verify(assetSyncMapper, never()).updateTransactionByApproval(any());
        verify(assetSyncMapper, never()).insertTransaction(any());
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
        when(assetSyncMapper.findAccountId(11L, "123456-01-789012")).thenReturn(31L);
        when(assetSyncMapper.findAccountId(11L, "987654-01-321098")).thenReturn(32L);

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
        verify(assetSyncMapper, never()).updateTransactionByApproval(any());
        verify(assetSyncMapper, never()).insertTransaction(any());
    }
}
