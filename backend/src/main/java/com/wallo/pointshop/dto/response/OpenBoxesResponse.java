package com.wallo.pointshop.dto.response;

import com.wallo.pointshop.domain.PointShopReward;
import java.util.List;

/** POST /api/point-shop/boxes/{boxId}/open-bulk 성공 응답임. */
public class OpenBoxesResponse {

    private final Long boxId;
    private final Integer openedCount;
    private final Integer usedPoint;
    private final Integer remainingPoint;
    private final Integer itemRewardCount;
    private final Integer pointRewardCount;
    private final Integer loseCount;
    private final Integer rewardPoint;
    private final List<Reward> rewards;
    private final List<DrawResult> drawResults;

    private OpenBoxesResponse(
            Long boxId,
            Integer openedCount,
            Integer usedPoint,
            Integer remainingPoint,
            Integer itemRewardCount,
            Integer pointRewardCount,
            Integer loseCount,
            Integer rewardPoint,
            List<PointShopReward> rewards,
            List<DrawResult> drawResults) {
        this.boxId = boxId;
        this.openedCount = openedCount;
        this.usedPoint = usedPoint;
        this.remainingPoint = remainingPoint;
        this.itemRewardCount = itemRewardCount;
        this.pointRewardCount = pointRewardCount;
        this.loseCount = loseCount;
        this.rewardPoint = rewardPoint;
        this.rewards = rewards.stream().map(Reward::from).toList();
        this.drawResults = drawResults == null ? List.of() : List.copyOf(drawResults);
    }

    public static OpenBoxesResponse of(
            Long boxId,
            Integer openedCount,
            Integer usedPoint,
            Integer remainingPoint,
            Integer itemRewardCount,
            Integer pointRewardCount,
            Integer loseCount,
            Integer rewardPoint,
            List<PointShopReward> rewards,
            List<DrawResult> drawResults) {
        return new OpenBoxesResponse(
                boxId,
                openedCount,
                usedPoint,
                remainingPoint,
                itemRewardCount,
                pointRewardCount,
                loseCount,
                rewardPoint,
                rewards,
                drawResults);
    }

    public Long getBoxId() {
        return boxId;
    }

    public Integer getOpenedCount() {
        return openedCount;
    }

    public Integer getUsedPoint() {
        return usedPoint;
    }

    public Integer getRemainingPoint() {
        return remainingPoint;
    }

    public Integer getItemRewardCount() {
        return itemRewardCount;
    }

    public Integer getPointRewardCount() {
        return pointRewardCount;
    }

    public Integer getLoseCount() {
        return loseCount;
    }

    public Integer getRewardPoint() {
        return rewardPoint;
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public List<DrawResult> getDrawResults() {
        return drawResults;
    }

    public static class DrawResult {

        private final String result;
        private final Long inventoryId;
        private final String itemName;
        private final String grade;
        private final Integer rewardPoint;

        private DrawResult(
                String result,
                Long inventoryId,
                String itemName,
                String grade,
                Integer rewardPoint) {
            this.result = result;
            this.inventoryId = inventoryId;
            this.itemName = itemName;
            this.grade = grade;
            this.rewardPoint = rewardPoint;
        }

        public static DrawResult item(PointShopReward reward) {
            return new DrawResult(
                    "WIN",
                    reward.getInventoryId(),
                    reward.getItemName(),
                    reward.getGrade(),
                    0);
        }

        public static DrawResult point(Integer rewardPoint) {
            return new DrawResult("POINT", null, null, null, rewardPoint);
        }

        public static DrawResult lose() {
            return new DrawResult("LOSE", null, null, null, 0);
        }

        public String getResult() {
            return result;
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

        public Integer getRewardPoint() {
            return rewardPoint;
        }
    }

    /** 일괄 개봉으로 보관함에 추가된 상품 정보임. */
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
