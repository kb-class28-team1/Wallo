package com.wallo.pointshop.service;

import com.wallo.pointshop.dto.response.PointHistoryResponse;

/** 로그인 사용자의 포인트 내역 조회 규칙을 정의하는 서비스임. */
public interface PointHistoryService {

    PointHistoryResponse getHistory(
            Long userId,
            String type,
            String period,
            String sort,
            String keyword,
            Integer page,
            Integer size);
}
