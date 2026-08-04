package com.wallo.pointshop.service;

import com.wallo.pointshop.domain.PointShopInventoryItem;
import com.wallo.pointshop.domain.PointShopReward;
import com.wallo.pointshop.dto.response.OpenBoxResponse;
import com.wallo.pointshop.dto.response.PointShopBoxDetailResponse;
import com.wallo.pointshop.dto.response.PointShopResponse;
import com.wallo.pointshop.mapper.PointShopMapper;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 포인트샵 조회 요청을 검증하고 MyBatis 결과를 응답으로 변환함. */
@Service
public class PointShopServiceImpl implements PointShopService {

    private static final long BASIC_BOX_ID = 1L;
    private static final int BASIC_BOX_PRICE = 500;

    private final PointShopMapper pointShopMapper;

    public PointShopServiceImpl(PointShopMapper pointShopMapper) {
        this.pointShopMapper = pointShopMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PointShopResponse getPointShop(Long userId) {
        validateUserId(userId);

        Integer pointBalance = pointShopMapper.findPointBalance(userId);
        if (pointBalance == null) {
            throw new IllegalArgumentException("사용자 정보를 찾을 수 없습니다.");
        }

        List<PointShopInventoryItem> inventoryItems = pointShopMapper.findInventoryItems(userId);
        return PointShopResponse.of(
                pointBalance,
                inventoryItems == null ? Collections.emptyList() : inventoryItems);
    }

    @Override
    @Transactional(readOnly = true)
    public PointShopBoxDetailResponse getBoxDetail(Long boxId) {
        validateBoxId(boxId);
        return PointShopBoxDetailResponse.basicBox();
    }

    /** 포인트 차감, 이력 저장과 보관함 저장을 하나의 트랜잭션으로 처리함. */
    @Override
    @Transactional
    public OpenBoxResponse openBox(Long userId, Long boxId) {
        validateUserId(userId);
        validateBoxId(boxId);

        int updatedRows = pointShopMapper.deductPoints(userId, BASIC_BOX_PRICE);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("보유 포인트가 부족합니다.");
        }

        String requestKey = UUID.randomUUID().toString();
        pointShopMapper.insertPointHistory(
                userId,
                -BASIC_BOX_PRICE,
                "BOX_OPEN",
                "BOX-" + requestKey,
                "기본 절약 상자 개봉");

        int drawNumber = ThreadLocalRandom.current().nextInt(100);
        PointShopReward reward = drawReward(userId, requestKey, drawNumber);
        int rewardPoint = drawRewardPoint(drawNumber);

        if (reward != null) {
            pointShopMapper.insertInventoryReward(reward);
        }
        if (rewardPoint > 0) {
            pointShopMapper.addPoints(userId, rewardPoint);
            pointShopMapper.insertPointHistory(
                    userId,
                    rewardPoint,
                    "BOX_REWARD",
                    "BOX-REWARD-" + requestKey,
                    "랜덤박스 " + rewardPoint + "P 즉시 지급");
        }

        Integer remainingPoint = pointShopMapper.findPointBalance(userId);
        if (rewardPoint > 0) {
            return OpenBoxResponse.point(
                    boxId,
                    BASIC_BOX_PRICE,
                    remainingPoint,
                    rewardPoint);
        }
        if (reward == null) {
            return OpenBoxResponse.lose(
                    boxId,
                    BASIC_BOX_PRICE,
                    remainingPoint);
        }
        return OpenBoxResponse.win(
                boxId,
                BASIC_BOX_PRICE,
                remainingPoint,
                reward);
    }

    @Override
    @Transactional
    public void deleteUsedInventoryItem(Long userId, Long inventoryId) {
        validateUserId(userId);
        if (inventoryId == null || inventoryId <= 0) {
            throw new IllegalArgumentException("보관함 상품 ID가 올바르지 않습니다.");
        }

        int updatedRows = pointShopMapper.softDeleteUsedInventoryItem(userId, inventoryId);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("삭제할 수 있는 사용 완료 상품을 찾지 못했습니다.");
        }
    }

    /** 상품 당첨 구간이면 보관함 상품을 만들고 나머지 구간이면 null을 반환함. */
    private PointShopReward drawReward(
            Long userId,
            String requestKey,
            int drawNumber) {
        if (drawNumber < 5) {
            return reward(userId, "편의점 1,000원 금액권", "NORMAL", requestKey);
        }
        if (drawNumber < 8) {
            return reward(userId, "아메리카노 기프티콘", "NORMAL", requestKey);
        }
        if (drawNumber < 9) {
            return reward(userId, "편의점 5,000원 금액권", "RARE", requestKey);
        }
        return null;
    }

    /** 상품 및 꽝 구간 이후 250P 12%, 500P 8% 즉시 지급 구간을 구분함. */
    private int drawRewardPoint(int drawNumber) {
        if (drawNumber >= 80 && drawNumber < 92) {
            return 250;
        }
        if (drawNumber >= 92) {
            return 500;
        }
        return 0;
    }

    private PointShopReward reward(
            Long userId,
            String itemName,
            String grade,
            String requestKey) {
        return new PointShopReward(
                userId,
                itemName,
                "WALLO-" + requestKey,
                grade);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID가 올바르지 않습니다.");
        }
    }

    private void validateBoxId(Long boxId) {
        if (boxId == null || boxId != BASIC_BOX_ID) {
            throw new IllegalArgumentException("지원하지 않는 랜덤박스입니다.");
        }
    }
}
