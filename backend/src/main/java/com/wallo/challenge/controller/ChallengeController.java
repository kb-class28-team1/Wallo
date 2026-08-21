package com.wallo.challenge.controller;

import com.wallo.auth.CurrentUserProvider;
import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.request.JoinChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;
import com.wallo.challenge.dto.response.CurrentChallengeResponse;
import com.wallo.challenge.dto.response.JoinChallengeResponse;
import com.wallo.challenge.dto.response.WeeklyRankingResponse;
import com.wallo.challenge.dto.response.WeeklyRankingRewardResponse;
import com.wallo.challenge.service.ChallengeService;
import com.wallo.challenge.service.WeeklyRankingRewardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 절약 챌린지 HTTP API 요청을 처리한다. */
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;
    private final WeeklyRankingRewardService weeklyRankingRewardService;
    private final CurrentUserProvider currentUserProvider;

    @Autowired
    public ChallengeController(
            ChallengeService challengeService,
            WeeklyRankingRewardService weeklyRankingRewardService,
            CurrentUserProvider currentUserProvider) {
        this.challengeService = challengeService;
        this.weeklyRankingRewardService = weeklyRankingRewardService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 기존 컨트롤러 단위 테스트와의 호환을 위한 생성자임. */
    public ChallengeController(
            ChallengeService challengeService,
            CurrentUserProvider currentUserProvider) {
        this(challengeService, null, currentUserProvider);
    }

    /**
     * 현재 로그인한 사용자의 새 챌린지를 생성한다.
     * 사용자 ID는 요청 본문이 아닌 인증 정보에서만 가져온다.
     */
    @PostMapping
    public ResponseEntity<CreateChallengeResponse> createChallenge(
            @RequestBody CreateChallengeRequest request) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        CreateChallengeResponse response = challengeService.createChallenge(currentUserId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 현재 로그인한 사용자를 초대 코드에 해당하는 챌린지에 참여시킨다. */
    @PostMapping("/join")
    public ResponseEntity<JoinChallengeResponse> joinChallenge(
            @RequestBody JoinChallengeRequest request) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        JoinChallengeResponse response = challengeService.joinChallenge(currentUserId, request);

        return ResponseEntity.ok(response);
    }

    /** 현재 로그인한 사용자를 요청한 챌린지에서 탈퇴시킨다. */
    @DeleteMapping("/{challengeId}/membership")
    public ResponseEntity<Void> leaveChallenge(@PathVariable Long challengeId) {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        challengeService.leaveChallenge(currentUserId, challengeId);

        return ResponseEntity.noContent().build();
    }

    /** 현재 로그인한 사용자의 챌린지 참여 상태를 조회한다. */
    @GetMapping("/current")
    public ResponseEntity<CurrentChallengeResponse> getCurrentChallenge() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        CurrentChallengeResponse response = challengeService.getCurrentChallenge(currentUserId);

        return ResponseEntity.ok(response);
    }

    /** 현재 로그인한 사용자가 참여 중인 GROUP 챌린지의 이번 주 랭킹을 조회함. */
    @GetMapping("/rankings/weekly")
    public ResponseEntity<WeeklyRankingResponse> getWeeklyRanking() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        WeeklyRankingResponse response = challengeService.getWeeklyRanking(currentUserId);

        return ResponseEntity.ok(response);
    }

    /** 실제 월요일을 기다리지 않고 현재 주 랭킹 보상을 지급하는 개발용 API임. */
    @PostMapping("/rankings/weekly/reward/test")
    public ResponseEntity<WeeklyRankingRewardResponse> grantWeeklyRankingRewardForTest() {
        Long currentUserId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(weeklyRankingRewardService.grantCurrentWeekRewardsForTest(currentUserId));
    }
}
