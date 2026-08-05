package com.wallo.asset.service;

import com.wallo.asset.dto.AssetReportDto;
import com.wallo.asset.mapper.AssetReportMapper;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AssetReportService {

    private static final long RAPID_INCREASE_THRESHOLD_PERCENT = 30L;
    private static final String EARLY_MONTH_REPORT_TITLE = "소비 데이터를 모으고 있어요";
    private static final String EARLY_MONTH_REPORT_CONTENT =
            "이번 달 소비 패턴을 분석하려면 조금 더 지출 내역이 필요해요."
                    + " 데이터가 쌓이면 지출이 많은 카테고리와 지난달 대비 변화를 알려드릴게요.";
    private static final String INSUFFICIENT_DATA_REPORT_TITLE = "소비 데이터가 아직 충분하지 않아요";
    private static final String INSUFFICIENT_DATA_REPORT_CONTENT =
            "현재까지의 소비 데이터가 아직 충분하지 않아요."
                    + " 조금 더 지출 내역이 쌓이면 소비 패턴을 분석해드릴게요.";
    private static final Map<String, String> CATEGORY_LABELS = Map.ofEntries(
            Map.entry("FOOD", "식비"),
            Map.entry("CAFE", "카페"),
            Map.entry("DELIVERY", "배달"),
            Map.entry("TRANSPORT", "교통/차량"),
            Map.entry("SHOPPING", "쇼핑"),
            Map.entry("HOUSING", "주거/통신"),
            Map.entry("LIVING", "생활"),
            Map.entry("CULTURE", "문화"),
            Map.entry("HEALTH", "건강"),
            Map.entry("EDUCATION", "교육"),
            Map.entry("LOAN_REPAYMENT", "대출 상환"),
            Map.entry("ETC", "기타")
    );

    private final AssetReportMapper assetReportMapper;

    public AssetReportService(AssetReportMapper assetReportMapper) {
        this.assetReportMapper = assetReportMapper;
    }

    public AssetReportDto.Insight getConsumptionInsight(long userId) {
        return getConsumptionInsight(userId, LocalDate.now());
    }

    public AssetReportDto.TaxSettlement getTaxSettlement(long userId, Integer year) {
        return getTaxSettlement(userId, year, LocalDate.now());
    }

    AssetReportDto.TaxSettlement getTaxSettlement(
            long userId,
            Integer year,
            LocalDate today
    ) {
        int targetYear = year == null ? today.getYear() : year;

        if (targetYear < 2000 || targetYear > today.getYear()) {
            throw new CustomException(ErrorCode.INVALID_REPORT_YEAR);
        }

        Long annualSalary = assetReportMapper.selectAnnualSalary(userId);
        if (annualSalary == null || annualSalary <= 0) {
            throw new CustomException(ErrorCode.ANNUAL_SALARY_REQUIRED);
        }

        LocalDate startDate = LocalDate.of(targetYear, 1, 1);
        LocalDate endDate = targetYear == today.getYear()
                ? today
                : LocalDate.of(targetYear, 12, 31);
        AssetReportDto.CardSpending spending = assetReportMapper.selectCardSpending(
                userId,
                startDate.toString(),
                endDate.toString()
        );

        long cardSpentYtd = spending == null ? 0L : spending.getCardSpentYtd();
        long creditCardSpentYtd =
                spending == null ? 0L : spending.getCreditCardSpentYtd();
        long checkCardSpentYtd =
                spending == null ? 0L : spending.getCheckCardSpentYtd();

        return new AssetReportDto.TaxSettlement(
                annualSalary,
                annualSalary / 4,
                cardSpentYtd,
                creditCardSpentYtd,
                checkCardSpentYtd
        );
    }

    AssetReportDto.Insight getConsumptionInsight(long userId, LocalDate today) {
        if (today.getDayOfMonth() <= 3) {
            return new AssetReportDto.Insight(
                    EARLY_MONTH_REPORT_TITLE,
                    EARLY_MONTH_REPORT_CONTENT,
                    AssetReportDto.GenerationMode.RULE
            );
        }

        LocalDate currentStartDate = today.withDayOfMonth(1);
        YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
        LocalDate previousStartDate = previousMonth.atDay(1);
        LocalDate previousEndDate = previousMonth.atDay(
                Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth())
        );

        List<AssetReportDto.CategoryExpense> categoryExpenses = assetReportMapper.selectCategoryExpenses(
                userId,
                currentStartDate.toString(),
                today.toString(),
                previousStartDate.toString(),
                previousEndDate.toString()
        );

        long currentTotalExpense = values(categoryExpenses).stream()
                .mapToLong(AssetReportDto.CategoryExpense::getCurrentAmount)
                .sum();
        if (currentTotalExpense < 30_000L) {
            return new AssetReportDto.Insight(
                    INSUFFICIENT_DATA_REPORT_TITLE,
                    INSUFFICIENT_DATA_REPORT_CONTENT,
                    AssetReportDto.GenerationMode.RULE
            );
        }

        InsightCandidate selectedCandidate = null;

        for (AssetReportDto.CategoryExpense expense : values(categoryExpenses)) {
            InsightCandidate candidate = createCandidate(expense);

            if (candidate != null && candidate.isHigherPriorityThan(selectedCandidate)) {
                selectedCandidate = candidate;
            }
        }

        return selectedCandidate == null ? null : createInsight(selectedCandidate);
    }

    private InsightCandidate createCandidate(AssetReportDto.CategoryExpense expense) {
        long previousAmount = expense.getPreviousAmount();
        long currentAmount = expense.getCurrentAmount();

        if (currentAmount <= 0) {
            return null;
        }

        double increaseRate = previousAmount > 0 && currentAmount > previousAmount
                ? ((double) currentAmount - previousAmount) / previousAmount * 100
                : 0;

        return new InsightCandidate(
                expense.normalizedCategory(),
                currentAmount,
                currentAmount - previousAmount,
                increaseRate
        );
    }

    private AssetReportDto.Insight createInsight(InsightCandidate candidate) {
        String categoryLabel = CATEGORY_LABELS.getOrDefault(candidate.category, "기타");
        long roundedIncreaseRate = Math.round(candidate.increaseRate);
        String title = String.format("%s 지출이 가장 많아요", categoryLabel);
        String content = candidate.increaseRate >= RAPID_INCREASE_THRESHOLD_PERCENT
                ? String.format(
                        "이번 달은 %s 지출이 가장 많아요. 지난달 같은 기간보다 %d%% 늘었어요."
                                + " 소비 내역을 한 번 확인해 보세요.",
                        categoryLabel,
                        roundedIncreaseRate
                )
                : String.format(
                        "이번 달은 %s 지출이 가장 많아요. 소비 내역을 한 번 확인해 보세요.",
                        categoryLabel
                );

        return new AssetReportDto.Insight(
                title,
                content,
                AssetReportDto.GenerationMode.RULE
        );
    }

    private List<AssetReportDto.CategoryExpense> values(
            List<AssetReportDto.CategoryExpense> categoryExpenses
    ) {
        return categoryExpenses == null ? Collections.emptyList() : categoryExpenses;
    }

    private static class InsightCandidate {

        private final String category;
        private final long currentAmount;
        private final long increaseAmount;
        private final double increaseRate;

        private InsightCandidate(
                String category,
                long currentAmount,
                long increaseAmount,
                double increaseRate
        ) {
            this.category = category;
            this.currentAmount = currentAmount;
            this.increaseAmount = increaseAmount;
            this.increaseRate = increaseRate;
        }

        private boolean isHigherPriorityThan(InsightCandidate other) {
            if (other == null) {
                return true;
            }
            if (currentAmount != other.currentAmount) {
                return currentAmount > other.currentAmount;
            }
            if (Double.compare(increaseRate, other.increaseRate) != 0) {
                return increaseRate > other.increaseRate;
            }
            if (increaseAmount != other.increaseAmount) {
                return increaseAmount > other.increaseAmount;
            }
            return category.compareTo(other.category) < 0;
        }
    }
}
