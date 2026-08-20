package com.wallo.asset.service;

import com.wallo.asset.dto.AssetAnalysisContextDto;
import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.mapper.AssetMapper;
import com.wallo.asset.mapper.ExpenseMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetAnalysisContextService {

    private final AssetMapper assetMapper;
    private final ExpenseMapper expenseMapper;
    private final Clock clock;

    @Autowired
    public AssetAnalysisContextService(
            AssetMapper assetMapper,
            ExpenseMapper expenseMapper,
            Clock clock
    ) {
        this.assetMapper = assetMapper;
        this.expenseMapper = expenseMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AssetAnalysisContextDto getContext(long userId) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        List<AssetDto.CategoryBreakdown> breakdowns = values(
                assetMapper.selectAssetCategoryBreakdown(userId)
        );

        long totalAssets = breakdowns.stream()
                .mapToLong(AssetDto.CategoryBreakdown::getAmount)
                .filter(amount -> amount > 0)
                .sum();
        long totalDebt = breakdowns.stream()
                .filter(item -> isLoan(item.getCategory()))
                .mapToLong(AssetDto.CategoryBreakdown::getAmount)
                .map(Math::abs)
                .sum();
        long netAssets = valueOrZero(assetMapper.selectTotalAssets(userId));

        ExpenseDto.MonthlyCashflow cashflow = expenseMapper.selectMonthlyCashflow(
                userId,
                currentMonth.atDay(1).toString(),
                currentMonth.atEndOfMonth().toString()
        );
        long monthlyIncome = cashflow == null ? 0L : cashflow.getMonthlyIncome();
        long monthlyExpense = cashflow == null ? 0L : cashflow.getMonthlyExpense();
        long monthlySaving = monthlyIncome - monthlyExpense;
        Double savingRatePercent = monthlyIncome == 0L
                ? null
                : monthlySaving * 100.0 / monthlyIncome;

        return new AssetAnalysisContextDto(
                totalAssets,
                totalDebt,
                netAssets,
                monthlyIncome,
                monthlyExpense,
                monthlySaving,
                savingRatePercent,
                buildComposition(breakdowns, totalAssets),
                OffsetDateTime.now(clock).toString()
        );
    }

    private List<AssetAnalysisContextDto.AssetComposition> buildComposition(
            List<AssetDto.CategoryBreakdown> breakdowns,
            long totalAssets
    ) {
        return breakdowns.stream()
                .filter(item -> item.getAmount() > 0)
                .map(item -> new AssetAnalysisContextDto.AssetComposition(
                        item.getCategory(),
                        item.getAmount(),
                        totalAssets == 0L
                                ? 0.0
                                : item.getAmount() * 100.0 / totalAssets
                ))
                .toList();
    }

    private boolean isLoan(String category) {
        return category != null && "LOAN".equals(category.trim().toUpperCase(Locale.ROOT));
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
