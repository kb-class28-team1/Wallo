package com.wallo.asset.service;

import com.wallo.asset.classification.AccountGoalFundClassifier;
import com.wallo.asset.domain.AccountSubtype;
import com.wallo.asset.domain.GoalFundAvailability;
import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.GoalAssetContextDto;
import com.wallo.asset.mapper.AssetMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

    public GoalAssetContextDto.Response getGoalAssetContext(long userId) {
        List<GoalAssetContextDto.AccountRecord> records = values(
                assetMapper.selectGoalContextAccounts(userId)
        );
        List<GoalAssetContextDto.Account> accounts = new ArrayList<>();
        long readyAmount = 0L;
        long conditionalAmount = 0L;
        long riskAssetAmount = 0L;
        long excludedAmount = 0L;
        long unknownAmount = 0L;
        long debtAmount = 0L;

        for (GoalAssetContextDto.AccountRecord record : records) {
            AccountSubtype subtype = AccountGoalFundClassifier.normalize(
                    record.getAccountType(),
                    record.getAccountSubtype()
            );
            GoalFundAvailability availability = AccountGoalFundClassifier.availability(subtype);
            long amount = goalContextAmount(record);
            accounts.add(new GoalAssetContextDto.Account(
                    subtype,
                    record.getAccountSubtype(),
                    amount,
                    availability
            ));

            switch (availability) {
                case READY -> readyAmount += amount;
                case CONDITIONAL -> conditionalAmount += amount;
                case RISK_ASSET -> riskAssetAmount += amount;
                case EXCLUDED -> excludedAmount += amount;
                case UNKNOWN -> unknownAmount += amount;
            }
            if (subtype == AccountSubtype.LOAN) {
                debtAmount += amount;
            }
        }

        return new GoalAssetContextDto.Response(
                !records.isEmpty(),
                readyAmount,
                conditionalAmount,
                riskAssetAmount,
                excludedAmount,
                unknownAmount,
                debtAmount,
                accounts
        );
    }

    private long goalContextAmount(GoalAssetContextDto.AccountRecord record) {
        String accountType = record.getAccountType();
        if (accountType != null
                && AssetTransactionConstants.STOCK_INSTITUTION_TYPE.equals(
                        accountType.trim().toUpperCase(Locale.ROOT)
                )) {
            return record.getEvaluationAmount();
        }
        return record.getBalance();
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private long valueOrZero(Long amount) {
        return amount == null ? 0L : amount;
    }
}
