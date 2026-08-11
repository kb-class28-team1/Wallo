package com.wallo.asset.mapper;

import com.wallo.asset.dto.AssetSyncDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AssetSyncMapper {

    int upsertAccount(@Param("connectionId") long connectionId, @Param("account") AssetSyncDto.Account account);

    int updateConnectionLastSyncAt(@Param("connectionId") long connectionId);

    Long findAccountId(@Param("connectionId") long connectionId, @Param("accountNumber") String accountNumber);

    int upsertCard(@Param("connectionId") long connectionId, @Param("card") AssetSyncDto.Card card);

    Long findCardId(@Param("connectionId") long connectionId, @Param("cardNumber") String cardNumber);

    int upsertAssetSnapshot(
            @Param("userId") long userId,
            @Param("snapshot") AssetSyncDto.AssetSnapshot snapshot
    );

    int upsertTransaction(@Param("transaction") AssetSyncDto.Transaction transaction);

    AssetSyncDto.ExistingClassification findExistingClassification(
            @Param("userId") long userId,
            @Param("sourceType") String sourceType,
            @Param("sourceOrganizationCode") String sourceOrganizationCode,
            @Param("sourceDedupKey") String sourceDedupKey
    );

    Long findExistingTransactionId(
            @Param("userId") long userId,
            @Param("sourceType") String sourceType,
            @Param("sourceOrganizationCode") String sourceOrganizationCode,
            @Param("sourceDedupKey") String sourceDedupKey
    );

    List<AssetSyncDto.ReconciliationCandidate> selectBankWithdrawalCandidates(
            @Param("userId") long userId
    );

    List<AssetSyncDto.ReconciliationCandidate> selectCheckCardApprovalCandidates(
            @Param("userId") long userId
    );

    int updateBankWithdrawalCategory(
            @Param("userId") long userId,
            @Param("transactionId") long transactionId,
            @Param("category") String category
    );
}
