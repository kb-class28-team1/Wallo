package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallo.asset.domain.Institution;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetSyncMapper;
import com.wallo.external.client.BankTransactionClient;
import com.wallo.external.dto.CodefDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BankTransactionCollectionServiceTest {

    private final BankTransactionClient bankTransactionClient = mock(BankTransactionClient.class);
    private final AssetSyncMapper assetSyncMapper = mock(AssetSyncMapper.class);
    private BankTransactionCollectionService service;
    private Institution institution;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-03T00:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new BankTransactionCollectionService(
                bankTransactionClient,
                new ObjectMapper(),
                new TransactionSourceKeyGenerator(),
                assetSyncMapper,
                clock
        );
        institution = new Institution(1L, "0004", "국민은행", "BANK", "bank-logo");
    }

    @Test
    void collectsIncomeAndOutgoingTransferTransactions() {
        when(bankTransactionClient.getTransactions(any())).thenReturn(CodefDto.Response.success(List.of(
                transaction("BANK-1", "3000000", "0", "월급_7월"),
                transaction("BANK-2", "0", "50000", "김철수")
        )));

        int collectedCount = service.collect(
                7L,
                31L,
                "123456-01-789012",
                institution,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        );

        assertEquals(2, collectedCount);

        ArgumentCaptor<CodefDto.BankTransactionRequest> requestCaptor =
                ArgumentCaptor.forClass(CodefDto.BankTransactionRequest.class);
        verify(bankTransactionClient).getTransactions(requestCaptor.capture());
        assertEquals("123456-01-789012", requestCaptor.getValue().getAccount());
        assertEquals("20260701", requestCaptor.getValue().getStartDate());
        assertEquals("20260731", requestCaptor.getValue().getEndDate());

        ArgumentCaptor<AssetSyncDto.Transaction> transactionCaptor =
                ArgumentCaptor.forClass(AssetSyncDto.Transaction.class);
        verify(assetSyncMapper, org.mockito.Mockito.times(2))
                .upsertTransaction(transactionCaptor.capture());
        List<AssetSyncDto.Transaction> savedTransactions = transactionCaptor.getAllValues();

        assertEquals("INCOME", savedTransactions.get(0).getType());
        assertEquals("INCOME", savedTransactions.get(0).getCategory());
        assertEquals(3_000_000L, savedTransactions.get(0).getAmount());
        assertEquals("TRANSFER", savedTransactions.get(1).getType());
        assertEquals("SEND", savedTransactions.get(1).getCategory());
        assertEquals(50_000L, savedTransactions.get(1).getAmount());
        assertEquals("BANK_TRANSACTION", savedTransactions.get(1).getSourceType());
        assertEquals("BANK-2", savedTransactions.get(1).getSourceTransactionId());
        assertNull(savedTransactions.get(1).getApprovalNo());
        assertEquals(64, savedTransactions.get(1).getSourceDedupKey().length());
    }

    @Test
    void initialCollectionUsesPreviousThreeMonths() {
        when(bankTransactionClient.getTransactions(any())).thenReturn(
                CodefDto.Response.success(List.of())
        );

        service.collectInitial(
                7L,
                31L,
                "123456-01-789012",
                institution
        );

        ArgumentCaptor<CodefDto.BankTransactionRequest> requestCaptor =
                ArgumentCaptor.forClass(CodefDto.BankTransactionRequest.class);
        verify(bankTransactionClient).getTransactions(requestCaptor.capture());
        assertEquals("20260503", requestCaptor.getValue().getStartDate());
        assertEquals("20260803", requestCaptor.getValue().getEndDate());
    }

    @Test
    void invalidDirectionDoesNotWriteTransaction() {
        when(bankTransactionClient.getTransactions(any())).thenReturn(CodefDto.Response.success(List.of(
                transaction("BANK-1", "1000", "1000", "잘못된 거래")
        )));

        assertThrows(IllegalArgumentException.class, () -> service.collect(
                7L,
                31L,
                "123456-01-789012",
                institution,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        ));
        verify(assetSyncMapper, never()).upsertTransaction(any());
    }

    @Test
    void failedCodefResponseDoesNotWriteTransaction() {
        when(bankTransactionClient.getTransactions(any())).thenReturn(
                CodefDto.Response.failure("CF-99999", "Mock API 호출 실패", "")
        );

        assertThrows(IllegalStateException.class, () -> service.collect(
                7L,
                31L,
                "123456-01-789012",
                institution,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        ));
        verify(assetSyncMapper, never()).upsertTransaction(any());
    }

    private CodefDto.BankTransaction transaction(
            String transactionId,
            String accountIn,
            String accountOut,
            String description
    ) {
        CodefDto.BankTransaction transaction = new CodefDto.BankTransaction();
        transaction.setResAccount("123456-01-789012");
        transaction.setResTrNo(transactionId);
        transaction.setResTrDate("20260728");
        transaction.setResTrTime("142000");
        transaction.setResAccountIn(accountIn);
        transaction.setResAccountOut(accountOut);
        transaction.setResAccountDesc(description);
        return transaction;
    }
}
