package com.wallo.pointshop.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.pointshop.dto.response.PointHistoryResponse;
import com.wallo.pointshop.service.PointHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자의 포인트 사용·적립 내역을 제공하는 API임. */
@RestController
@RequestMapping("/api/point-history")
public class PointHistoryController {

    private final PointHistoryService pointHistoryService;
    private final CurrentUserProvider currentUserProvider;

    public PointHistoryController(
            PointHistoryService pointHistoryService,
            CurrentUserProvider currentUserProvider) {
        this.pointHistoryService = pointHistoryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<PointHistoryResponse> getHistory(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "ALL") String period,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(pointHistoryService.getHistory(
                currentUserId,
                type,
                period,
                sort,
                keyword,
                page,
                size));
    }
}
