package com.wallo.pointshop.service;

import com.wallo.pointshop.domain.PointShopInventoryItem;
import com.wallo.pointshop.dto.response.PointShopResponse;
import com.wallo.pointshop.mapper.PointShopMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 포인트샵 조회 요청을 검증하고 MyBatis 결과를 응답으로 변환함. */
@Service
public class PointShopServiceImpl implements PointShopService {

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

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID가 올바르지 않습니다.");
        }
    }
}
