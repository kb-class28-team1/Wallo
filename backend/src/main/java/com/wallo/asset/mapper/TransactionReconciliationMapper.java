package com.wallo.asset.mapper;

import com.wallo.asset.dto.AssetSyncDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TransactionReconciliationMapper {

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
