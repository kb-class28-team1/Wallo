package com.wallo.asset.service;

import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.mapper.AssetMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

@Service
public class AssetService {

    private static final int ASSET_TREND_MONTH_COUNT = 6;

    private final AssetMapper assetMapper;

    public AssetService(AssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    public AssetDto.Response getAssets(long userId) {
        YearMonth currentMonth = YearMonth.now();
        LocalDate currentDate = LocalDate.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        YearMonth trendStartMonth = currentMonth.minusMonths(ASSET_TREND_MONTH_COUNT - 1);

        return new AssetDto.Response(
                valueOrZero(assetMapper.selectTotalAssets(userId)),
                valueOrZero(assetMapper.selectPreviousMonthTotalAssets(userId, previousMonth.toString())),
                assetMapper.selectAccounts(userId),
                assetMapper.selectCreditCardsWithBilledAmount(
                        userId,
                        currentMonth.atDay(1).toString(),
                        currentDate.toString()
                ),
                assetMapper.selectStocks(userId),
                assetMapper.selectAssetCategoryBreakdown(userId),
                assetMapper.selectAssetTrend(userId, trendStartMonth.toString(), currentMonth.toString())
        );
    }

    private long valueOrZero(Long amount) {
        return amount == null ? 0L : amount;
    }
}
