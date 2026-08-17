package com.wallo.asset.controller;

import com.wallo.asset.service.FallbackClassificationService;
import com.wallo.auth.CurrentUserProvider;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets/expense")
public class FallbackClassificationController {

    private final FallbackClassificationService fallbackClassificationService;
    private final CurrentUserProvider currentUserProvider;

    public FallbackClassificationController(
            FallbackClassificationService fallbackClassificationService,
            CurrentUserProvider currentUserProvider
    ) {
        this.fallbackClassificationService = fallbackClassificationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/fallback/reclassify")
    public CommonResponse<Integer> reclassifyFallbackTransactions() {
        return CommonResponse.success(
                fallbackClassificationService.reclassify(currentUserProvider.getCurrentUserId())
        );
    }
}
