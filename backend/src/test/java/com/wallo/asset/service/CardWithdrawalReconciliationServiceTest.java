package com.wallo.asset.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wallo.asset.dto.TransactionReconciliationDto;
import com.wallo.asset.mapper.TransactionReconciliationMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardWithdrawalReconciliationServiceTest {

    private final TransactionReconciliationMapper reconciliationMapper =
            mock(TransactionReconciliationMapper.class);
    private final CardWithdrawalReconciliationService service =
            new CardWithdrawalReconciliationService(reconciliationMapper);

    @Test
    void matchesOneCardApprovalToOnlyOneBankWithdrawal() {
        when(reconciliationMapper.selectBankWithdrawalCandidates(7L)).thenReturn(List.of(
                candidate(1L, 38_000L, "2026-07-26", "19:30:00", "SEND"),
                candidate(2L, 38_000L, "2026-07-27", "09:00:00", "SEND")
        ));
        when(reconciliationMapper.selectCheckCardApprovalCandidates(7L)).thenReturn(List.of(
                candidate(10L, 38_000L, "2026-07-26", "19:30:00", "DELIVERY")
        ));
        when(reconciliationMapper.updateBankWithdrawalCategory(
                7L, 1L, CardWithdrawalReconciliationService.CARD_WITHDRAWAL
        )).thenReturn(1);

        int updatedCount = service.reconcile(7L);

        assertEquals(1, updatedCount);
        verify(reconciliationMapper).updateBankWithdrawalCategory(
                7L, 1L, CardWithdrawalReconciliationService.CARD_WITHDRAWAL
        );
        verify(reconciliationMapper, never()).updateBankWithdrawalCategory(
                7L, 2L, CardWithdrawalReconciliationService.CARD_WITHDRAWAL
        );
    }

    @Test
    void restoresCardWithdrawalToSendWhenMatchingApprovalDisappears() {
        when(reconciliationMapper.selectBankWithdrawalCandidates(7L)).thenReturn(List.of(
                candidate(3L, 50_000L, "2026-07-28", "14:20:00", "CARD_WITHDRAWAL")
        ));
        when(reconciliationMapper.selectCheckCardApprovalCandidates(7L)).thenReturn(List.of());
        when(reconciliationMapper.updateBankWithdrawalCategory(
                7L, 3L, CardWithdrawalReconciliationService.SEND
        )).thenReturn(1);

        int updatedCount = service.reconcile(7L);

        assertEquals(1, updatedCount);
        verify(reconciliationMapper).updateBankWithdrawalCategory(
                7L, 3L, CardWithdrawalReconciliationService.SEND
        );
    }

    @Test
    void doesNotMatchApprovalMoreThanTwoDaysAway() {
        when(reconciliationMapper.selectBankWithdrawalCandidates(7L)).thenReturn(List.of(
                candidate(4L, 85_000L, "2026-07-29", "12:00:00", "SEND")
        ));
        when(reconciliationMapper.selectCheckCardApprovalCandidates(7L)).thenReturn(List.of(
                candidate(11L, 85_000L, "2026-07-26", "12:00:00", "SHOPPING")
        ));

        int updatedCount = service.reconcile(7L);

        assertEquals(0, updatedCount);
        verify(reconciliationMapper, never()).updateBankWithdrawalCategory(
                7L, 4L, CardWithdrawalReconciliationService.CARD_WITHDRAWAL
        );
    }

    private TransactionReconciliationDto.Candidate candidate(
            long transactionId,
            long amount,
            String date,
            String time,
            String category
    ) {
        return new TransactionReconciliationDto.Candidate(
                transactionId,
                amount,
                LocalDate.parse(date),
                LocalTime.parse(time),
                category
        );
    }
}
