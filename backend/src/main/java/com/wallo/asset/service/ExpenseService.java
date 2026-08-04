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

    private static final int MAX_SIZE = 100;

    private final ExpenseMapper expenseMapper;

    public ExpenseService(ExpenseMapper expenseMapper) {
        this.expenseMapper = expenseMapper;
    }

    public ExpenseDto.Summary getExpenseSummary(long userId, ExpenseDto.SearchCondition condition) {
        ExpenseDto.SearchCondition normalizedCondition = normalizeCondition(condition);
        List<ExpenseDto.Transaction> transactions = expenseMapper.selectTransactions(userId, normalizedCondition);
        long totalElements = expenseMapper.countTransactions(userId, normalizedCondition);
        int totalPages = calculateTotalPages(totalElements, normalizedCondition.getSize());

        return new ExpenseDto.Summary(
                valueOrZero(expenseMapper.selectTotalExpense(userId, normalizedCondition)),
                valueOrZero(expenseMapper.selectTotalIncome(userId, normalizedCondition)),
                expenseMapper.selectExpenseCategoryBreakdown(userId, normalizedCondition),
                expenseMapper.selectDailyBreakdown(userId, normalizedCondition),
                transactions,
                new ExpenseDto.Pagination(
                        normalizedCondition.getPage(),
                        totalPages,
                        totalElements,
                        normalizedCondition.getPage() + 1 < totalPages
                )
        );
    }

    private ExpenseDto.SearchCondition normalizeCondition(ExpenseDto.SearchCondition condition) {
        if (condition == null) {
            throw new InvalidDashboardRequestException();
        }
        LocalDate startDate = parseRequired(condition.getStartDate());
        LocalDate endDate = parseRequired(condition.getEndDate());
        int page = condition.getPage();
        int size = condition.getSize();

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

    private LocalDate parseRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDashboardRequestException();
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new InvalidDashboardRequestException();
        }
    }

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements == 0L) {
            return 0;
        }
        long totalPages = ((totalElements - 1L) / size) + 1L;
        if (totalPages > Integer.MAX_VALUE) {
            throw new InvalidDashboardRequestException();
        }
        return (int) totalPages;
    }

    private long valueOrZero(Long amount) {
        return amount == null ? 0L : amount;
    }
}
