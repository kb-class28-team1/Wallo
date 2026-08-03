package com.wallo.asset.mapper;

import com.wallo.asset.dto.AssetSyncDto;
import org.apache.ibatis.annotations.Param;

public interface AssetSyncMapper {

    int upsertAccount(@Param("connectionId") long connectionId, @Param("account") AssetSyncDto.Account account);

    Long findAccountId(@Param("connectionId") long connectionId, @Param("accountNumber") String accountNumber);

    int upsertCard(@Param("connectionId") long connectionId, @Param("card") AssetSyncDto.Card card);

    Long findCardId(@Param("connectionId") long connectionId, @Param("cardNumber") String cardNumber);

    int upsertAssetSnapshot(
            @Param("userId") long userId,
            @Param("snapshot") AssetSyncDto.AssetSnapshot snapshot
    );

    int updateTransactionByApproval(@Param("transaction") AssetSyncDto.Transaction transaction);

    int insertTransaction(@Param("transaction") AssetSyncDto.Transaction transaction);

    int upsertTransaction(@Param("transaction") AssetSyncDto.Transaction transaction);
}
