package com.wallo.asset.controller;

import com.wallo.asset.dto.BudgetDto;
import com.wallo.asset.service.BudgetService;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private static final long TEMPORARY_USER_ID = 1L;

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public CommonResponse<BudgetDto.Summary> getBudget(
            @RequestParam(value = "targetMonth", required = false) String targetMonth
    ) {
        return CommonResponse.success(budgetService.getBudgetSummary(TEMPORARY_USER_ID, targetMonth));
    }

    @PutMapping
    public CommonResponse<BudgetDto.Summary> upsertBudget(
            @RequestBody BudgetDto.UpsertRequest request
    ) {
        return CommonResponse.success(budgetService.upsertBudget(TEMPORARY_USER_ID, request));
    }
}
