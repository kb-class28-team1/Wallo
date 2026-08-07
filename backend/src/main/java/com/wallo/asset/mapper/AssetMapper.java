package com.wallo.asset.mapper;

import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.GoalAssetContextDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AssetMapper {

    Long selectTotalAssets(@Param("userId") long userId);

    Long selectPreviousMonthTotalAssets(
            @Param("userId") long userId,
            @Param("snapshotMonth") String snapshotMonth
    );

    List<AssetDto.Account> selectAccounts(@Param("userId") long userId);

    List<AssetDto.Card> selectCreditCardsWithBilledAmount(
            @Param("userId") long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    List<AssetDto.Stock> selectStocks(@Param("userId") long userId);

    List<AssetDto.CategoryBreakdown> selectAssetCategoryBreakdown(@Param("userId") long userId);

    List<AssetDto.AssetTrend> selectAssetTrend(
            @Param("userId") long userId,
            @Param("startMonth") String startMonth,
            @Param("endMonth") String endMonth
    );

    List<GoalAssetContextDto.AccountRecord> selectGoalContextAccounts(
            @Param("userId") long userId
    );
}
