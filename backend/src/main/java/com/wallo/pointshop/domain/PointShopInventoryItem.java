package com.wallo.pointshop.domain;

import java.time.LocalDateTime;

/** 포인트샵 보관함에서 조회한 상품 한 건임. */
public class PointShopInventoryItem {

    private Long inventoryId;
    private String itemName;
    private String status;
    private LocalDateTime acquiredAt;

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAcquiredAt() {
        return acquiredAt;
    }

    public void setAcquiredAt(LocalDateTime acquiredAt) {
        this.acquiredAt = acquiredAt;
    }
}
