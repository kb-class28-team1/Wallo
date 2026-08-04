package com.wallo.asset.service;

import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.mapper.AssetSyncMapper;
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

    private final AssetSyncMapper assetSyncMapper;

    public CardWithdrawalReconciliationService(
            AssetSyncMapper assetSyncMapper
    ) {
        this.assetSyncMapper = assetSyncMapper;
    }

    public int reconcile(long userId) {
        List<AssetSyncDto.ReconciliationCandidate> bankWithdrawals = sorted(
                assetSyncMapper.selectBankWithdrawalCandidates(userId)
        );
        List<AssetSyncDto.ReconciliationCandidate> cardApprovals = sorted(
                assetSyncMapper.selectCheckCardApprovalCandidates(userId)
        );
        Set<Long> matchedCardApprovalIds = new HashSet<>();

        int updatedCount = 0;
        for (AssetSyncDto.ReconciliationCandidate bankWithdrawal : bankWithdrawals) {
            AssetSyncDto.ReconciliationCandidate matchedApproval = cardApprovals.stream()
                    .filter(approval -> !matchedCardApprovalIds.contains(approval.getTransactionId()))
                    .filter(approval -> approval.getAmount() == bankWithdrawal.getAmount())
                    .filter(approval -> isWithinDateRange(bankWithdrawal, approval))
                    .min(Comparator
                            .comparingLong((AssetSyncDto.ReconciliationCandidate approval) ->
                                    dateDifference(bankWithdrawal, approval))
                            .thenComparingLong(approval -> timeDifference(bankWithdrawal, approval))
                            .thenComparingLong(AssetSyncDto.ReconciliationCandidate::getTransactionId))
                    .orElse(null);

            String expectedCategory = matchedApproval != null
                    ? CARD_WITHDRAWAL
                    : restoreUnmatchedCategory(bankWithdrawal);
            if (matchedApproval != null) {
                matchedCardApprovalIds.add(matchedApproval.getTransactionId());
            }
            if (!expectedCategory.equals(bankWithdrawal.getCategory())) {
                updatedCount += assetSyncMapper.updateBankWithdrawalCategory(
                        userId,
                        bankWithdrawal.getTransactionId(),
                        expectedCategory
                );
            }
        }
        return updatedCount;
    }

    private String restoreUnmatchedCategory(AssetSyncDto.ReconciliationCandidate bankWithdrawal) {
        return CARD_WITHDRAWAL.equals(bankWithdrawal.getCategory())
                ? SEND
                : bankWithdrawal.getCategory();
    }

    private boolean isWithinDateRange(
            AssetSyncDto.ReconciliationCandidate bankWithdrawal,
            AssetSyncDto.ReconciliationCandidate cardApproval
    ) {
        return bankWithdrawal.getDate() != null
                && cardApproval.getDate() != null
                && dateDifference(bankWithdrawal, cardApproval) <= MAX_DATE_DIFFERENCE_DAYS;
    }

    private long dateDifference(
            AssetSyncDto.ReconciliationCandidate left,
            AssetSyncDto.ReconciliationCandidate right
    ) {
        return Math.abs(ChronoUnit.DAYS.between(left.getDate(), right.getDate()));
    }

    private long timeDifference(
            AssetSyncDto.ReconciliationCandidate left,
            AssetSyncDto.ReconciliationCandidate right
    ) {
        if (left.getTime() == null || right.getTime() == null) {
            return Long.MAX_VALUE;
        }
        LocalDateTime leftDateTime = LocalDateTime.of(left.getDate(), left.getTime());
        LocalDateTime rightDateTime = LocalDateTime.of(right.getDate(), right.getTime());
        return Math.abs(Duration.between(leftDateTime, rightDateTime).toSeconds());
    }

    private List<AssetSyncDto.ReconciliationCandidate> sorted(
            List<AssetSyncDto.ReconciliationCandidate> candidates
    ) {
        if (candidates == null) {
            return Collections.emptyList();
        }
        return candidates.stream()
                .sorted(Comparator
                        .comparing(AssetSyncDto.ReconciliationCandidate::getDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AssetSyncDto.ReconciliationCandidate::getTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(AssetSyncDto.ReconciliationCandidate::getTransactionId))
                .toList();
    }
}
