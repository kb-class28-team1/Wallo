package com.wallo.asset.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.asset.dto.AssetDto;
import com.wallo.asset.dto.AssetSyncDto;
import com.wallo.asset.dto.ExpenseDto;
import com.wallo.asset.service.AssetService;
import com.wallo.asset.service.AssetSyncOrchestrator;
import com.wallo.asset.service.ExpenseService;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;
    private final ExpenseService expenseService;
    private final AssetSyncOrchestrator assetSyncOrchestrator;
    private final CurrentUserProvider currentUserProvider;

    public AssetController(
            AssetService assetService,
            ExpenseService expenseService,
            AssetSyncOrchestrator assetSyncOrchestrator,
            CurrentUserProvider currentUserProvider) {
        this.assetService = assetService;
        this.expenseService = expenseService;
        this.assetSyncOrchestrator = assetSyncOrchestrator;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public CommonResponse<AssetDto.Response> getAssets() {
        return CommonResponse.success(assetService.getAssets(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/expense")
    public CommonResponse<ExpenseDto.Summary> getExpenses(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam(value = "category", required = false) String category
    ) {
        ExpenseDto.SearchCondition condition = new ExpenseDto.SearchCondition(
                startDate,
                endDate,
                page,
                size,
                category,
                0
        );

        return CommonResponse.success(expenseService.getExpenseSummary(
                currentUserProvider.getCurrentUserId(), condition));
    }

    @PatchMapping("/expense/{transactionId}/category")
    public CommonResponse<Void> updateExpenseCategory(
            @PathVariable long transactionId,
            @RequestBody(required = false) ExpenseDto.CategoryUpdateRequest request
    ) {
        expenseService.updateTransactionCategory(
                currentUserProvider.getCurrentUserId(),
                transactionId,
                request
        );
        return CommonResponse.success(null);
    }

    @PostMapping("/sync")
    public CommonResponse<AssetSyncDto.SyncResponse> syncAssets() {
        return CommonResponse.success(
                assetSyncOrchestrator.syncNow(currentUserProvider.getCurrentUserId())
        );
    }
}
