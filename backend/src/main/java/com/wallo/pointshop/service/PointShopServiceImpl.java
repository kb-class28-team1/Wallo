package com.wallo.pointshop.service;

import com.wallo.pointshop.domain.PointShopInventoryItem;
import com.wallo.pointshop.domain.PointShopReward;
import com.wallo.pointshop.dto.response.OpenBoxResponse;
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

    /** 포인트 차감, 이력 저장과 보관함 저장을 하나의 트랜잭션으로 처리함. */
    @Override
    @Transactional
    public OpenBoxResponse openBox(Long userId, Long boxId) {
        validateUserId(userId);
        if (boxId == null || boxId != BASIC_BOX_ID) {
            throw new IllegalArgumentException("지원하지 않는 랜덤박스입니다.");
        }

        int updatedRows = pointShopMapper.deductPoints(userId, BASIC_BOX_PRICE);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("보유 포인트가 부족합니다.");
        }

        String requestKey = UUID.randomUUID().toString();
        pointShopMapper.insertPointHistory(
                userId,
                -BASIC_BOX_PRICE,
                "BOX-" + requestKey,
                "기본 절약 상자 개봉");

        PointShopReward reward = drawReward(userId, requestKey);
        pointShopMapper.insertInventoryReward(reward);

        Integer remainingPoint = pointShopMapper.findPointBalance(userId);
        return OpenBoxResponse.win(
                boxId,
                BASIC_BOX_PRICE,
                remainingPoint,
                reward);
    }

    /** 화면에 안내한 60%, 30%, 10% 확률로 당첨 상품을 결정함. */
    private PointShopReward drawReward(Long userId, String requestKey) {
        int drawNumber = ThreadLocalRandom.current().nextInt(100);

        if (drawNumber < 60) {
            return reward(userId, "편의점 1,000원 금액권", "NORMAL", requestKey);
        }
        if (drawNumber < 90) {
            return reward(userId, "아메리카노 기프티콘", "NORMAL", requestKey);
        }
        return reward(userId, "편의점 5,000원 금액권", "RARE", requestKey);
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
}
