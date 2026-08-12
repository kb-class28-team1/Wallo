package com.wallo.asset.mapper;

import com.wallo.asset.dto.BudgetDto;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface BudgetMapper {

    BudgetDto.Budget selectBudget(
            @Param("userId") long userId,
            @Param("targetMonth") String targetMonth
    );

    List<BudgetDto.Budget> selectBudgets(@Param("userId") long userId);

    int upsertBudget(
            @Param("userId") long userId,
            @Param("request") BudgetDto.UpsertRequest request
    );

    Long selectSpentAmount(
            @Param("userId") long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
