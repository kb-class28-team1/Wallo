package com.wallo.pointshop.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.pointshop.service.PointShopService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자의 포인트샵 보관함 상품 API를 제공함. */
@RestController
@RequestMapping("/api/users/me/inventory")
public class InventoryController {

    private final PointShopService pointShopService;
    private final CurrentUserProvider currentUserProvider;

    public InventoryController(
            PointShopService pointShopService,
            CurrentUserProvider currentUserProvider) {
        this.pointShopService = pointShopService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 로그인 사용자가 사용 완료한 상품을 보관함에서 실제로 삭제함. */
    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<Void> deleteUsedInventoryItem(@PathVariable Long inventoryId) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        pointShopService.deleteUsedInventoryItem(currentUserId, inventoryId);
        return ResponseEntity.noContent().build();
    }

    /** 로그인 사용자가 보관함의 사용 가능한 상품을 사용 완료 처리함. */
    @PatchMapping("/{inventoryId}/use")
    public ResponseEntity<Void> useInventoryItem(@PathVariable Long inventoryId) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        pointShopService.useInventoryItem(currentUserId, inventoryId);
        return ResponseEntity.noContent().build();
    }
}
