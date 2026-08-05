package com.wallo.asset.service;

import com.wallo.asset.dto.ReportDto;
import com.wallo.asset.mapper.ReportMapper;
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
    private static final String DELIVERY_REPORT_CONTENT =
            "지난달 대비 식비 중 배달 앱 결제가 크게 늘었어요. 야식의 유혹을 조심하세요!";
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

    private final ReportMapper reportMapper;

    public AssetReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    public ReportDto.Insight getConsumptionInsight(long userId) {
        return getConsumptionInsight(userId, LocalDate.now());
    }

    public ReportDto.TaxSettlement getTaxSettlement(long userId, Integer year) {
        return getTaxSettlement(userId, year, LocalDate.now());
    }

    ReportDto.TaxSettlement getTaxSettlement(
            long userId,
            Integer year,
            LocalDate today
    ) {
        int targetYear = year == null ? today.getYear() : year;

        if (targetYear < 2000 || targetYear > today.getYear()) {
            throw new CustomException(ErrorCode.INVALID_REPORT_YEAR);
        }

        Long annualSalary = reportMapper.selectAnnualSalary(userId);
        if (annualSalary == null || annualSalary <= 0) {
            throw new CustomException(ErrorCode.ANNUAL_SALARY_REQUIRED);
        }

        LocalDate startDate = LocalDate.of(targetYear, 1, 1);
        LocalDate endDate = targetYear == today.getYear()
                ? today
                : LocalDate.of(targetYear, 12, 31);
        ReportDto.CardSpending spending = reportMapper.selectCardSpending(
                userId,
                startDate.toString(),
                endDate.toString()
        );

        long cardSpentYtd = spending == null ? 0L : spending.getCardSpentYtd();
        long creditCardSpentYtd =
                spending == null ? 0L : spending.getCreditCardSpentYtd();
        long checkCardSpentYtd =
                spending == null ? 0L : spending.getCheckCardSpentYtd();

        return new ReportDto.TaxSettlement(
                annualSalary,
                annualSalary / 4,
                cardSpentYtd,
                creditCardSpentYtd,
                checkCardSpentYtd
        );
    }

    ReportDto.Insight getConsumptionInsight(long userId, LocalDate today) {
        LocalDate currentStartDate = today.withDayOfMonth(1);
        YearMonth previousMonth = YearMonth.from(today).minusMonths(1);
        LocalDate previousStartDate = previousMonth.atDay(1);
        LocalDate previousEndDate = previousMonth.atDay(
                Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth())
        );

        List<ReportDto.CategoryExpense> categoryExpenses = reportMapper.selectCategoryExpenses(
                userId,
                currentStartDate.toString(),
                today.toString(),
                previousStartDate.toString(),
                previousEndDate.toString()
        );

        InsightCandidate selectedCandidate = null;

        for (ReportDto.CategoryExpense expense : values(categoryExpenses)) {
            InsightCandidate candidate = createCandidate(expense);

            if (candidate != null && candidate.isHigherPriorityThan(selectedCandidate)) {
                selectedCandidate = candidate;
            }
        }

        return selectedCandidate == null ? null : createInsight(selectedCandidate);
    }

    private InsightCandidate createCandidate(ReportDto.CategoryExpense expense) {
        long previousAmount = expense.getPreviousAmount();
        long currentAmount = expense.getCurrentAmount();

        if (previousAmount <= 0 || currentAmount <= previousAmount) {
            return null;
        }

        double increaseRate =
                ((double) currentAmount - previousAmount) / previousAmount * 100;

        if (increaseRate < RAPID_INCREASE_THRESHOLD_PERCENT) {
            return null;
        }

        return new InsightCandidate(
                expense.normalizedCategory(),
                currentAmount - previousAmount,
                increaseRate
        );
    }

    private ReportDto.Insight createInsight(InsightCandidate candidate) {
        String categoryLabel = CATEGORY_LABELS.getOrDefault(candidate.category, "기타");
        long roundedIncreaseRate = Math.round(candidate.increaseRate);
        String title = "DELIVERY".equals(candidate.category)
                ? String.format("배달비 %d%% 급증!", roundedIncreaseRate)
                : String.format("%s 지출 %d%% 급증!", categoryLabel, roundedIncreaseRate);
        String content = "DELIVERY".equals(candidate.category)
                ? DELIVERY_REPORT_CONTENT
                : String.format(
                        "지난달 대비 %s 지출이 크게 늘었어요. 소비 내역을 확인해 보세요!",
                        categoryLabel
                );

        return new ReportDto.Insight(title, content);
    }

    private List<ReportDto.CategoryExpense> values(
            List<ReportDto.CategoryExpense> categoryExpenses
    ) {
        return categoryExpenses == null ? Collections.emptyList() : categoryExpenses;
    }

    private static class InsightCandidate {

        private final String category;
        private final long increaseAmount;
        private final double increaseRate;

        private InsightCandidate(String category, long increaseAmount, double increaseRate) {
            this.category = category;
            this.increaseAmount = increaseAmount;
            this.increaseRate = increaseRate;
        }

        private boolean isHigherPriorityThan(InsightCandidate other) {
            if (other == null) {
                return true;
            }
            if (increaseAmount != other.increaseAmount) {
                return increaseAmount > other.increaseAmount;
            }
            if (Double.compare(increaseRate, other.increaseRate) != 0) {
                return increaseRate > other.increaseRate;
            }
            return category.compareTo(other.category) < 0;
        }
    }
}
