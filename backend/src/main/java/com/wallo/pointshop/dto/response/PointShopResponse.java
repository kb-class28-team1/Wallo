package com.wallo.pointshop.dto.response;

import com.wallo.pointshop.domain.PointShopInventoryItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** GET /api/point-shop 성공 응답임. */
public class PointShopResponse {

    private final Integer pointBalance;
    private final List<Box> boxes;
    private final List<InventoryItem> inventory;

    private PointShopResponse(
            Integer pointBalance,
            List<PointShopInventoryItem> inventoryItems) {
        this.pointBalance = pointBalance;
        this.boxes = List.of(
                new Box(1L, "기본 절약 상자", 500, "NORMAL", "/images/boxes/basic.png"));
        this.inventory = inventoryItems.stream()
                .map(InventoryItem::from)
                .collect(Collectors.toList());
    }

    public static PointShopResponse of(
            Integer pointBalance,
            List<PointShopInventoryItem> inventoryItems) {
        return new PointShopResponse(pointBalance, inventoryItems);
    }

    public Integer getPointBalance() {
        return pointBalance;
    }

    public List<Box> getBoxes() {
        return boxes;
    }

    public List<InventoryItem> getInventory() {
        return inventory;
    }

    /** 현재 화면에서 사용할 기본 랜덤박스 정보임. */
    public static class Box {

        private final Long boxId;
        private final String boxName;
        private final Integer price;
        private final String grade;
        private final String imageUrl;

        public Box(
                Long boxId,
                String boxName,
                Integer price,
                String grade,
                String imageUrl) {
            this.boxId = boxId;
            this.boxName = boxName;
            this.price = price;
            this.grade = grade;
            this.imageUrl = imageUrl;
        }

        public Long getBoxId() {
            return boxId;
        }

        public String getBoxName() {
            return boxName;
        }

        public Integer getPrice() {
            return price;
        }

        public String getGrade() {
            return grade;
        }

        public String getImageUrl() {
            return imageUrl;
        }
    }

    /** DB 컬럼명을 프론트 응답 항목명으로 변환한 보관함 상품임. */
    public static class InventoryItem {

        private final Long inventoryId;
        private final String itemName;
        private final String status;
        private final LocalDateTime acquiredAt;

        private InventoryItem(PointShopInventoryItem item) {
            this.inventoryId = item.getInventoryId();
            this.itemName = item.getItemName();
            this.status = item.getStatus();
            this.acquiredAt = item.getAcquiredAt();
        }

        public static InventoryItem from(PointShopInventoryItem item) {
            return new InventoryItem(item);
        }

        public Long getInventoryId() {
            return inventoryId;
        }

        public String getItemName() {
            return itemName;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getAcquiredAt() {
            return acquiredAt;
        }
    }
}
