package com.wallo.asset.mapper;

import com.wallo.asset.dto.ReportDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ReportMapper {

    Long selectAnnualSalary(@Param("userId") long userId);

    ReportDto.CardSpending selectCardSpending(
            @Param("userId") long userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    List<ReportDto.CategoryExpense> selectCategoryExpenses(
            @Param("userId") long userId,
            @Param("currentStartDate") String currentStartDate,
            @Param("currentEndDate") String currentEndDate,
            @Param("previousStartDate") String previousStartDate,
            @Param("previousEndDate") String previousEndDate
    );
}
