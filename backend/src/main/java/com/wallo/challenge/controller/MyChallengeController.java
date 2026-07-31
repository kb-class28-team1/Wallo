package com.wallo.challenge.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.challenge.dto.response.MyChallengeDashboardResponse;
import com.wallo.challenge.service.ChallengeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자의 내 챌린지 화면에 필요한 API를 제공함. */
@RestController
@RequestMapping("/api/users/me")
public class MyChallengeController {

    private final ChallengeService challengeService;
    private final CurrentUserProvider currentUserProvider;

    public MyChallengeController(
            ChallengeService challengeService,
            CurrentUserProvider currentUserProvider) {
        this.challengeService = challengeService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 인증 정보의 사용자 ID를 기준으로 내 챌린지 대시보드 정보를 조회함. */
    @GetMapping("/challenge-dashboard")
    public ResponseEntity<MyChallengeDashboardResponse> getMyChallengeDashboard(
            @RequestParam(defaultValue = "6M") String period) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        MyChallengeDashboardResponse response =
                challengeService.getMyChallengeDashboard(currentUserId, period);

        return ResponseEntity.ok(response);
    }
}
