package com.wallo.asset.service;

import com.wallo.asset.client.AssetReportAiClient;
import com.wallo.asset.client.AssetReportAiDto;
import com.wallo.asset.dto.AssetReportDto;
import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.mapper.AssetReportMapper;
import com.wallo.asset.mapper.BudgetMapper;
import com.wallo.chat.client.AiServerException;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

@Service
public class AssetReportService {

    private static final Logger LOGGER = Logger.getLogger(AssetReportService.class.getName());
    private static final String EARLY_MONTH_REPORT_TITLE = "소비 데이터를 모으고 있어요";
    private static final String EARLY_MONTH_REPORT_CONTENT =
            "이번 달 소비 패턴을 분석하려면 조금 더 지출 내역이 필요해요."
                    + " 데이터가 쌓이면 지출이 많은 카테고리와 지난달 대비 변화를 알려드릴게요.";
    private static final String INSUFFICIENT_DATA_REPORT_TITLE = "소비 데이터가 아직 충분하지 않아요";
    private static final String INSUFFICIENT_DATA_REPORT_CONTENT =
            "현재까지의 소비 데이터가 아직 충분하지 않아요."
                    + " 조금 더 지출 내역이 쌓이면 소비 패턴을 분석해드릴게요.";
    private static final String ETC_REPORT_TITLE = "기타 지출이 눈에 띄어요";
    private static final String ETC_REPORT_CONTENT =
            "여러 소비가 기타로 모여 있어요. 지출 내역을 한 번 확인해보세요.";
    private static final String NO_ANALYZABLE_EXPENSE_REPORT_TITLE = "분석할 소비가 없어요";
    private static final String NO_ANALYZABLE_EXPENSE_REPORT_CONTENT =
            "현재는 소비 습관을 분석할 수 있는 내역이 없어요."
                    + " 다른 소비 내역이 쌓이면 알려드릴게요.";
    private static final String AI_FALLBACK_REPORT_TITLE = "소비 리포트를 준비 중이에요";
    private static final String AI_FALLBACK_REPORT_CONTENT =
            "현재 소비 내역은 확인했지만 맞춤 분석 문구를 생성하지 못했어요."
                    + " 잠시 후 다시 시도해 주세요.";
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
    private final BudgetMapper budgetMapper;
    private final AssetReportAiClient assetReportAiClient;
    private final ConsumptionInsightCache consumptionInsightCache;
    private final Clock clock;

    public AssetReportService(
            AssetReportMapper assetReportMapper,
            AssetReportAiClient assetReportAiClient,
            BudgetMapper budgetMapper,
            ConsumptionInsightCache consumptionInsightCache,
            Clock clock
    ) {
        this.assetReportMapper = assetReportMapper;
        this.assetReportAiClient = assetReportAiClient;
        this.budgetMapper = budgetMapper;
        this.consumptionInsightCache = consumptionInsightCache;
        this.clock = clock;
    }

    public AssetReportDto.Insight getConsumptionInsight(long userId) {
        return getConsumptionInsight(userId, LocalDate.now(clock));
    }

    public AssetReportDto.TaxSettlement getTaxSettlement(long userId, Integer year) {
        return getTaxSettlement(userId, year, LocalDate.now(clock));
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
        long previousTotalExpense = values(categoryExpenses).stream()
                .mapToLong(AssetReportDto.CategoryExpense::getPreviousAmount)
                .sum();
        if (currentTotalExpense < 30_000L) {
            return new AssetReportDto.Insight(
                    INSUFFICIENT_DATA_REPORT_TITLE,
                    INSUFFICIENT_DATA_REPORT_CONTENT,
                    AssetReportDto.GenerationMode.RULE
            );
        }

        YearMonth currentMonth = YearMonth.from(today);
        ConsumptionInsightCache.Key cacheKey =
                new ConsumptionInsightCache.Key(userId, currentMonth);
        AssetReportDto.Insight cachedInsight = consumptionInsightCache.get(cacheKey);
        if (cachedInsight != null) {
            return cachedInsight;
        }

        InsightCandidate selectedCandidate = null;

        for (AssetReportDto.CategoryExpense expense : values(categoryExpenses)) {
            InsightCandidate candidate = createCandidate(expense);

            if (candidate != null && candidate.isHigherPriorityThan(selectedCandidate)) {
                selectedCandidate = candidate;
            }
        }

        if (selectedCandidate == null) {
            return new AssetReportDto.Insight(
                    NO_ANALYZABLE_EXPENSE_REPORT_TITLE,
                    NO_ANALYZABLE_EXPENSE_REPORT_CONTENT,
                    AssetReportDto.GenerationMode.RULE
            );
        }
        if ("ETC".equals(selectedCandidate.category)) {
            return new AssetReportDto.Insight(
                    ETC_REPORT_TITLE,
                    ETC_REPORT_CONTENT,
                    AssetReportDto.GenerationMode.RULE,
                    "ETC"
            );
        }

        BudgetDto.Budget budget = budgetMapper.selectBudget(userId, currentMonth.toString());
        long monthlyBudget = budget == null ? 0L : budget.getTotalAmount();

        return createInsight(
                userId,
                currentMonth,
                selectedCandidate,
                currentTotalExpense,
                previousTotalExpense,
                monthlyBudget
        );
    }

    private InsightCandidate createCandidate(AssetReportDto.CategoryExpense expense) {
        String category = expense.normalizedCategory();
        long previousAmount = expense.getPreviousAmount();
        long currentAmount = expense.getCurrentAmount();

        if (currentAmount <= 0 || "LOAN_REPAYMENT".equals(category)) {
            return null;
        }

        double increaseRate = previousAmount > 0 && currentAmount > previousAmount
                ? ((double) currentAmount - previousAmount) / previousAmount * 100
                : 0;

        return new InsightCandidate(
                category,
                previousAmount,
                currentAmount,
                currentAmount - previousAmount,
                increaseRate
        );
    }

    private AssetReportDto.Insight createInsight(
            long userId,
            YearMonth currentMonth,
            InsightCandidate candidate,
            long currentTotalExpense,
            long previousTotalExpense,
            long monthlyBudget
    ) {
        ConsumptionInsightCache.Key cacheKey =
                new ConsumptionInsightCache.Key(userId, currentMonth);
        return consumptionInsightCache.getOrGenerate(
                cacheKey,
                () -> generateInsight(
                        candidate,
                        currentTotalExpense,
                        previousTotalExpense,
                        monthlyBudget
                )
        );
    }

    private AssetReportDto.Insight generateInsight(
            InsightCandidate candidate,
            long currentTotalExpense,
            long previousTotalExpense,
            long monthlyBudget
    ) {
        String categoryLabel = CATEGORY_LABELS.getOrDefault(candidate.category, "기타");
        AssetReportAiDto.Request request = new AssetReportAiDto.Request(
                candidate.category,
                categoryLabel,
                candidate.previousAmount,
                previousTotalExpense,
                monthlyBudget,
                calculateChangeRate(candidate.currentAmount, candidate.previousAmount),
                calculateChangeRate(currentTotalExpense, previousTotalExpense),
                monthlyBudget > 0 && currentTotalExpense <= monthlyBudget
        );

        try {
            AssetReportAiDto.Response response = assetReportAiClient.generate(request);
            AssetReportDto.Insight insight = new AssetReportDto.Insight(
                    response.reportTitle(),
                    response.reportContent(),
                    AssetReportDto.GenerationMode.AI,
                    candidate.category
            );
            return insight;
        } catch (AiServerException exception) {
            LOGGER.warning("AI consumption insight failed; using fallback response. type="
                    + exception.getClass().getSimpleName());
            return new AssetReportDto.Insight(
                    AI_FALLBACK_REPORT_TITLE,
                    AI_FALLBACK_REPORT_CONTENT,
                    AssetReportDto.GenerationMode.FALLBACK,
                    candidate.category
            );
        }
    }

    private double calculateChangeRate(long currentAmount, long previousAmount) {
        if (previousAmount <= 0) {
            return 0.0;
        }

        return ((double) currentAmount - previousAmount) / previousAmount * 100;
    }

    private List<AssetReportDto.CategoryExpense> values(
            List<AssetReportDto.CategoryExpense> categoryExpenses
    ) {
        return categoryExpenses == null ? Collections.emptyList() : categoryExpenses;
    }

    private static class InsightCandidate {

        private final String category;
        private final long previousAmount;
        private final long currentAmount;
        private final long increaseAmount;
        private final double increaseRate;

        private InsightCandidate(
                String category,
                long previousAmount,
                long currentAmount,
                long increaseAmount,
                double increaseRate
        ) {
            this.category = category;
            this.previousAmount = previousAmount;
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
