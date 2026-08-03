package com.wallo.asset.mapper;

import org.apache.ibatis.annotations.Param;

public interface AnnualSalaryMapper {

    int updateAnnualSalary(
            @Param("userId") long userId,
            @Param("annualSalary") long annualSalary
    );
}
