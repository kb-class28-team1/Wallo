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

    BudgetDto.Plan selectApplicablePlan(
            @Param("userId") long userId,
            @Param("targetMonth") String targetMonth
    );

    BudgetDto.Plan selectPlan(
            @Param("userId") long userId,
            @Param("effectiveMonth") String effectiveMonth
    );

    List<BudgetDto.Allocation> selectPlanAllocations(
            @Param("budgetPlanId") long budgetPlanId
    );

    int upsertPlan(
            @Param("userId") long userId,
            @Param("request") BudgetDto.CategoryUpsertRequest request
    );

    int deletePlanAllocations(@Param("budgetPlanId") long budgetPlanId);

    int insertPlanAllocations(
            @Param("budgetPlanId") long budgetPlanId,
            @Param("categories") List<BudgetDto.CategoryBudgetRequest> categories
    );

    List<BudgetDto.CategoryExpense> selectCategoryExpenses(
            @Param("userId") long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    Long selectSpentAmount(
            @Param("userId") long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
}
