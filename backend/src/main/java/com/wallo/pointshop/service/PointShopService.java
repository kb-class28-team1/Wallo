package com.wallo.pointshop.service;

import com.wallo.pointshop.dto.response.OpenBoxResponse;
import com.wallo.pointshop.dto.response.OpenBoxesResponse;
import com.wallo.pointshop.dto.response.PointShopBoxDetailResponse;
import com.wallo.pointshop.dto.response.PointShopResponse;

/** 포인트샵 조회 기능을 정의함. */
public interface PointShopService {

    /** 로그인 사용자의 포인트 잔액과 보관함을 조회함. */
    PointShopResponse getPointShop(Long userId);

    /** 기본 랜덤박스의 상품 구성과 당첨 확률을 조회함. */
    PointShopBoxDetailResponse getBoxDetail(Long boxId);

    /** 기본 랜덤박스 가격을 차감하고 추첨 결과를 반환함. */
    OpenBoxResponse openBox(Long userId, Long boxId);

    /** 기본 랜덤박스 10개를 한 번에 차감하고 모든 추첨 결과를 반환함. */
    OpenBoxesResponse openBoxes(Long userId, Long boxId);

    /** 로그인 사용자의 사용 완료 상품을 보관함에서 실제로 삭제함. */
    void deleteUsedInventoryItem(Long userId, Long inventoryId);
}
