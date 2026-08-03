package com.wallo.pointshop.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.pointshop.dto.response.PointShopResponse;
import com.wallo.pointshop.service.PointShopService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자의 포인트샵 조회 API를 제공함. */
@RestController
@RequestMapping("/api/point-shop")
public class PointShopController {

    private final PointShopService pointShopService;
    private final CurrentUserProvider currentUserProvider;

    public PointShopController(
            PointShopService pointShopService,
            CurrentUserProvider currentUserProvider) {
        this.pointShopService = pointShopService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 세션의 로그인 사용자 ID로 포인트 잔액과 보관함을 조회함. */
    @GetMapping
    public ResponseEntity<PointShopResponse> getPointShop() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(pointShopService.getPointShop(currentUserId));
    }
}
