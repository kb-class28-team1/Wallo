package com.wallo.asset.service;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.dto.ConsumptionAnalysisContextDto;
import com.wallo.asset.mapper.BudgetMapper;
import com.wallo.asset.mapper.ExpenseMapper;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumptionAnalysisContextService {
    private final ExpenseMapper expenseMapper;
    private final BudgetMapper budgetMapper;
    private final Clock clock;

    @Autowired
    public ConsumptionAnalysisContextService(ExpenseMapper expenseMapper, BudgetMapper budgetMapper) {
        this(expenseMapper, budgetMapper, Clock.systemDefaultZone());
    }

    ConsumptionAnalysisContextService(ExpenseMapper expenseMapper, BudgetMapper budgetMapper, Clock clock) {
        this.expenseMapper = expenseMapper;
        this.budgetMapper = budgetMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConsumptionAnalysisContextDto getContext(long userId) {
        String targetMonth = YearMonth.now(clock).toString();
        BudgetDto.Budget budget = budgetMapper.selectBudget(userId, targetMonth);
        ConsumptionAnalysisContextDto.Budget budgetContext = budget == null
                ? null
                : new ConsumptionAnalysisContextDto.Budget(targetMonth, budget.getTotalAmount());
        return new ConsumptionAnalysisContextDto(
                expenseMapper.selectAllExpenseTransactions(userId),
                budgetContext
        );
    }
}
