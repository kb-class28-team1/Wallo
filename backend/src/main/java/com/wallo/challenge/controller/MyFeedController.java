package com.wallo.challenge.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.challenge.dto.response.MyFeedListResponse;
import com.wallo.challenge.service.MyFeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자의 게시물 목록 조회 API를 제공함. */
@RestController
@RequestMapping("/api/users/me/feeds")
public class MyFeedController {

    private final MyFeedService myFeedService;
    private final CurrentUserProvider currentUserProvider;

    public MyFeedController(
            MyFeedService myFeedService,
            CurrentUserProvider currentUserProvider) {
        this.myFeedService = myFeedService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * 세션의 로그인 사용자 ID를 기준으로 본인이 작성한 게시물만 조회함.
     * 조회 조건을 생략하면 좋아요순, 전체 카테고리, 첫 페이지를 기본값으로 사용함.
     */
    @GetMapping
    public ResponseEntity<MyFeedListResponse> getMyFeeds(
            @RequestParam(defaultValue = "LIKE_DESC") String sort,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        MyFeedListResponse response = myFeedService.getMyFeeds(
                currentUserId,
                sort,
                category,
                page,
                size);

        return ResponseEntity.ok(response);
    }
}
