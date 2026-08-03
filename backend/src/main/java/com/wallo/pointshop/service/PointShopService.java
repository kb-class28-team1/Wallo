package com.wallo.pointshop.service;

import com.wallo.pointshop.dto.response.PointShopResponse;

/** 포인트샵 조회 기능을 정의함. */
public interface PointShopService {

    /** 로그인 사용자의 포인트 잔액과 보관함을 조회함. */
    PointShopResponse getPointShop(Long userId);
}
