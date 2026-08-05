package com.wallo.asset.controller;

import com.wallo.asset.dto.ReportDto;
import com.wallo.asset.service.AssetReportService;
import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class AssetReportController {

    private final AssetReportService assetReportService;
    private final CurrentUserProvider currentUserProvider;

    public AssetReportController(
            AssetReportService assetReportService,
            CurrentUserProvider currentUserProvider
    ) {
        this.assetReportService = assetReportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/insights")
    public CommonResponse<ReportDto.Insight> getConsumptionInsight() {
        return CommonResponse.success(
                assetReportService.getConsumptionInsight(currentUserProvider.getCurrentUserId())
        );
    }

    @GetMapping("/tax-settlement")
    public CommonResponse<ReportDto.TaxSettlement> getTaxSettlement(
            @RequestParam(value = "year", required = false) Integer year
    ) {
        return CommonResponse.success(
                assetReportService.getTaxSettlement(
                        currentUserProvider.getCurrentUserId(),
                        year
                )
        );
    }
}
