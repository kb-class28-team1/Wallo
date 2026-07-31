package com.wallo.asset.service;

import com.wallo.asset.dto.AnnualSalaryDto;
import com.wallo.asset.mapper.AnnualSalaryMapper;
import com.wallo.common.exception.CustomException;
import com.wallo.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnualSalaryService {

    private final AnnualSalaryMapper annualSalaryMapper;

    public AnnualSalaryService(AnnualSalaryMapper annualSalaryMapper) {
        this.annualSalaryMapper = annualSalaryMapper;
    }

    @Transactional
    public AnnualSalaryDto.Response updateAnnualSalary(
            long userId,
            AnnualSalaryDto.UpdateRequest request
    ) {
        if (request == null
                || request.getAnnualSalary() == null
                || request.getAnnualSalary() <= 0) {
            throw new CustomException(ErrorCode.INVALID_ANNUAL_SALARY);
        }

        long annualSalary = request.getAnnualSalary();
        int updatedRows = annualSalaryMapper.updateAnnualSalary(userId, annualSalary);

        if (updatedRows == 0) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        return new AnnualSalaryDto.Response(annualSalary);
    }
}
