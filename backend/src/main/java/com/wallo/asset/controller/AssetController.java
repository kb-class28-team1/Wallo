package com.wallo.asset.controller;

import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.service.AssetService;
import com.wallo.asset.service.ExpenseService;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private static final long TEMPORARY_USER_ID = 1L;

    private final AssetService assetService;
    private final ExpenseService expenseService;

    public AssetController(AssetService assetService, ExpenseService expenseService) {
        this.assetService = assetService;
        this.expenseService = expenseService;
    }

    @GetMapping
    public CommonResponse<AssetDto.Response> getAssets() {
        return CommonResponse.success(assetService.getAssets(TEMPORARY_USER_ID));
    }

    @GetMapping("/expense")
    public CommonResponse<ExpenseDto.Summary> getExpenses(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size
    ) {
        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                startDate,
                endDate,
                page,
                size,
                0
        );

        return CommonResponse.success(expenseService.getExpenseSummary(TEMPORARY_USER_ID, condition));
    }
}
