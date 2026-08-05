package com.wallo.asset.controller;

import com.wallo.asset.dto.ReportDto;
import com.wallo.asset.service.ReportService;
import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("assetReportController")
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    public ReportController(
            ReportService reportService,
            CurrentUserProvider currentUserProvider
    ) {
        this.reportService = reportService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/insights")
    public CommonResponse<ReportDto.Insight> getConsumptionInsight() {
        return CommonResponse.success(
                reportService.getConsumptionInsight(currentUserProvider.getCurrentUserId())
        );
    }

    @GetMapping("/tax-settlement")
    public CommonResponse<ReportDto.TaxSettlement> getTaxSettlement(
            @RequestParam(value = "year", required = false) Integer year
    ) {
        return CommonResponse.success(
                reportService.getTaxSettlement(
                        currentUserProvider.getCurrentUserId(),
                        year
                )
        );
    }
}
