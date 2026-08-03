package com.wallo.pointshop.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.pointshop.dto.response.OpenBoxResponse;
import com.wallo.pointshop.dto.response.PointShopResponse;
import com.wallo.pointshop.service.PointShopService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /** 포인트를 차감하고 기본 랜덤박스의 당첨 상품을 보관함에 추가함. */
    @PostMapping("/boxes/{boxId}/open")
    public ResponseEntity<OpenBoxResponse> openBox(@PathVariable Long boxId) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(pointShopService.openBox(currentUserId, boxId));
    }
}
