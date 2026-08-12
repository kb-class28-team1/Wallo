package com.wallo.asset.service;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.exception.InvalidDashboardRequestException;
import com.wallo.asset.mapper.BudgetMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private static final int USAGE_RATE_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final String ETC_CATEGORY = "ETC";
    private static final List<String> DIRECT_CATEGORIES = List.of(
            "FOOD",
            "CAFE",
            "TRANSPORT",
            "SHOPPING",
            "DELIVERY",
            "HOUSING",
            "LIVING",
            "CULTURE",
            "HEALTH",
            "EDUCATION",
            "LOAN_REPAYMENT"
    );

    private final BudgetMapper budgetMapper;
    private final ConsumptionInsightCache consumptionInsightCache;
    private final Clock clock;

    public BudgetService(
            BudgetMapper budgetMapper,
            ConsumptionInsightCache consumptionInsightCache,
            Clock clock
    ) {
        this.budgetMapper = budgetMapper;
        this.consumptionInsightCache = consumptionInsightCache;
        this.clock = clock;
    }

    public BudgetDto.Summary getBudgetSummary(long userId, String targetMonth) {
        YearMonth yearMonth = resolveTargetMonth(targetMonth);
        BudgetDto.Plan plan = budgetMapper.selectApplicablePlan(userId, yearMonth.toString());
        long totalAmount = plan == null
                ? legacyBudgetAmount(userId, yearMonth)
                : plan.getTotalAmount();

        return new BudgetDto.Summary(
                yearMonth.toString(),
                totalAmount,
                valueOrZero(budgetMapper.selectSpentAmount(
                        userId,
                        yearMonth.atDay(1).toString(),
                        yearMonth.atEndOfMonth().toString()
                ))
        );
    }

    public BudgetDto.CategorySummary getCategoryBudgetSummary(long userId, String targetMonth) {
        YearMonth yearMonth = resolveTargetMonth(targetMonth);
        BudgetDto.Plan plan = budgetMapper.selectApplicablePlan(userId, yearMonth.toString());
        long totalAmount = plan == null
                ? legacyBudgetAmount(userId, yearMonth)
                : plan.getTotalAmount();
        Map<String, Long> budgetByCategory = plan == null
                ? Collections.emptyMap()
                : allocationMap(budgetMapper.selectPlanAllocations(plan.getBudgetPlanId()));
        Map<String, Long> spentByCategory = expenseMap(
                budgetMapper.selectCategoryExpenses(
                        userId,
                        yearMonth.atDay(1).toString(),
                        yearMonth.atEndOfMonth().toString()
                )
        );

        List<BudgetDto.Category> categories = new ArrayList<>();
        long allocatedAmount = 0L;
        for (String category : DIRECT_CATEGORIES) {
            long budgetAmount = budgetByCategory.getOrDefault(category, 0L);
            allocatedAmount = Math.addExact(allocatedAmount, budgetAmount);
            categories.add(createCategorySummary(
                    category,
                    budgetAmount,
                    spentByCategory.getOrDefault(category, 0L)
            ));
        }

        long unallocatedAmount = Math.subtractExact(totalAmount, allocatedAmount);
        categories.add(createCategorySummary(
                ETC_CATEGORY,
                unallocatedAmount,
                spentByCategory.getOrDefault(ETC_CATEGORY, 0L)
        ));

        long spentAmount = valueOrZero(budgetMapper.selectSpentAmount(
                userId,
                yearMonth.atDay(1).toString(),
                yearMonth.atEndOfMonth().toString()
        ));

        return new BudgetDto.CategorySummary(
                yearMonth.toString(),
                totalAmount,
                allocatedAmount,
                unallocatedAmount,
                spentAmount,
                totalAmount - spentAmount,
                usageRate(totalAmount, spentAmount),
                spentAmount > totalAmount,
                categories
        );
    }

    @Transactional
    public BudgetDto.Summary upsertBudget(long userId, BudgetDto.UpsertRequest request) {
        if (request == null || request.getTotalAmount() <= 0) {
            throw new InvalidDashboardRequestException();
        }

        YearMonth yearMonth = resolveTargetMonth(request.getTargetMonth());
        BudgetDto.Plan applicablePlan = budgetMapper.selectApplicablePlan(
                userId,
                yearMonth.toString()
        );
        if (applicablePlan != null) {
            budgetMapper.upsertPlan(
                    userId,
                    createPlanWithUpdatedTotal(
                            yearMonth,
                            request.getTotalAmount(),
                            budgetMapper.selectPlanAllocations(applicablePlan.getBudgetPlanId())
                    )
            );
        }
        budgetMapper.upsertBudget(
                userId,
                new BudgetDto.UpsertRequest(yearMonth.toString(), request.getTotalAmount())
        );

        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        if (currentMonth.equals(yearMonth)) {
            consumptionInsightCache.invalidateAfterCommit(userId, currentMonth);
        }

        return getBudgetSummary(userId, yearMonth.toString());
    }

    private BudgetDto.CategoryUpsertRequest createPlanWithUpdatedTotal(
            YearMonth yearMonth,
            long totalAmount,
            List<BudgetDto.Allocation> allocations
    ) {
        Map<String, Long> budgetByCategory = allocationMap(allocations);
        List<BudgetDto.CategoryBudgetRequest> categories = new ArrayList<>();
        long allocatedAmount = 0L;
        for (String category : DIRECT_CATEGORIES) {
            long budgetAmount = budgetByCategory.getOrDefault(category, 0L);
            try {
                allocatedAmount = Math.addExact(allocatedAmount, budgetAmount);
            } catch (ArithmeticException exception) {
                throw new InvalidDashboardRequestException();
            }
            categories.add(new BudgetDto.CategoryBudgetRequest(category, budgetAmount));
        }

        if (allocatedAmount > totalAmount) {
            throw new InvalidDashboardRequestException();
        }
        return new BudgetDto.CategoryUpsertRequest(
                yearMonth.toString(),
                totalAmount,
                categories
        );
    }

    @Transactional
    public BudgetDto.CategorySummary upsertCategoryBudgets(
            long userId,
            BudgetDto.CategoryUpsertRequest request
    ) {
        BudgetDto.CategoryUpsertRequest normalizedRequest = normalizeCategoryRequest(request);
        YearMonth yearMonth = YearMonth.parse(normalizedRequest.getTargetMonth());

        budgetMapper.upsertPlan(userId, normalizedRequest);
        BudgetDto.Plan plan = budgetMapper.selectPlan(userId, normalizedRequest.getTargetMonth());
        if (plan == null) {
            throw new IllegalStateException("Budget plan could not be loaded after save.");
        }

        budgetMapper.deletePlanAllocations(plan.getBudgetPlanId());
        budgetMapper.insertPlanAllocations(
                plan.getBudgetPlanId(),
                normalizedRequest.getCategoryBudgets()
        );

        // Keep the legacy aggregate table synchronized for the existing API and AI context.
        budgetMapper.upsertBudget(
                userId,
                new BudgetDto.UpsertRequest(
                        normalizedRequest.getTargetMonth(),
                        normalizedRequest.getTotalAmount()
                )
        );

        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        if (currentMonth.equals(yearMonth)) {
            consumptionInsightCache.invalidateAfterCommit(userId, currentMonth);
        }

        return getCategoryBudgetSummary(userId, normalizedRequest.getTargetMonth());
    }

    private YearMonth resolveTargetMonth(String targetMonth) {
        if (targetMonth == null || targetMonth.isBlank()) {
            return YearMonth.from(LocalDate.now(clock));
        }

        try {
            return YearMonth.parse(targetMonth);
        } catch (DateTimeException exception) {
            throw new InvalidDashboardRequestException();
        }
    }

    private BudgetDto.CategoryUpsertRequest normalizeCategoryRequest(
            BudgetDto.CategoryUpsertRequest request
    ) {
        if (request == null || request.getTotalAmount() <= 0) {
            throw new InvalidDashboardRequestException();
        }

        YearMonth targetMonth = resolveTargetMonth(request.getTargetMonth());
        YearMonth currentMonth = YearMonth.from(LocalDate.now(clock));
        if (!currentMonth.equals(targetMonth)) {
            throw new InvalidDashboardRequestException();
        }

        Map<String, Long> requestedAmounts = new HashMap<>();
        if (request.getCategoryBudgets() != null) {
            for (BudgetDto.CategoryBudgetRequest item : request.getCategoryBudgets()) {
                if (item == null || item.getCategory() == null || item.getCategory().isBlank()) {
                    throw new InvalidDashboardRequestException();
                }

                String category = item.getCategory().trim().toUpperCase(Locale.ROOT);
                if (!DIRECT_CATEGORIES.contains(category)
                        || item.getBudgetAmount() < 0
                        || requestedAmounts.containsKey(category)) {
                    throw new InvalidDashboardRequestException();
                }
                requestedAmounts.put(category, item.getBudgetAmount());
            }
        }

        List<BudgetDto.CategoryBudgetRequest> normalizedCategories = new ArrayList<>();
        long allocatedAmount = 0L;
        for (String category : DIRECT_CATEGORIES) {
            long budgetAmount = requestedAmounts.getOrDefault(category, 0L);
            try {
                allocatedAmount = Math.addExact(allocatedAmount, budgetAmount);
            } catch (ArithmeticException exception) {
                throw new InvalidDashboardRequestException();
            }
            normalizedCategories.add(
                    new BudgetDto.CategoryBudgetRequest(category, budgetAmount)
            );
        }

        if (allocatedAmount > request.getTotalAmount()) {
            throw new InvalidDashboardRequestException();
        }

        return new BudgetDto.CategoryUpsertRequest(
                targetMonth.toString(),
                request.getTotalAmount(),
                normalizedCategories
        );
    }

    private long legacyBudgetAmount(long userId, YearMonth yearMonth) {
        BudgetDto.Budget budget = budgetMapper.selectBudget(userId, yearMonth.toString());
        return budget == null ? 0L : budget.getTotalAmount();
    }

    private Map<String, Long> allocationMap(List<BudgetDto.Allocation> allocations) {
        Map<String, Long> result = new HashMap<>();
        for (BudgetDto.Allocation allocation : values(allocations)) {
            String category = normalizeDirectCategory(allocation.getCategory());
            if (category != null) {
                result.put(category, allocation.getBudgetAmount());
            }
        }
        return result;
    }

    private Map<String, Long> expenseMap(List<BudgetDto.CategoryExpense> expenses) {
        Map<String, Long> result = new HashMap<>();
        for (BudgetDto.CategoryExpense expense : values(expenses)) {
            String category = normalizeExpenseCategory(expense.getCategory());
            result.merge(category, expense.getSpentAmount(), Long::sum);
        }
        return result;
    }

    private BudgetDto.Category createCategorySummary(
            String category,
            long budgetAmount,
            long spentAmount
    ) {
        return new BudgetDto.Category(
                category,
                budgetAmount,
                spentAmount,
                budgetAmount - spentAmount,
                usageRate(budgetAmount, spentAmount),
                spentAmount > budgetAmount
        );
    }

    private BigDecimal usageRate(long budgetAmount, long spentAmount) {
        if (budgetAmount <= 0) {
            return spentAmount == 0 ? BigDecimal.ZERO.setScale(USAGE_RATE_SCALE) : null;
        }
        return BigDecimal.valueOf(spentAmount)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(budgetAmount), USAGE_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeDirectCategory(String category) {
        if (category == null) {
            return null;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        return DIRECT_CATEGORIES.contains(normalized) ? normalized : null;
    }

    private String normalizeExpenseCategory(String category) {
        String normalized = category == null
                ? ETC_CATEGORY
                : category.trim().toUpperCase(Locale.ROOT);
        return DIRECT_CATEGORIES.contains(normalized) ? normalized : ETC_CATEGORY;
    }

    private <T> List<T> values(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private long valueOrZero(Long amount) {
        return amount == null ? 0L : amount;
    }
}
