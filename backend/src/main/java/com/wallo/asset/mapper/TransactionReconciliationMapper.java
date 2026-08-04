package com.wallo.asset.mapper;

import com.wallo.asset.dto.TransactionReconciliationDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TransactionReconciliationMapper {

    List<TransactionReconciliationDto.Candidate> selectBankWithdrawalCandidates(
            @Param("userId") long userId
    );

    List<TransactionReconciliationDto.Candidate> selectCheckCardApprovalCandidates(
            @Param("userId") long userId
    );

    int updateBankWithdrawalCategory(
            @Param("userId") long userId,
            @Param("transactionId") long transactionId,
            @Param("category") String category
    );
}
