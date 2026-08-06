package com.wallo.asset.service;

import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.mapper.AssetMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

@Service
public class AssetService {

    private static final int ASSET_TREND_MONTH_COUNT = 5;

    private final AssetMapper assetMapper;
    private final Clock clock;

    public AssetService(AssetMapper assetMapper, Clock clock) {
        this.assetMapper = assetMapper;
        this.clock = clock;
    }

    public AssetDto.Response getAssets(long userId) {
        LocalDate currentDate = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(currentDate);
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
