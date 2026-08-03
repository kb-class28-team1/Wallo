package com.wallo.pointshop.dto.response;

import com.wallo.pointshop.domain.PointShopReward;

/** POST /api/point-shop/boxes/{boxId}/open 성공 응답임. */
public class OpenBoxResponse {

    private final Long boxId;
    private final Integer usedPoint;
    private final Integer remainingPoint;
    private final String result;
    private final Reward reward;
    private final Integer rewardPoint;

    private OpenBoxResponse(
            Long boxId,
            Integer usedPoint,
            Integer remainingPoint,
            String result,
            PointShopReward reward,
            Integer rewardPoint) {
        this.boxId = boxId;
        this.usedPoint = usedPoint;
        this.remainingPoint = remainingPoint;
        this.result = result;
        this.reward = reward == null ? null : Reward.from(reward);
        this.rewardPoint = rewardPoint;
    }

    public static OpenBoxResponse win(
            Long boxId,
            Integer usedPoint,
            Integer remainingPoint,
            PointShopReward reward) {
        return new OpenBoxResponse(boxId, usedPoint, remainingPoint, "WIN", reward, null);
    }

    public static OpenBoxResponse lose(
            Long boxId,
            Integer usedPoint,
            Integer remainingPoint) {
        return new OpenBoxResponse(boxId, usedPoint, remainingPoint, "LOSE", null, null);
    }

    public static OpenBoxResponse point(
            Long boxId,
            Integer usedPoint,
            Integer remainingPoint,
            Integer rewardPoint) {
        return new OpenBoxResponse(
                boxId,
                usedPoint,
                remainingPoint,
                "POINT",
                null,
                rewardPoint);
    }

    public Long getBoxId() {
        return boxId;
    }

    public Integer getUsedPoint() {
        return usedPoint;
    }

    public Integer getRemainingPoint() {
        return remainingPoint;
    }

    public String getResult() {
        return result;
    }

    public Reward getReward() {
        return reward;
    }

    public Integer getRewardPoint() {
        return rewardPoint;
    }

    /** 당첨되어 보관함에 추가된 상품 정보임. */
    public static class Reward {

        private final Long inventoryId;
        private final String itemName;
        private final String grade;

        private Reward(PointShopReward reward) {
            this.inventoryId = reward.getInventoryId();
            this.itemName = reward.getItemName();
            this.grade = reward.getGrade();
        }

        public static Reward from(PointShopReward reward) {
            return new Reward(reward);
        }

        public Long getInventoryId() {
            return inventoryId;
        }

        public String getItemName() {
            return itemName;
        }

        public String getGrade() {
            return grade;
        }
    }
}
