package com.wallo.challenge.service;

import com.wallo.challenge.dto.response.MyFeedListResponse;

/** 로그인 사용자의 게시물 목록 조회 규칙을 정의하는 서비스임. */
public interface MyFeedService {

    /** 정렬, 카테고리, 페이지 조건에 맞는 내 게시물 목록을 반환함. */
    MyFeedListResponse getMyFeeds(
            Long userId,
            String sort,
            String category,
            Integer page,
            Integer size);
}
