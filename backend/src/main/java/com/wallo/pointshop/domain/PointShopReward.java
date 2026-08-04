package com.wallo.pointshop.domain;

/** 랜덤박스 당첨 시 USER_INVENTORY에 저장할 상품 정보임. */
public class PointShopReward {

    private Long inventoryId;
    private Long userId;
    private String itemName;
    private String couponCode;
    private String grade;

    public PointShopReward(
            Long userId,
            String itemName,
            String couponCode,
            String grade) {
        this.userId = userId;
        this.itemName = itemName;
        this.couponCode = couponCode;
        this.grade = grade;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getItemName() {
        return itemName;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public String getGrade() {
        return grade;
    }
}
