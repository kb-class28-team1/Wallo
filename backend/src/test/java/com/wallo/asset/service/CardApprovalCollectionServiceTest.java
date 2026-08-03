package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.classification.ExpenseCategoryClassifier;
import com.wallo.asset.classification.MerchantKeywordCategoryRule;
import com.wallo.asset.classification.MerchantSectorCategoryRule;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.client.CardApprovalClient;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CardApprovalCollectionServiceTest {

    private final CardApprovalClient cardApprovalClient = mock(CardApprovalClient.class);
    private final AssetSyncMapper assetSyncMapper = mock(AssetSyncMapper.class);
    private CardApprovalCollectionService service;
    private Institution institution;

    @BeforeEach
    void setUp() {
        ExpenseCategoryClassifier classifier = new ExpenseCategoryClassifier(List.of(
                new MerchantSectorCategoryRule(),
                new MerchantKeywordCategoryRule()
        ));
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-03T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new CardApprovalCollectionService(
                cardApprovalClient,
                new ObjectMapper(),
                classifier,
                new TransactionSourceKeyGenerator(),
                assetSyncMapper,
                clock
        );
        institution = new Institution(2L, "0311", "하나카드", "CARD", "card-logo");
    }

    @Test
    void collectsClassifiesAndUpsertsCardApprovals() {
        when(cardApprovalClient.getApprovals(any())).thenReturn(CodefDto.Response.success(List.of(
                approval("9876", "10000001", "배달의민족", "요식/음료", "38000"),
                approval("9876", "10000002", "SK에너지", "주유", "50000"),
                approval("4321", "10000003", "알 수 없는 상점", "미분류", "12000")
        )));
        when(assetSyncMapper.findCardId(11L, "9876")).thenReturn(21L);
        when(assetSyncMapper.findCardId(11L, "4321")).thenReturn(22L);

        int collectedCount = service.collect(
                7L,
                11L,
                institution,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        );

        assertEquals(3, collectedCount);

        ArgumentCaptor<CodefDto.CardApprovalRequest> requestCaptor =
                ArgumentCaptor.forClass(CodefDto.CardApprovalRequest.class);
        verify(cardApprovalClient).getApprovals(requestCaptor.capture());
        assertEquals("20260701", requestCaptor.getValue().getStartDate());
        assertEquals("20260731", requestCaptor.getValue().getEndDate());

        ArgumentCaptor<AssetSyncDto.Transaction> transactionCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.Transaction.class);
        verify(assetSyncMapper, org.mockito.Mockito.times(3))
                .upsertTransaction(transactionCaptor.capture());
        List<AssetSyncDto.Transaction> transactions = transactionCaptor.getAllValues();

        assertEquals("DELIVERY", transactions.get(0).getCategory());
        assertEquals("MERCHANT_KEYWORD", transactions.get(0).getCategorySource());
        assertEquals("TRANSPORT", transactions.get(1).getCategory());
        assertEquals("ETC", transactions.get(2).getCategory());
        assertEquals("CARD_APPROVAL", transactions.get(0).getSourceType());
        assertEquals(64, transactions.get(0).getSourceDedupKey().length());
        assertEquals(38_000L, transactions.get(0).getAmount());
    }

    @Test
    void initialCollectionUsesPreviousThreeMonths() {
        when(cardApprovalClient.getApprovals(any())).thenReturn(CodefDto.Response.success(List.of()));

        service.collectInitial(7L, 11L, institution);

        ArgumentCaptor<CodefDto.CardApprovalRequest> requestCaptor =
                ArgumentCaptor.forClass(CodefDto.CardApprovalRequest.class);
        verify(cardApprovalClient).getApprovals(requestCaptor.capture());
        assertEquals("20260503", requestCaptor.getValue().getStartDate());
        assertEquals("20260803", requestCaptor.getValue().getEndDate());
    }

    @Test
    void failedCodefResponseDoesNotWriteTransactions() {
        when(cardApprovalClient.getApprovals(any())).thenReturn(
                CodefDto.Response.failure("CF-99999", "Mock API 호출 실패", "")
        );

        assertThrows(IllegalStateException.class, () -> service.collect(
                7L,
                11L,
                institution,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        ));
        verify(assetSyncMapper, never()).upsertTransaction(any());
    }

    @Test
    void invalidAmountDoesNotWriteTransaction() {
        when(cardApprovalClient.getApprovals(any())).thenReturn(CodefDto.Response.success(List.of(
                approval("9876", "10000001", "배달의민족", "요식/음료", "not-a-number")
        )));
        when(assetSyncMapper.findCardId(eq(11L), eq("9876"))).thenReturn(21L);

        assertThrows(IllegalArgumentException.class, () -> service.collect(
                7L,
                11L,
                institution,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        ));
        verify(assetSyncMapper, never()).upsertTransaction(any());
    }

    private CodefDto.CardApproval approval(
            String cardNumber,
            String approvalNo,
            String merchantName,
            String sector,
            String amount
    ) {
        CodefDto.CardApproval approval = new CodefDto.CardApproval();
        approval.setResCardNo(cardNumber);
        approval.setResApprovalNo(approvalNo);
        approval.setResMemberName(merchantName);
        approval.setResMemberSector(sector);
        approval.setResUsedAmount(amount);
        approval.setResUsedDate("20260726");
        approval.setResUsedTime("193000");
        approval.setResCardType("1");
        return approval;
    }
}
