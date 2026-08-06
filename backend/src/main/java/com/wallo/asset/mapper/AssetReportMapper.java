package com.wallo.asset.mapper;

import com.wallo.asset.dto.AssetReportDto;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AssetReportMapper {

    Long selectAnnualSalary(@Param("userId") long userId);

    AssetReportDto.CardSpending selectCardSpending(
            @Param("userId") long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    List<AssetReportDto.CategoryExpense> selectCategoryExpenses(
            @Param("userId") long userId,
            @Param("currentStartDate") String currentStartDate,
            @Param("currentEndDate") String currentEndDate,
            @Param("previousStartDate") String previousStartDate,
            @Param("previousEndDate") String previousEndDate
    );
}
