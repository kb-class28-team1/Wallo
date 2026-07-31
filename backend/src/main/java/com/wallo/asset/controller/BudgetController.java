package com.wallo.asset.controller;

import com.wallo.auth.CurrentUserProvider;
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

    private final BudgetService budgetService;
    private final CurrentUserProvider currentUserProvider;

    public BudgetController(BudgetService budgetService, CurrentUserProvider currentUserProvider) {
        this.budgetService = budgetService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public CommonResponse<BudgetDto.Summary> getBudget(
            @RequestParam(value = "targetMonth", required = false) String targetMonth
    ) {
        return CommonResponse.success(
                budgetService.getBudgetSummary(currentUserProvider.getCurrentUserId(), targetMonth));
    }

    @PutMapping
    public CommonResponse<BudgetDto.Summary> upsertBudget(
            @RequestBody BudgetDto.UpsertRequest request
    ) {
        return CommonResponse.success(
                budgetService.upsertBudget(currentUserProvider.getCurrentUserId(), request));
    }
}
