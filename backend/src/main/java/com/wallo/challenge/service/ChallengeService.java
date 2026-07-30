package com.wallo.challenge.service;

import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.request.JoinChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;
import com.wallo.challenge.dto.response.CurrentChallengeResponse;
import com.wallo.challenge.dto.response.JoinChallengeResponse;
import com.wallo.challenge.dto.response.WeeklyRankingResponse;

/** 챌린지 생성과 참여 상태 변경에 대한 비즈니스 규칙을 처리한다. */
public interface ChallengeService {

    /**
     * 현재 미참여 상태인 사용자의 챌린지를 생성하고, 생성자를 해당 챌린지에 연결한다.
     *
     * @param ownerId 인증 정보에서 얻은 현재 사용자 ID
     */
    CreateChallengeResponse createChallenge(Long ownerId, CreateChallengeRequest request);

    /** 초대 코드로 기존 챌린지에 참여하고 사용자의 현재 챌린지를 연결한다. */
    JoinChallengeResponse joinChallenge(Long userId, JoinChallengeRequest request);

    /** 로그인 사용자의 현재 챌린지 참여 상태와 챌린지 정보를 조회한다. */
    CurrentChallengeResponse getCurrentChallenge(Long userId);

    /** 로그인 사용자가 참여 중인 GROUP 챌린지의 이번 주 랭킹을 조회함. */
    WeeklyRankingResponse getWeeklyRanking(Long userId);
}
