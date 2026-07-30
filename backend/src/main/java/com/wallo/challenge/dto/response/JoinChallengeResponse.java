package com.wallo.challenge.dto.response;

import com.wallo.challenge.domain.Challenge;

/** POST /api/challenges/join 성공 응답. */
public class JoinChallengeResponse {

    private final Long id;
    private final String name;
    private final String challengeType;
    private final String inviteCode;
    private final String status;

    private JoinChallengeResponse(Challenge challenge) {
        this.id = challenge.getId();
        this.name = challenge.getName();
        this.challengeType = challenge.getChallengeType();
        this.inviteCode = challenge.getInviteCode();
        this.status = challenge.getStatus();
    }

    public static JoinChallengeResponse from(Challenge challenge) {
        return new JoinChallengeResponse(challenge);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getChallengeType() {
        return challengeType;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public String getStatus() {
        return status;
    }
}
