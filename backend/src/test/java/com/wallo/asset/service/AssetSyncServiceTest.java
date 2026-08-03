package com.wallo.asset.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.dto.CodefDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetSyncServiceTest {

    private final AssetSyncMapper assetSyncMapper = mock(AssetSyncMapper.class);
    private final CardApprovalCollectionService cardApprovalCollectionService =
            mock(CardApprovalCollectionService.class);
    private final AssetSyncService assetSyncService = new AssetSyncService(
            assetSyncMapper,
            new ObjectMapper(),
            cardApprovalCollectionService
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
}
