package com.wallo.asset.service;

import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.exception.InvalidDashboardRequestException;
import com.wallo.asset.mapper.ExpenseMapper;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExpenseService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final ExpenseMapper expenseMapper;

    public ExpenseService(ExpenseMapper expenseMapper) {
        this.expenseMapper = expenseMapper;
    }

    public ExpenseDto.Summary getExpenseSummary(long userId, ExpenseDto.SearchCondition condition) {
        ExpenseDto.SearchCondition normalizedCondition = normalizeCondition(condition);
        List<ExpenseDto.Transaction> transactions = expenseMapper.selectTransactions(userId, normalizedCondition);

        return new ExpenseDto.Summary(
                valueOrZero(expenseMapper.selectTotalExpense(userId, normalizedCondition)),
                valueOrZero(expenseMapper.selectTotalIncome(userId, normalizedCondition)),
                expenseMapper.selectExpenseCategoryBreakdown(userId, normalizedCondition),
                transactions,
                expenseMapper.countTransactions(userId, normalizedCondition),
                normalizedCondition.getPage(),
                normalizedCondition.getSize()
        );
    }

    private ExpenseDto.SearchCondition normalizeCondition(ExpenseDto.SearchCondition condition) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = parseOrDefault(
                condition == null ? null : condition.getStartDate(),
                today.withDayOfMonth(1)
        );
        LocalDate endDate = parseOrDefault(
                condition == null ? null : condition.getEndDate(),
                today
        );
        int page = condition == null ? DEFAULT_PAGE : condition.getPage();
        int size = condition == null || condition.getSize() == 0 ? DEFAULT_SIZE : condition.getSize();

        if (startDate.isAfter(endDate) || page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidDashboardRequestException();
        }

        try {
            return new ExpenseDto.SearchCondition(
                    startDate.toString(),
                    endDate.toString(),
                    page,
                    size,
                    Math.multiplyExact(page, size)
            );
        } catch (ArithmeticException exception) {
            throw new InvalidDashboardRequestException();
        }
    }

    private LocalDate parseOrDefault(String value, LocalDate defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new InvalidDashboardRequestException();
        }
    }

    private long valueOrZero(Long amount) {
        return amount == null ? 0L : amount;
    }
}
