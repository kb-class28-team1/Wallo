package com.wallo.challenge.service;

import com.wallo.challenge.dto.request.CreateChallengeRequest;
import com.wallo.challenge.dto.response.CreateChallengeResponse;

/** 챌린지 생성과 참여 상태 변경에 대한 비즈니스 규칙을 처리한다. */
public interface ChallengeService {

    /**
     * 현재 미참여 상태인 사용자의 챌린지를 생성하고, 생성자를 해당 챌린지에 연결한다.
     *
     * @param ownerId 인증 정보에서 얻은 현재 사용자 ID
     */
    CreateChallengeResponse createChallenge(Long ownerId, CreateChallengeRequest request);
}
