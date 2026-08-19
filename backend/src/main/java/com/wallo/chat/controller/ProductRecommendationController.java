package com.wallo.chat.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.chat.dto.ProductRecommendationResultDto;
import com.wallo.chat.service.ProductRecommendationResultService;
import com.wallo.common.response.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-recommendations")
public class ProductRecommendationController {

    private final ProductRecommendationResultService productRecommendationResultService;
    private final CurrentUserProvider currentUserProvider;

    public ProductRecommendationController(
            ProductRecommendationResultService productRecommendationResultService,
            CurrentUserProvider currentUserProvider
    ) {
        this.productRecommendationResultService = productRecommendationResultService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/latest")
    public CommonResponse<ProductRecommendationResultDto.LatestResponse> getLatest() {
        return CommonResponse.success(
                productRecommendationResultService.findLatestByUserId(
                        currentUserProvider.getCurrentUserId()
                )
        );
    }
}
