package com.wallo.pointshop.dto.response;

import java.util.List;

/** GET /api/point-shop/boxes/{boxId} 성공 응답임. */
public class PointShopBoxDetailResponse {

    private final Long boxId;
    private final String boxName;
    private final Integer price;
    private final List<Probability> probabilities;

    private PointShopBoxDetailResponse(
            Long boxId,
            String boxName,
            Integer price,
            List<Probability> probabilities) {
        this.boxId = boxId;
        this.boxName = boxName;
        this.price = price;
        this.probabilities = probabilities;
    }

    public static PointShopBoxDetailResponse basicBox() {
        return new PointShopBoxDetailResponse(
                1L,
                "기본 절약 상자",
                500,
                List.of(
                        new Probability("편의점 1,000원 금액권", 5),
                        new Probability("아메리카노 기프티콘", 3),
                        new Probability("편의점 5,000원 금액권", 1),
                        new Probability("꽝", 71),
                        new Probability("250P 즉시 지급", 12),
                        new Probability("500P 즉시 지급", 8)));
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

    public List<Probability> getProbabilities() {
        return probabilities;
    }

    public static class Probability {

        private final String label;
        private final Integer rate;

        public Probability(String label, Integer rate) {
            this.label = label;
            this.rate = rate;
        }

        public String getLabel() {
            return label;
        }

        public Integer getRate() {
            return rate;
        }
    }
}
