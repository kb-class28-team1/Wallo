package com.wallo.asset.service;

import com.wallo.asset.dto.TransactionReconciliationDto;
import com.wallo.asset.mapper.TransactionReconciliationMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CardWithdrawalReconciliationService {

    static final String CARD_WITHDRAWAL = "CARD_WITHDRAWAL";
    static final String SEND = "SEND";
    private static final long MAX_DATE_DIFFERENCE_DAYS = 2L;

    private final TransactionReconciliationMapper reconciliationMapper;

    public CardWithdrawalReconciliationService(
            TransactionReconciliationMapper reconciliationMapper
    ) {
        this.reconciliationMapper = reconciliationMapper;
    }

    public int reconcile(long userId) {
        List<TransactionReconciliationDto.Candidate> bankWithdrawals = sorted(
                reconciliationMapper.selectBankWithdrawalCandidates(userId)
        );
        List<TransactionReconciliationDto.Candidate> cardApprovals = sorted(
                reconciliationMapper.selectCheckCardApprovalCandidates(userId)
        );
        Set<Long> matchedCardApprovalIds = new HashSet<>();

        int updatedCount = 0;
        for (TransactionReconciliationDto.Candidate bankWithdrawal : bankWithdrawals) {
            TransactionReconciliationDto.Candidate matchedApproval = cardApprovals.stream()
                    .filter(approval -> !matchedCardApprovalIds.contains(approval.getTransactionId()))
                    .filter(approval -> approval.getAmount() == bankWithdrawal.getAmount())
                    .filter(approval -> isWithinDateRange(bankWithdrawal, approval))
                    .min(Comparator
                            .comparingLong((TransactionReconciliationDto.Candidate approval) ->
                                    dateDifference(bankWithdrawal, approval))
                            .thenComparingLong(approval -> timeDifference(bankWithdrawal, approval))
                            .thenComparingLong(TransactionReconciliationDto.Candidate::getTransactionId))
                    .orElse(null);

            String expectedCategory = matchedApproval == null ? SEND : CARD_WITHDRAWAL;
            if (matchedApproval != null) {
                matchedCardApprovalIds.add(matchedApproval.getTransactionId());
            }
            if (!expectedCategory.equals(bankWithdrawal.getCategory())) {
                updatedCount += reconciliationMapper.updateBankWithdrawalCategory(
                        userId,
                        bankWithdrawal.getTransactionId(),
                        expectedCategory
                );
            }
        }
        return updatedCount;
    }

    private boolean isWithinDateRange(
            TransactionReconciliationDto.Candidate bankWithdrawal,
            TransactionReconciliationDto.Candidate cardApproval
    ) {
        return bankWithdrawal.getDate() != null
                && cardApproval.getDate() != null
                && dateDifference(bankWithdrawal, cardApproval) <= MAX_DATE_DIFFERENCE_DAYS;
    }

    private long dateDifference(
            TransactionReconciliationDto.Candidate left,
            TransactionReconciliationDto.Candidate right
    ) {
        return Math.abs(ChronoUnit.DAYS.between(left.getDate(), right.getDate()));
    }

    private long timeDifference(
            TransactionReconciliationDto.Candidate left,
            TransactionReconciliationDto.Candidate right
    ) {
        if (left.getTime() == null || right.getTime() == null) {
            return Long.MAX_VALUE;
        }
        LocalDateTime leftDateTime = LocalDateTime.of(left.getDate(), left.getTime());
        LocalDateTime rightDateTime = LocalDateTime.of(right.getDate(), right.getTime());
        return Math.abs(Duration.between(leftDateTime, rightDateTime).toSeconds());
    }

    private List<TransactionReconciliationDto.Candidate> sorted(
            List<TransactionReconciliationDto.Candidate> candidates
    ) {
        if (candidates == null) {
            return Collections.emptyList();
        }
        return candidates.stream()
                .sorted(Comparator
                        .comparing(TransactionReconciliationDto.Candidate::getDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TransactionReconciliationDto.Candidate::getTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(TransactionReconciliationDto.Candidate::getTransactionId))
                .toList();
    }
}
