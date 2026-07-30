package com.wallo.asset.service;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.exception.InvalidDashboardRequestException;
import com.wallo.asset.mapper.BudgetMapper;
import java.time.DateTimeException;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private final BudgetMapper budgetMapper;

    public BudgetService(BudgetMapper budgetMapper) {
        this.budgetMapper = budgetMapper;
    }

    public BudgetDto.Summary getBudgetSummary(long userId, String targetMonth) {
        YearMonth yearMonth = resolveTargetMonth(targetMonth);
        BudgetDto.Budget budget = budgetMapper.selectBudget(userId, yearMonth.toString());

        return new BudgetDto.Summary(
                yearMonth.toString(),
                budget == null ? 0L : budget.getTotalAmount(),
                valueOrZero(budgetMapper.selectSpentAmount(
                        userId,
                        yearMonth.atDay(1).toString(),
                        yearMonth.atEndOfMonth().toString()
                ))
        );
    }

    @Transactional
    public BudgetDto.Summary upsertBudget(long userId, BudgetDto.UpsertRequest request) {
        if (request == null || request.getTotalAmount() <= 0) {
            throw new InvalidDashboardRequestException();
        }

        YearMonth yearMonth = resolveTargetMonth(request.getTargetMonth());
        budgetMapper.upsertBudget(
                userId,
                new BudgetDto.UpsertRequest(yearMonth.toString(), request.getTotalAmount())
        );

        return getBudgetSummary(userId, yearMonth.toString());
    }

    private YearMonth resolveTargetMonth(String targetMonth) {
        if (targetMonth == null || targetMonth.isBlank()) {
            return YearMonth.now();
        }

        try {
            return YearMonth.parse(targetMonth);
        } catch (DateTimeException exception) {
            throw new InvalidDashboardRequestException();
        }
    }

    private long valueOrZero(Long amount) {
        return amount == null ? 0L : amount;
    }
}
