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

    List<ExpenseDto.Transaction> selectTransactions(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );

    long countTransactions(
            @Param("userId") long userId,
            @Param("condition") ExpenseDto.SearchCondition condition
    );
}
