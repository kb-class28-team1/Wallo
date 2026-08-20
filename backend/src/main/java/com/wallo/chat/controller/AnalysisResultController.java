package com.wallo.chat.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.chat.dto.AssetAnalysisResultDto;
import com.wallo.chat.dto.ConsumptionAnalysisResultDto;
import com.wallo.chat.service.AssetAnalysisResultService;
import com.wallo.chat.service.ConsumptionAnalysisResultService;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis-results")
public class AnalysisResultController {

    private final AssetAnalysisResultService assetAnalysisResultService;
    private final ConsumptionAnalysisResultService consumptionAnalysisResultService;
    private final CurrentUserProvider currentUserProvider;

    public AnalysisResultController(
            AssetAnalysisResultService assetAnalysisResultService,
            ConsumptionAnalysisResultService consumptionAnalysisResultService,
            CurrentUserProvider currentUserProvider
    ) {
        this.assetAnalysisResultService = assetAnalysisResultService;
        this.consumptionAnalysisResultService = consumptionAnalysisResultService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/latest")
    public CommonResponse<LatestAnalysisResponse> getLatest() {
        long userId = currentUserProvider.getCurrentUserId();
        return CommonResponse.success(new LatestAnalysisResponse(
                assetAnalysisResultService.findLatestByUserId(userId),
                consumptionAnalysisResultService.findLatestByUserId(userId)
        ));
    }

    public record LatestAnalysisResponse(
            AssetAnalysisResultDto.LatestResponse asset,
            ConsumptionAnalysisResultDto.LatestResponse consumption
    ) {
    }
}
