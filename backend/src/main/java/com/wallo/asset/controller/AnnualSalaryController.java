package com.wallo.asset.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.asset.dto.AnnualSalaryDto;
import com.wallo.asset.service.AnnualSalaryService;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/profile")
public class AnnualSalaryController {

    private final AnnualSalaryService annualSalaryService;
    private final CurrentUserProvider currentUserProvider;

    public AnnualSalaryController(
            AnnualSalaryService annualSalaryService,
            CurrentUserProvider currentUserProvider
    ) {
        this.annualSalaryService = annualSalaryService;
        this.currentUserProvider = currentUserProvider;
    }

    @PatchMapping
    public CommonResponse<AnnualSalaryDto.Response> updateAnnualSalary(
            @RequestBody(required = false) AnnualSalaryDto.UpdateRequest request
    ) {
        return CommonResponse.success(
                annualSalaryService.updateAnnualSalary(
                        currentUserProvider.getCurrentUserId(),
                        request
                )
        );
    }
}
