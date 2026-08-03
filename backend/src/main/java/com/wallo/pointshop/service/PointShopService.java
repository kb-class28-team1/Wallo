package com.wallo.pointshop.service;

import com.wallo.pointshop.dto.response.OpenBoxResponse;
import com.wallo.pointshop.dto.response.PointShopResponse;

/** 포인트샵 조회 기능을 정의함. */
public interface PointShopService {

    /** 로그인 사용자의 포인트 잔액과 보관함을 조회함. */
    PointShopResponse getPointShop(Long userId);

    /** 기본 랜덤박스 가격을 차감하고 추첨 결과를 반환함. */
    OpenBoxResponse openBox(Long userId, Long boxId);
}
