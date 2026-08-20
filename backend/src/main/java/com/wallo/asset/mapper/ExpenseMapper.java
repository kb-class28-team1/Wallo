package com.wallo.asset.mapper;

import com.wallo.asset.dto.ExpenseDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ExpenseMapper {

    Long selectTotalExpense(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );

    Long selectTotalIncome(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );

    List<ExpenseDto.CategoryBreakdown> selectExpenseCategoryBreakdown(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );

    List<ExpenseDto.DailyBreakdown> selectDailyBreakdown(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );

    List<ExpenseDto.Transaction> selectTransactions(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );

    long countTransactions(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );

    int updateTransactionCategory(
            @Param("userId") long userId,
            @Param("transactionId") long transactionId,
            @Param("category") String category
    );

    List<ExpenseDto.AnalysisTransaction> selectAllExpenseTransactions(
            @Param("userId") long userId
    );

    ExpenseDto.MonthlyCashflow selectMonthlyCashflow(
            @Param("userId") long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
